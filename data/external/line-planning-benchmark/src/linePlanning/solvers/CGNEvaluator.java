package linePlanning.solvers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import graph.structures.digraph.DirectedGraphArc;
import ilog.concert.IloException;
import linePlanning.CGN.CGNArc;
import linePlanning.CGN.CGNTravelArc;
import linePlanning.PTN.Connection;
import linePlanning.columnGeneration.columns.LineActivationColumn;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteSlackColumn;
import linePlanning.columnGeneration.constraints.CapacityConstraint;
import linePlanning.columnGeneration.constraints.DemandConstraint;
import linePlanning.columnGeneration.constraints.FrequencyConstraint;
import linePlanning.columnGeneration.master.LinePlanningMasterProblem;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblemSolver;
import linePlanning.columnGeneration.pricing.LinePlanningPricingRoutine;
import linePlanning.columnGeneration.pricing.LinePlanningPricingRoutine.NetworkType;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.LinePlanningSolution;
import linePlanning.instance.ODPair;
import linePlanning.instance.StationNode;
import linePlanning.lineGeneration.Line;
import optimisation.BAP.BranchAndPrice;
import optimisation.BAP.NodeComparators.BoundComparator;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.columnManagement.AllColumnSelector;

public class CGNEvaluator
{
	public LinePlanningSolution solve(LinePlanningInstance instance, Map<Line, Integer> frequencyMap)
			throws IloException
	{
		System.out.println("Lower Bound on Travel Time: " + instance.getLowerBound() + " or "
				+ instance.getAverageTTBound() + " per pax");

		// Derive a restricted line pool.
		List<Line> restrictedLinePool = new ArrayList<>();
		for (Line line : frequencyMap.keySet())
		{
			if (frequencyMap.get(line) > 0)
			{
				restrictedLinePool.add(line);
			}
		}

		// Prepare lines and CGN.
		instance.deriveCGN(restrictedLinePool);

		// Initialise master.
		LinePlanningMasterProblem master = new LinePlanningMasterProblem(instance, NetworkType.CGN);

		// Add constraints.
		for (ODPair pair : instance.getODPairs())
		{
			master.addConstraint(new DemandConstraint(pair));
		}
		for (DirectedGraphArc<StationNode, CGNArc> arc : instance.getCGN().getArcs())
		{
			if (arc.getData() instanceof CGNTravelArc)
			{
				master.addConstraint(new CapacityConstraint(arc, LinePlanningParameters.CAPACITY));
			}
		}
		for (DirectedGraphArc<StationNode, Connection> arc : instance.getConnectionNetwork().getArcs())
		{
			master.addConstraint(new FrequencyConstraint(arc.getData()));
		}

		// Add columns.
		for (Line line : frequencyMap.keySet())
		{
			// Add line column.
			LineFrequencyColumn lineColumn = new LineFrequencyColumn(line);
			master.addColumn(lineColumn);
			master.fixColumn(lineColumn, frequencyMap.get(line));

			// Add fixed cost column.
			master.addColumn(new LineActivationColumn(line));
		}
		for (ODPair od : instance.getODPairs())
		{
			master.addColumn(new RouteSlackColumn(instance.getMissedDemandPenalty(od), od));
		}

		// Initialise column generation framework.
		LinePlanningPricingRoutine routine = new LinePlanningPricingRoutine(NetworkType.CGN);
		LinePlanningPricingProblemSolver<CGNArc> pricingAlgorithm = new LinePlanningPricingProblemSolver<>(
				instance.getCGN());
		AllColumnSelector<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> selector = new AllColumnSelector<>();
		ColumnGeneration<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> columnGeneration = new ColumnGeneration<>(
				routine, pricingAlgorithm, pricingAlgorithm, selector);

		// Initialise branch-and-price.
		BranchAndPrice<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> branchAndPrice = new BranchAndPrice<>(
				new BoundComparator<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>(), instance,
				master, columnGeneration);

		// Apply branch and price.
		System.out.println("\n Start solving");
		branchAndPrice.applyBranchAndPrice();

		int uniqueLines = 0;
		for (Entry<LinePlanningColumn, Double> entry : branchAndPrice.getBestSolution().getColumnMap().entrySet())
		{
			if (entry.getKey() instanceof LineFrequencyColumn)
			{
				uniqueLines++;
			}
		}
		System.out.println("#unique lines: " + uniqueLines);

		//printCapacityAndRoutes(branchAndPrice.getBestSolution().getColumnMap(), instance, "LZ", "ZG", "LZ", "ZG");

		// Return solution.
		return new LinePlanningSolution(branchAndPrice.getBestSolution(), instance);
	}

