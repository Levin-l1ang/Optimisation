package linePlanning.solvers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import graph.structures.digraph.DirectedGraphArc;
import ilog.concert.IloException;
import linePlanning.DPTN.DPTNArc;
import linePlanning.DPTN.DPTNDrivingArc;
import linePlanning.DPTN.DPTNDwellArc;
import linePlanning.DPTN.DPTNRoute;
import linePlanning.columnGeneration.branching.ConnectionBranchingRule;
import linePlanning.columnGeneration.branching.LineActivationBranchingRule;
import linePlanning.columnGeneration.branching.LineBranchingRule;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
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
import linePlanning.routeGeneration.RouteGenerator;
import optimisation.BAP.BranchAndPrice;
import optimisation.BAP.NodeComparators.BoundComparator;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.columnManagement.AllColumnSelector;

public class DPTNSolver extends Solver
{
	private final boolean generateColumns;

	public DPTNSolver(boolean generateColumns, boolean useStrongInequalities)
	{
		super(NetworkType.DPTN);

		this.generateColumns = generateColumns;
		this.useStrongInequalities = useStrongInequalities;
	}

	@Override
	public String getName()
	{
		if (!generateColumns) return "DPTNSolver-MIP";
		return useStrongInequalities ? "DPTNSolver-BAPC" : "DPTNSolver-BAP";
	}

	@Override
	public LinePlanningSolution solve(LinePlanningInstance instance, List<Line> linePool, int cpuTimeSeconds)
			throws IloException
	{
		System.out.println("Lower Bound on Travel Time: " + instance.getLowerBound() + " or "
				+ instance.getAverageTTBound() + " per pax");
		long solveStartTime = System.currentTimeMillis();

		// Prepare lines.
		instance.setLinePool(linePool);

		// Initialise master with generic constraints.
		LinePlanningMasterProblem master = initialiseMasterWithDefaultConstraints(instance);

		// Add capacity constraints.
		for (DirectedGraphArc<StationNode, DPTNArc> desnArc : instance.getDPTN().getArcs())
		{
			DPTNArc arc = desnArc.getData();
			if (arc instanceof DPTNDrivingArc)
			{
				DPTNDrivingArc capacitatedArc = (DPTNDrivingArc) arc;
				master.addConstraint(new CapacityConstraint(desnArc, capacitatedArc.getCapacity()));
			}
			if (arc instanceof DPTNDwellArc)
			{
				DPTNDwellArc capacitatedArc = (DPTNDwellArc) arc;
				master.addConstraint(new CapacityConstraint(desnArc, capacitatedArc.getCapacity()));
			}
		}

		// Add line and OD-slack columns.
		addDefaultColumns(master, instance);

		if (!generateColumns)
		{
			RouteGenerator rpGen = new RouteGenerator(instance, instance.getRouteGenerationFile());

			for (ODPair od : instance.getODPairs())
			{
				List<DPTNRoute> routes = rpGen.getRoutes(od, false);
				for (DPTNRoute route : routes)
				{
					preGeneratedRoutes.add(route);
					master.addColumn(new RouteColumn(route.getDuration(), false, od, route));
				}
			}
			master.setMaximumWorkMem(16384); //32768
			master.setOut(System.out);
			int remainingSeconds = Math.max(1, cpuTimeSeconds - (int) ((System.currentTimeMillis() - solveStartTime) / 1000));
			AbstractSolution<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> abstractSol = master
					.applyRestrictedMasterHeuristic(remainingSeconds);
			double computationTimeSeconds = (System.currentTimeMillis() - solveStartTime) / 1000.0;
			LinePlanningSolution solution = new LinePlanningSolution(abstractSol, instance);
			solution.setComputationTimeSeconds(computationTimeSeconds);
			solution.setLowerBound(master.getLowerBoundMILP());
			return solution;
		}

		// Initialise column generation framework.
		LinePlanningPricingRoutine routine = new LinePlanningPricingRoutine(NetworkType.DPTN);
		LinePlanningPricingProblemSolver<DPTNArc> pricingAlgorithm = new LinePlanningPricingProblemSolver<>(
				instance.getDPTN());
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

		LinePlanningSolution solution = new LinePlanningSolution(branchAndPrice.getBestSolution(), instance);
		solution.setComputationTimeSeconds(computationTimeSeconds);
		solution.setLowerBound(branchAndPrice.getLowerBound());
		return solution;
	}
}
