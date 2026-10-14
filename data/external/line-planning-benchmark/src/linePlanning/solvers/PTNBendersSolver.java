package linePlanning.solvers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import graph.structures.digraph.DirectedGraphArc;
import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import linePlanning.DPTN.DPTNRoute;
import linePlanning.PTN.Connection;
import linePlanning.PTN.LightBasicDirectRouteConstraint;
import linePlanning.PTN.PTNRoute;
import linePlanning.PTN.benders.KarbsteinCallback;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
import linePlanning.columnGeneration.constraints.CapacityConstraint;
import linePlanning.columnGeneration.master.LinePlanningMasterProblem;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.columnGeneration.pricing.LinePlanningPricingRoutine.NetworkType;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.LinePlanningSolution;
import linePlanning.instance.ODPair;
import linePlanning.instance.StationNode;
import linePlanning.lineGeneration.Line;
import linePlanning.routeGeneration.RouteGenerator;
import optimisation.columnGeneration.AbstractSolution;
import util.Pair;

public class PTNBendersSolver extends Solver
{
	public PTNBendersSolver()
	{
		super(NetworkType.PTN);
	}

	@Override
	public String getName()
	{
		return "PTN-BendersSolver";
	}

	@Override
	public LinePlanningSolution solve(LinePlanningInstance instance, List<Line> linePool, int cpuTimeSeconds)
			throws IloException
	{
		System.out.println("Lower Bound on Travel Time: " + instance.getLowerBound() + " or "
				+ instance.getAverageTTBound() + " per pax");
		long solveStartTime = System.currentTimeMillis();

		// Prepare lines and routes.
		instance.setLinePool(linePool);

		RouteGenerator rpGen = new RouteGenerator(instance, instance.getRouteGenerationFile());

		Set<PTNRoute> connectionRoutes = new HashSet<>();
		Set<PTNRoute> directConnectionRoutes = new HashSet<>();
		Set<PTNRoute> indirectConnectionRoutes = new HashSet<>();

		for (ODPair od : instance.getODPairs())
		{
			if ((System.currentTimeMillis() - solveStartTime) / 1000 >= cpuTimeSeconds)
			{
				System.out.println("Time limit exceeded during route generation, terminating.");
				return null;
			}
			List<DPTNRoute> routes = rpGen.getRoutes(od, true);
			for (PTNRoute r : rpGen.toPTNRoutes(routes, od, false))
			{
				if (r.isDirect())
				{
					directConnectionRoutes.add(r);
				}
				else
				{
					indirectConnectionRoutes.add(r);
				}
			}
		}
		Map<Connection, Set<List<Line>>> lineSetsPerConnection = new HashMap<>();

		for (DirectedGraphArc<StationNode, Connection> connectionArc : instance.getConnectionNetwork().getArcs())
		{
			Connection connection = connectionArc.getData();
			lineSetsPerConnection.put(connection, new HashSet<>());
		}
		// Add all direct routes that can be supported by a line.
		for (PTNRoute r : directConnectionRoutes)
		{
			r.computeSupportingLines(instance.getLinePool());
			if (!r.getSupportingLines().isEmpty())
			{
				connectionRoutes.add(r);
				for (Connection c : r.getConnections())
				{
					lineSetsPerConnection.get(c).add(r.getSupportingLines());
				}
			}

		}
		int nrDirect = connectionRoutes.size();
		for (PTNRoute r : indirectConnectionRoutes)
		{
			connectionRoutes.add(r);
		}
		int nrIndirect = connectionRoutes.size() - nrDirect;

		System.out.println("There are " + nrDirect + " direct routes and " + nrIndirect + " indirect routes");

		preGeneratedRoutes.addAll(connectionRoutes);

		// Initialise master with generic constraints.
		LinePlanningMasterProblem master = initialiseMasterWithDefaultConstraints(instance);

		// Add capacity and direct route constraints.
		for (DirectedGraphArc<StationNode, Connection> connectionArc : instance.getConnectionNetwork().getArcs())
		{
			master.addConstraint(new CapacityConstraint(connectionArc, LinePlanningParameters.CAPACITY));
		}
		for (Entry<Connection, Set<List<Line>>> ent : lineSetsPerConnection.entrySet())
		{
			Connection connection = ent.getKey();
			for (List<Line> lineSet : ent.getValue())
			{
				master.addConstraint(new LightBasicDirectRouteConstraint(lineSet, connection));
			}
		}

		// Add columns.
		addDefaultColumns(master, instance);
		for (PTNRoute route : connectionRoutes)
		{
			master.addColumn(new RouteColumn(route.getDuration(), false, route.getOdPair(), route));
		}

		// Retrieve line and direct route variables to initialise callback.
		Map<Line, IloNumVar> lineVariableMap = new LinkedHashMap<>();
		Map<PTNRoute, IloNumVar> directRouteVariableMap = new LinkedHashMap<>();
		for (Pair<LinePlanningColumn, IloNumVar> pair : master.getColumns())
		{
			if (pair.getKey() instanceof LineFrequencyColumn)
			{
				Line line = ((LineFrequencyColumn) pair.getKey()).getLine();
				lineVariableMap.put(line, pair.getValue());
			}
			if (pair.getKey() instanceof RouteColumn)
			{
				RouteColumn routeColumn = (RouteColumn) pair.getKey();
				if (routeColumn.getRoute() == null)
				{
					continue;
				}
				PTNRoute route = (PTNRoute) routeColumn.getRoute();
				if (route.isDirect())
				{
					directRouteVariableMap.put(route, pair.getValue());
				}
			}
		}
		KarbsteinCallback callback = new KarbsteinCallback(lineVariableMap, directRouteVariableMap);

		System.out.println("\n Start solving");
		master.setMaximumWorkMem(16384);
		master.setOut(System.out);
		int remainingSeconds = Math.max(1, cpuTimeSeconds - (int) ((System.currentTimeMillis() - solveStartTime) / 1000));
		AbstractSolution<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> abstractSol = master
				.applyRestrictedMasterHeuristicWithCallback(remainingSeconds, callback);
		// AbstractSolution<LinePlanningInstance, LinePlanningColumn,
		// LinePlanningPricingProblem> abstractSol = master
		// .applyRestrictedMasterHeuristic(remainingSeconds);
		double computationTimeSeconds = (System.currentTimeMillis() - solveStartTime) / 1000.0;

		LinePlanningSolution solution = new LinePlanningSolution(abstractSol, instance);
		solution.setComputationTimeSeconds(computationTimeSeconds);
		solution.setLowerBound(master.getLowerBoundMILP());
		return solution;
	}
}
