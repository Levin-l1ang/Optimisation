package linePlanning.PTN.benders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import linePlanning.PTN.Connection;
import linePlanning.PTN.PTNRoute;
import linePlanning.lineGeneration.Line;
import util.Configuration;

public class DisaggregationLP
{
	private final Map<Line, Double> lineValueMap;
	private final Map<PTNRoute, Double> directRouteValueMap;

	private final Map<PTNRoute, Map<Line, IloNumVar>> assignmentVariables;
	private final Map<PTNRoute, IloRange> assignmentConstraints;
	private final Map<Line, Map<Connection, IloRange>> capacityConstraints;

	private IloCplex cplex;

	private int iteration = 0;

	public DisaggregationLP(Map<Line, Double> lineValueMap, Map<PTNRoute, Double> directRouteValueMap)
			throws IloException
	{
		this.lineValueMap = lineValueMap;
		this.directRouteValueMap = directRouteValueMap;

		this.assignmentVariables = new LinkedHashMap<>();
		this.assignmentConstraints = new LinkedHashMap<>();
		this.capacityConstraints = new LinkedHashMap<>();

		cplex = new IloCplex();
		cplex.setOut(null);

		// Set number of threads available to the solver.
		cplex.setParam(IloCplex.Param.Threads, Configuration.getConfiguration().getIntProperty("NUM_THREADS_SOLVER"));

		addVariables();
		addMaximumAssignmentConstraints();
		addCapacityConstraints();
		addObjective();
	}

	public void solve() throws IloException
	{
		try
		{
			cplex.solve();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public double getObjectiveValue() throws IloException
	{
		return cplex.getObjValue();
	}

	private void addVariables() throws IloException
	{
		for (PTNRoute route : directRouteValueMap.keySet())
		{
			assignmentVariables.put(route, new LinkedHashMap<>());
			for (Line line : route.getSupportingLines())
			{
				assignmentVariables.get(route).put(line, cplex.numVar(0, Double.MAX_VALUE));
			}
		}
	}

	private void addMaximumAssignmentConstraints() throws IloException
	{
		for (PTNRoute route : directRouteValueMap.keySet())
		{
			IloLinearNumExpr lhs = cplex.linearNumExpr();
			for (IloNumVar var : assignmentVariables.get(route).values())
			{
				lhs.addTerm(1.0, var);
			}
			IloRange range = cplex.addLe(lhs, directRouteValueMap.get(route));
			assignmentConstraints.put(route, range);
		}
	}

	private void addCapacityConstraints() throws IloException
	{
		// Build reverse index: line -> (route -> variable) for routes that support it.
		Map<Line, Map<PTNRoute, IloNumVar>> lineToRouteVars = new LinkedHashMap<>();
		for (Entry<PTNRoute, Map<Line, IloNumVar>> routeEntry : assignmentVariables.entrySet())
		{
			PTNRoute route = routeEntry.getKey();
			for (Entry<Line, IloNumVar> lineEntry : routeEntry.getValue().entrySet())
			{
				Line line = lineEntry.getKey();
				if (!lineToRouteVars.containsKey(line))
				{
					lineToRouteVars.put(line, new LinkedHashMap<>());
				}
				lineToRouteVars.get(line).put(route, lineEntry.getValue());
			}
		}

		for (Entry<Line, Double> lineEntry : lineValueMap.entrySet())
		{
			Line line = lineEntry.getKey();
			capacityConstraints.put(line, new LinkedHashMap<>());
			Map<PTNRoute, IloNumVar> routeVars = lineToRouteVars.get(line);

			// Precompute connection -> lhs: one pass over route connections avoids
			// repeated contains() checks in the constraint loop.
			Map<Connection, IloLinearNumExpr> connectionLhsMap = new LinkedHashMap<>();
			if (routeVars != null)
			{
				for (Entry<PTNRoute, IloNumVar> routeVar : routeVars.entrySet())
				{
					for (Connection connection : routeVar.getKey().getConnections())
					{
						if (!connectionLhsMap.containsKey(connection))
						{
							connectionLhsMap.put(connection, cplex.linearNumExpr());
						}
						connectionLhsMap.get(connection).addTerm(1.0, routeVar.getValue());
					}
				}
			}

			for (Connection connection : line.getConnectionPathForward())
			{
				IloLinearNumExpr lhs = connectionLhsMap.containsKey(connection) ? connectionLhsMap.get(connection)
						: cplex.linearNumExpr();
				IloRange range = cplex.addLe(lhs, line.getCapacity() * lineEntry.getValue());
				capacityConstraints.get(line).put(connection, range);
			}
			for (Connection connection : line.getConnectionPathBackward())
			{
				IloLinearNumExpr lhs = connectionLhsMap.containsKey(connection) ? connectionLhsMap.get(connection)
						: cplex.linearNumExpr();
				IloRange range = cplex.addLe(lhs, line.getCapacity() * lineEntry.getValue());
				capacityConstraints.get(line).put(connection, range);
			}
		}
	}

	private void addObjective() throws IloException
	{
		IloLinearNumExpr obj = cplex.linearNumExpr();
		for (Entry<PTNRoute, Map<Line, IloNumVar>> entry : assignmentVariables.entrySet())
		{
			for (IloNumVar var : entry.getValue().values())
			{
				obj.addTerm(1.0, var);
			}
		}
		cplex.addMaximize(obj);
	}

	public void updateValues(Map<Line, Double> newLineValues, Map<PTNRoute, Double> newRouteValues) throws IloException
	{
		iteration++;
		for (PTNRoute route : assignmentConstraints.keySet())
		{
			assignmentConstraints.get(route).setUB(newRouteValues.get(route));
		}
		for (Entry<Line, Map<Connection, IloRange>> lineEntry : capacityConstraints.entrySet())
		{
			Line line = lineEntry.getKey();
			double capacity = line.getCapacity() * newLineValues.get(line);
			for (IloRange range : lineEntry.getValue().values())
			{
				range.setUB(capacity);
			}
		}
	}

	public void clean() throws IloException
	{
		cplex.clearModel();
		cplex.end();
	}

	public double getAssignmentDual(PTNRoute route) throws IloException
	{
		return cplex.getDual(assignmentConstraints.get(route));
	}

	public double getLineDual(Line line) throws IloException
	{
		double sum = 0;
		for (Connection connection : line.getConnectionPathForward())
		{
			sum += cplex.getDual(capacityConstraints.get(line).get(connection));
		}
		for (Connection connection : line.getConnectionPathBackward())
		{
			sum += cplex.getDual(capacityConstraints.get(line).get(connection));
		}
		return sum;
	}
}