	private void printCapacityAndRoutes(Map<LinePlanningColumn, Double> columnMap, LinePlanningInstance instance,
			String connFrom, String connTo, String odFrom, String odTo)
	{
		Map<CGNTravelArc, Double> flow = new HashMap<>();
		Map<CGNTravelArc, Double> capacity = new HashMap<>();
		Map<ODPair, Double> flowPerOD = new HashMap<>();
		for (Entry<LinePlanningColumn, Double> entry : columnMap.entrySet())
		{
			if (entry.getKey() instanceof linePlanning.columnGeneration.columns.RouteColumn
					&& !(entry.getKey() instanceof RouteSlackColumn))
			{
				linePlanning.columnGeneration.columns.RouteColumn rc =
						(linePlanning.columnGeneration.columns.RouteColumn) entry.getKey();
				for (linePlanning.columnGeneration.pricing.NetworkArc na : rc.getRoute().getArcs())
				{
					if (na instanceof CGNTravelArc)
					{
						CGNTravelArc ta = (CGNTravelArc) na;
						String from = ta.getLineSegment().getFrom().getName();
						String to   = ta.getLineSegment().getTo().getName();
						if (from.equals(connFrom) && to.equals(connTo))
						{
							flow.merge(ta, entry.getValue(), Double::sum);
							flowPerOD.merge(rc.getODPair(), entry.getValue(), Double::sum);
						}
					}
				}
			}
			else if (entry.getKey() instanceof LineFrequencyColumn)
			{
				LineFrequencyColumn lc = (LineFrequencyColumn) entry.getKey();
				for (DirectedGraphArc<StationNode, CGNArc> graphArc : instance.getCGN().getArcs())
				{
					if (!(graphArc.getData() instanceof CGNTravelArc))
						continue;
					CGNTravelArc ta = (CGNTravelArc) graphArc.getData();
					if (!ta.getLine().equals(lc.getLine()))
						continue;
					String from = ta.getLineSegment().getFrom().getName();
					String to   = ta.getLineSegment().getTo().getName();
					if (from.equals(connFrom) && to.equals(connTo))
						capacity.merge(ta, entry.getValue() * LinePlanningParameters.CAPACITY, Double::sum);
				}
			}
		}
		System.out.printf("Capacity constraints for %s -> %s:%n", connFrom, connTo);
		for (CGNTravelArc ta : flow.keySet())
			System.out.printf("  %s: LHS=%.2f, RHS=%.2f%n",
					ta, flow.getOrDefault(ta, 0.0), capacity.getOrDefault(ta, 0.0));
		System.out.printf("Flow over %s -> %s per OD pair:%n", connFrom, connTo);
		flowPerOD.entrySet().stream()
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
				.forEach(e -> System.out.printf("  %s -> %s: %.2f%n",
						e.getKey().getOrigin().getName(), e.getKey().getDestination().getName(), e.getValue()));

		System.out.printf("Routes for %s -> %s:%n", odFrom, odTo);
		for (Entry<LinePlanningColumn, Double> entry : columnMap.entrySet())
		{
			if (!(entry.getKey() instanceof linePlanning.columnGeneration.columns.RouteColumn)
					|| entry.getKey() instanceof RouteSlackColumn)
				continue;
			linePlanning.columnGeneration.columns.RouteColumn rc =
					(linePlanning.columnGeneration.columns.RouteColumn) entry.getKey();
			if (rc.getODPair().getOrigin().getName().equals(odFrom)
					&& rc.getODPair().getDestination().getName().equals(odTo))
				System.out.printf("  flow=%.2f  %s%n", entry.getValue(), rc.getRoute());
		}
	}
}
