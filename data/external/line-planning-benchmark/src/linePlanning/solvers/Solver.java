package linePlanning.solvers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import graph.structures.digraph.DirectedGraphArc;
import ilog.concert.IloException;
import linePlanning.DPTN.DPTNRoute;
import linePlanning.instance.Route;
import linePlanning.PTN.Connection;
import linePlanning.columnGeneration.columns.LineActivationColumn;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.RouteSlackColumn;
import linePlanning.columnGeneration.constraints.BudgetConstraint;
import linePlanning.columnGeneration.constraints.DemandConstraint;
import linePlanning.columnGeneration.constraints.FixedCostCouplingConstraint;
import linePlanning.columnGeneration.constraints.FrequencyConstraint;
import linePlanning.columnGeneration.master.LinePlanningMasterProblem;
import linePlanning.columnGeneration.pricing.LinePlanningPricingRoutine.NetworkType;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.LinePlanningSolution;
import linePlanning.instance.ODPair;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import linePlanning.lineGeneration.Line;

public abstract class Solver
{
	private final NetworkType networkType;

	public Solver(NetworkType networkType)
	{
		this.networkType = networkType;
	}

	protected Set<Route> preGeneratedRoutes = new HashSet<>();

	public Set<Route> getPreGeneratedRoutes()
	{
		return preGeneratedRoutes;
	}

	protected boolean useStrongInequalities = false;

	public void setUseStrongInequalities(boolean useStrongInequalities)
	{
		this.useStrongInequalities = useStrongInequalities;
	}

	public boolean useStrongInequalities()
	{
		return useStrongInequalities;
	}

	public static Map<Station, Map<Set<ODPair>, Double>> buildGroupedVolumeMap(LinePlanningInstance instance)
	{
		// Group OD-pairs by origin.
		Map<Station, List<ODPair>> pairsByOrigin = new LinkedHashMap<>();
		Map<ODPair, Set<Connection>> pathArcs = new LinkedHashMap<>();
		for (ODPair pair : instance.getODPairs())
		{
			pairsByOrigin.computeIfAbsent(pair.getOrigin(), s -> new ArrayList<>()).add(pair);
			DPTNRoute route = instance.getDPTN().getShortestPath(pair.getOrigin(), pair.getDestination());
			pathArcs.put(pair, new LinkedHashSet<>(route.getConnectionPath()));
		}

		// Greedily partition each origin's OD-pairs into singleton groups,
		// maximising arc overlap.
		Map<Station, Map<Set<ODPair>, Double>> totalVolumeMap = new LinkedHashMap<>();
		for (Entry<Station, List<ODPair>> originEntry : pairsByOrigin.entrySet())
		{
			Station origin = originEntry.getKey();
			List<ODPair> remaining = new ArrayList<>(originEntry.getValue());
			Map<Set<ODPair>, Double> groupMap = new LinkedHashMap<>();

			while (!remaining.isEmpty())
			{
				// Seed with the highest-volume remaining pair.
				ODPair seed = remaining.stream().max((a, b) -> Double.compare(a.getVolume(), b.getVolume())).get();
				remaining.remove(seed);
				Set<ODPair> group = new LinkedHashSet<>();
				group.add(seed);
				Set<Connection> groupArcs = new LinkedHashSet<>(pathArcs.getOrDefault(seed, Set.of()));
				double totalVolume = seed.getVolume();

				while (group.size() < 1 && !remaining.isEmpty())
				{
					ODPair bestPair = null;
					int bestOverlap = -1;
					for (ODPair candidate : remaining)
					{
						int overlap = 0;
						for (Connection arc : pathArcs.getOrDefault(candidate, Set.of()))
						{
							if (groupArcs.contains(arc))
							{
								overlap++;
							}
						}
						if (overlap > bestOverlap)
						{
							bestOverlap = overlap;
							bestPair = candidate;
						}
					}
					remaining.remove(bestPair);
					group.add(bestPair);
					groupArcs.addAll(pathArcs.getOrDefault(bestPair, Set.of()));
					totalVolume += bestPair.getVolume();
				}
				groupMap.put(group, totalVolume);
			}
			totalVolumeMap.put(origin, groupMap);
		}
		return totalVolumeMap;
	}

	public LinePlanningMasterProblem initialiseMasterWithDefaultConstraints(LinePlanningInstance instance)
			throws IloException
	{
		// Initialise master.
		LinePlanningMasterProblem masterProblem = new LinePlanningMasterProblem(instance, networkType);

		// Add generic constraints.
		masterProblem.addConstraint(new BudgetConstraint(instance.getBudget()));
		for (ODPair pair : instance.getODPairs())
		{
			masterProblem.addConstraint(new DemandConstraint(pair));
		}
		for (DirectedGraphArc<StationNode, Connection> arc : instance.getConnectionNetwork().getArcs())
		{
			masterProblem.addConstraint(new FrequencyConstraint(arc.getData()));
		}
		if (LinePlanningParameters.LINE_ACTIVATION_COST > 0)
		{
			for (Line line : instance.getLinePool())
			{
				masterProblem.addConstraint(new FixedCostCouplingConstraint(line));
			}
		}
		return masterProblem;
	}

	public void addDefaultColumns(LinePlanningMasterProblem masterProblem, LinePlanningInstance instance)
			throws IloException
	{
		// Add line columns.
		for (Line line : instance.getLinePool())
		{
			masterProblem.addColumn(new LineFrequencyColumn(line));

			// Add fixed cost column.
			if (LinePlanningParameters.LINE_ACTIVATION_COST > 0)
			{
				masterProblem.addColumn(new LineActivationColumn(line));
			}
		}

		// Add slack-OD columns.
		for (ODPair od : instance.getODPairs())
		{
			masterProblem.addColumn(new RouteSlackColumn(instance.getMissedDemandPenalty(od), od));
		}
	}

	public abstract LinePlanningSolution solve(LinePlanningInstance instance, List<Line> linePool, int cpuTimeSeconds)
			throws IloException;

	public abstract String getName();
}
