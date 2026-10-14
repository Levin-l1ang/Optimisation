package linePlanning.solvers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import graph.structures.digraph.DirectedGraphArc;
import ilog.concert.IloException;
import linePlanning.DPTN.DPTNRoute;
import linePlanning.PTN.Connection;
import linePlanning.PTN.LightBasicDirectRouteConstraint;
import linePlanning.PTN.PTNRoute;
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

public class PTNSolver extends Solver
{
	private final boolean karbsteinification;

	public PTNSolver(boolean karbsteinification)
	{
		super(NetworkType.PTN);

		this.karbsteinification = karbsteinification;
	}

	@Override
	public String getName()
	{
		return karbsteinification ? "PTNSolver-Karbstein" : "PTNSolver";
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
			for (PTNRoute r : rpGen.toPTNRoutes(routes, od, !karbsteinification))
			{
				if (!karbsteinification)
				{
					connectionRoutes.add(r);
				}
				else if (r.isDirect())
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
		if (karbsteinification)
		{
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
		}
		else
		{
			System.out.println("There are " + connectionRoutes.size() + " routes");
		}

		preGeneratedRoutes.addAll(connectionRoutes);

		// Initialise master with generic constraints.
		LinePlanningMasterProblem master = initialiseMasterWithDefaultConstraints(instance);

		// Add constraints.
		for (DirectedGraphArc<StationNode, Connection> connectionArc : instance.getConnectionNetwork().getArcs())
		{
			master.addConstraint(new CapacityConstraint(connectionArc, LinePlanningParameters.CAPACITY));
		}
		if (karbsteinification)
		{
			int count = 0;
			// new approach
			for (Entry<Connection, Set<List<Line>>> ent : lineSetsPerConnection.entrySet())
			{
				Connection connection = ent.getKey();
				for (List<Line> lineSet : ent.getValue())
				{
					count++;
					master.addConstraint(new LightBasicDirectRouteConstraint(lineSet, connection));
				}
			}
			System.out.println("There are " + count + " basic dr constraints");
		}

		// Add line and OD-slack columns.
		System.out.println("Adding line and  route slack cols");
		addDefaultColumns(master, instance);

		System.out.println("Start adding route cols");
		for (PTNRoute route : connectionRoutes)
		{
			master.addColumn(new RouteColumn(route.getDuration(), false, route.getOdPair(), route));
		}

		master.setMaximumWorkMem(16384);

		System.out.println("\n Start solving");
		master.setOut(System.out);
		int remainingSeconds = Math.max(1, cpuTimeSeconds - (int) ((System.currentTimeMillis() - solveStartTime) / 1000));
		AbstractSolution<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> abstractSol = master
				.applyRestrictedMasterHeuristic(remainingSeconds);
		double computationTimeSeconds = (System.currentTimeMillis() - solveStartTime) / 1000.0;

		// for(Entry<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>, Double> ent: master.getActiveSlackVariables().entrySet()) 
		// {
		// 	if(ent.getValue()>0.000001) {
		// 		System.out.println(ent.getKey()+" has value "+ent.getValue());
		// 	}
		// }

		LinePlanningSolution solution = new LinePlanningSolution(abstractSol, instance);
		solution.setComputationTimeSeconds(computationTimeSeconds);
		solution.setLowerBound(master.getLowerBoundMILP());
		return solution;
	}
}
