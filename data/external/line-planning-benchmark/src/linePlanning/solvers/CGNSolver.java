package linePlanning.solvers;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import graph.structures.digraph.DirectedGraphArc;
import ilog.concert.IloException;
import linePlanning.CGN.CGNArc;
import linePlanning.CGN.CGNTravelArc;
import linePlanning.columnGeneration.branching.ConnectionBranchingRule;
import linePlanning.columnGeneration.branching.LineActivationBranchingRule;
import linePlanning.columnGeneration.branching.LineBranchingRule;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.constraints.CapacityConstraint;
import linePlanning.columnGeneration.cuts.LineActivationStrongInequalitySeparator;
import linePlanning.columnGeneration.cuts.StrongInequalitySeparator;
import linePlanning.columnGeneration.diving.LinePlanningDivingHeuristic;
import linePlanning.columnGeneration.master.LinePlanningMasterProblem;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblemSolver;
import linePlanning.columnGeneration.pricing.LinePlanningPricingRoutine;
import linePlanning.columnGeneration.pricing.LinePlanningPricingRoutine.NetworkType;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.instance.LinePlanningSolution;
import linePlanning.instance.ODPair;
import linePlanning.instance.Station;
import linePlanning.instance.StationNode;
import linePlanning.lineGeneration.Line;
import optimisation.BAP.BranchAndPrice;
import optimisation.BAP.NodeComparators.BoundComparator;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.columnManagement.AllColumnSelector;

public class CGNSolver extends Solver
{
	public CGNSolver(boolean useStrongInequalities)
	{
		super(NetworkType.CGN);

		this.useStrongInequalities = useStrongInequalities;
	}

	@Override
	public String getName()
	{
		return useStrongInequalities ? "CGNSolver-cuts" : "CGNSolver";
	}

	@Override
	public LinePlanningSolution solve(LinePlanningInstance instance, List<Line> linePool, int cpuTimeSeconds)
			throws IloException
	{
		System.out.println("Lower Bound on Travel Time: " + instance.getLowerBound() + " or "
				+ instance.getAverageTTBound() + " per pax");
		long solveStartTime = System.currentTimeMillis();

		// Prepare lines and CGN.
		instance.deriveCGN(linePool);
		System.out.println("CGN has " + instance.getCGN().getNumberOfNodes() + " nodes and "
				+ instance.getCGN().getNumberOfArcs() + " arcs.");
		System.out.println("There are " + instance.getODPairs().size() + " OD pairs.");

		// Initialise master with generic constraints.
		LinePlanningMasterProblem master = initialiseMasterWithDefaultConstraints(instance);

		// Add constraints.
		for (DirectedGraphArc<StationNode, CGNArc> arc : instance.getCGN().getArcs())
		{
			if (arc.getData() instanceof CGNTravelArc)
			{
				master.addConstraint(new CapacityConstraint(arc, LinePlanningParameters.CAPACITY));
			}
		}

		// Add line and OD-slack columns.
		addDefaultColumns(master, instance);

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
		branchAndPrice.addBranchingRule(new ConnectionBranchingRule(0));
		if (LinePlanningParameters.LINE_ACTIVATION_COST > 0)
		{
			branchAndPrice.addBranchingRule(new LineActivationBranchingRule(1));
		}
		branchAndPrice.addBranchingRule(new LineBranchingRule(2));
		if (useStrongInequalities)
		{
			// Build grouped strong inequality separator, partitioning OD-pairs by arc
			// overlap.
			Map<Station, Map<Set<ODPair>, Double>> totalVolumeMap = buildGroupedVolumeMap(instance);
			branchAndPrice.addCutSeparator(new StrongInequalitySeparator(0.01, totalVolumeMap));
			if (LinePlanningParameters.LINE_ACTIVATION_COST > 0)
			{
				branchAndPrice.addCutSeparator(new LineActivationStrongInequalitySeparator(0.01, totalVolumeMap));
			}
		}

		// Use diving heuristic on the root node.
		branchAndPrice.setRootNodeHeuristic(new LinePlanningDivingHeuristic(linePool, instance.getBudget()));

		// Apply branch and price.
		int remainingSeconds = Math.max(1, cpuTimeSeconds - (int) ((System.currentTimeMillis() - solveStartTime) / 1000));
		branchAndPrice.setTimeLimit(remainingSeconds * 1000L);
		System.out.println("\n Start solving");
		branchAndPrice.applyBranchAndPrice();
		double computationTimeSeconds = (System.currentTimeMillis() - solveStartTime) / 1000.0;

		// Return solution (getBestSolution() is null when B&P finds no feasible
		// solution).
		LinePlanningSolution solution = new LinePlanningSolution(branchAndPrice.getBestSolution(), instance);
		solution.setComputationTimeSeconds(computationTimeSeconds);
		solution.setLowerBound(branchAndPrice.getLowerBound());

		if (branchAndPrice.getBestSolution() != null)
		{
			int uniqueLines = 0;
			for (Entry<LinePlanningColumn, Double> entry : branchAndPrice.getBestSolution().getColumnMap().entrySet())
			{
				if (entry.getKey() instanceof LineFrequencyColumn)
					uniqueLines++;
			}
			System.out.println("#unique lines: " + uniqueLines);
		}
		else
		{
			System.out.println("No feasible solution found.");
		}
		return solution;
	}
}
