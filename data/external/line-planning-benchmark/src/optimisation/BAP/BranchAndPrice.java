package optimisation.BAP;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ilog.concert.IloException;
import optimisation.BAP.enumeration.AbstractEnumerationRoutine;
import optimisation.BAP.enumeration.EnumerationSolver;
import optimisation.BAP.primalHeuristics.AbstractPrimalHeuristic;
import optimisation.BAP.primalHeuristics.RestrictedMasterHeuristic;
import optimisation.BAP.strongBranching.StrongBranching;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.DualVariables;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import optimisation.columnGeneration.pricing.AbstractPricingRoutine;
import optimisation.cuts.AbstractCutSeparator;
import util.Configuration;
import util.Logger;
import util.Logger.TimeQuantity;
import util.Logger.ValueQuantity;
import util.Pair;
import util.Writer;

public class BranchAndPrice<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	private final BranchingTree<T, U, V> branchingTree;
	private List<AbstractBranchingRule<T, U, V>> branchingRules;

	private List<AbstractCutSeparator<T, U, V>> cutSeparators;

	private AbstractPrimalHeuristic<T, U, V> rootNodeHeuristic = null;
	private RestrictedMasterHeuristic<T, U, V> restrictedMasterHeuristic;
	private AbstractReducedCostFixing<T, U, V> reducedCostFixing = null;

	private final T instance;
	private final AbstractMasterProblem<T, U, V> masterProblem;
	private final ColumnGeneration<T, U, V> columnGeneration;
	private final AbstractPricingRoutine<T, U, V> pricingRoutine;

	private AbstractSolution<T, U, V> bestSolution;

	private long timeLimit = Long.MAX_VALUE;
	private int iterationsWithoutRMH = 0;
	private Logger logger;

	public BranchAndPrice(Comparator<BAPNode<T, U, V>> comparator, T instance,
			AbstractMasterProblem<T, U, V> masterProblem, ColumnGeneration<T, U, V> columnGeneration)
	{
		this.branchingTree = new BranchingTree<T, U, V>(comparator);
		this.branchingRules = new ArrayList<>();

		this.cutSeparators = new ArrayList<>();

		this.restrictedMasterHeuristic = new RestrictedMasterHeuristic<>(
				Configuration.getConfiguration().getIntProperty("RMH_TIME_LIMIT"));

		this.instance = instance;
		this.masterProblem = masterProblem;
		this.columnGeneration = columnGeneration;
		this.pricingRoutine = columnGeneration.getPricingRoutine();

		this.bestSolution = null;
		this.logger = Logger.getLogger();
	}

	public void setRestrictedMasterHeuristic(RestrictedMasterHeuristic<T, U, V> restrictedMasterHeuristic)
	{
		this.restrictedMasterHeuristic = restrictedMasterHeuristic;
	}

	public void applyBranchAndPrice() throws IloException
	{
		// Initialise a root node and last processed node.
		BAPNode<T, U, V> rootNode = new BAPNode<T, U, V>(null);
		BAPNode<T, U, V> previousNode = rootNode;

		// Keep track of performance.
		double previousUB = branchingTree.getUpperBound();
		double previousGap = Double.MAX_VALUE;
		logger.resetNode();

		// Add root node to the tree.
		branchingTree.enqueue(rootNode);

		while (!branchingTree.isEmpty())
		{
			// Process the first node from the queue.
			BAPNode<T, U, V> parent = branchingTree.dequeue();

			// Process branching decisions.
			processBranchingDecisions(previousNode, parent);

			// Solve this node with column generation.
			if (parent.getPotentialColumns() != null)
			{
				columnGeneration.setPotentialColumns(parent.getPotentialColumns());
			}

			// Check whether we need feasible (optimal) duals. If this is the case, we
			// cannot terminate column generation at the primal lower bound.
			double lowerBound = parent.getLowerBound();
			if (reducedCostFixing != null)
			{
				lowerBound = Double.MAX_VALUE;
			}
			if (branchingTree.getUpperBound() - parent.getLowerBound() <= Configuration.getConfiguration()
					.getDoubleProperty("ENUMERATION_THRESHOLD"))
			{
				lowerBound = 0;
			}

			// Determine which cut routines are active.
			boolean isRootNode = parent.equals(rootNode);
			List<AbstractCutSeparator<T, U, V>> activeCutSeparators = new ArrayList<>();
			for (AbstractCutSeparator<T, U, V> cutSeparator : cutSeparators)
			{
				if (cutSeparator.separate(isRootNode))
				{
					cutSeparator.setMaxNumberIterations(isRootNode);
					activeCutSeparators.add(cutSeparator);
				}
			}

			// Solve RMP with column generation and cutting planes.
			if (isRootNode)
			{
				columnGeneration.applyColumnGeneration(masterProblem, instance, lowerBound);
				if (!activeCutSeparators.isEmpty() && masterProblem.isFeasible())
				{
					// If cut separators are added, call column generation again but now including a
					// cut separation routine. Only do this if the node is still feasible.
					columnGeneration.setCutSeparators(activeCutSeparators);
					columnGeneration.applyColumnGeneration(masterProblem, instance, lowerBound);
					columnGeneration.removeCutSeparators();
				}
			}
			else
			{
				columnGeneration.setCutSeparators(activeCutSeparators);
				columnGeneration.applyColumnGeneration(masterProblem, instance, lowerBound);
				columnGeneration.removeCutSeparators();
			}

			// Choose the enumeration routine.
			AbstractEnumerationRoutine<T, U, V> enumerationRoutine = columnGeneration.getPricingRoutine();
			if (parent.getPotentialColumns() != null)
			{
				enumerationRoutine = new EnumerationSolver<T, U, V>(parent.getPotentialColumns());
			}

			// Process the node.
			processNode(parent.equals(rootNode), parent, enumerationRoutine);

			// Update lower bound.
			branchingTree.updateLowerBound();

			// Update bounds and node counter.
			logger.setValue(ValueQuantity.VALUE_LOWER_BOUND, branchingTree.getLowerBound());
			logger.setValue(ValueQuantity.VALUE_UPPER_BOUND, branchingTree.getUpperBound());
			logger.increaseNode();

			// Termination criterion.
			double absoluteGap = Math.max(0, branchingTree.getUpperBound() - branchingTree.getLowerBound());
			double gap = 100.0 * absoluteGap / branchingTree.getUpperBound();
			if (logger.getNode() % 1 == 0 || branchingTree.getUpperBound() != previousUB
					|| Math.abs(gap - previousGap) > 1)
			{
				System.out.println("LB: " + Writer.formatDouble(branchingTree.getLowerBound()) + ". UB:"
						+ Writer.formatDouble(branchingTree.getUpperBound()) + ". Gap: " + Writer.formatDouble(gap)
						+ "%. Node: " + logger.getNode() + ". Time (ms): " + logger.getTime() + ". Nodes: "
						+ branchingTree.getNumberOfNodes());
			}
			if (absoluteGap < Configuration.getConfiguration().getDoubleProperty("PRECISION"))
			{
				System.out.println("LB: " + branchingTree.getLowerBound() + ". UB:" + branchingTree.getUpperBound()
						+ ". Gap: " + gap + "%. Time (ms): " + logger.getTime());
				break;
			}

			// Terminate if time limit has been reached.
			if (logger.getTime() >= timeLimit)
			{
				System.out.println("Terminating due to time limit.");
				break;
			}

			// Update last processed node.
			previousNode = parent;
			previousUB = branchingTree.getUpperBound();
			previousGap = gap;
		}

	}

	private void processNode(boolean isRootNode, BAPNode<T, U, V> parent,
			AbstractEnumerationRoutine<T, U, V> enumerationRoutine) throws IloException
	{
		// Update number of iterations without calling RMH.
		iterationsWithoutRMH++;

		// Deal with infeasibilities.
		if (!masterProblem.isFeasible())
		{
			// The node should be pruned, and no child nodes should be created.
			parent.setLowerBound(Double.MAX_VALUE);
			return;
		}

		// Update the upper and lower bound of this node. Also, store the optimal dual
		// variables.
		parent.setSolution(masterProblem.getSolution());
		parent.setLowerBound(masterProblem.getObjectiveValue());
		DualVariables<T, U, V> optimalDuals = masterProblem.getCurrentDuals();

		// If the lower bound exceeds the best incumbent solution, we can terminate
		// early.
		if (parent.getLowerBound() >= branchingTree.getUpperBound())
		{
			return;
		}

		// Determine the first branching rule that applies, if any.
		List<BranchingCandidate<T, U, V>> branchingCandidates = new ArrayList<>();
		boolean canBranch = false;
		logger.startTimer(TimeQuantity.TIME_BRANCHING);
		for (AbstractBranchingRule<T, U, V> rule : branchingRules)
		{
			branchingCandidates = rule.getBranchingCandidates(parent);
			if (branchingCandidates.size() > 0)
			{
				canBranch = true;
				break;
			}
		}
		logger.stopTimer(TimeQuantity.TIME_BRANCHING);

		// When no branching rule applies, the solution forms a valid upper bound.
		if (!canBranch)
		{
			System.out.println("Integral solution with objective " + parent.getLowerBound());
			parent.setUpperBound(parent.getLowerBound());
			if (parent.getUpperBound() < branchingTree.getUpperBound())
			{
				bestSolution = parent.getSolution();
				branchingTree.setUpperBound(parent.getUpperBound());
			}
			return;
		}

		// Apply root node heuristic, if any.
		if (isRootNode && rootNodeHeuristic != null)
		{
			System.out.println("Starting root node heuristic.");
			long remainingTime = Math.max(0, timeLimit - logger.getTime());
			logger.startTimer(TimeQuantity.TIME_HEURISTIC);
			AbstractSolution<T, U, V> heuristicSolution = rootNodeHeuristic.applyHeuristic(masterProblem,
					columnGeneration, parent.getLowerBound(), remainingTime);
			logger.stopTimer(TimeQuantity.TIME_HEURISTIC);

			// Update UB.
			if (heuristicSolution != null && heuristicSolution.getObjectiveValue() < branchingTree.getUpperBound())
			{
				System.out
						.println("Root node heuristic found better solution: " + heuristicSolution.getObjectiveValue());
				branchingTree.setUpperBound(heuristicSolution.getObjectiveValue());
				bestSolution = heuristicSolution;
			}
			masterProblem.setDuals(optimalDuals);
			masterProblem.updateGenericDuals();
		}

		// Apply RMH, possibly combined with enumeration procedure.
		double enumerationThreshold = Configuration.getConfiguration().getDoubleProperty("ENUMERATION_THRESHOLD");
		double gap = branchingTree.getUpperBound() - parent.getLowerBound();
		int frequency = Configuration.getConfiguration().getIntProperty("RMH_FREQUENCY");
		double heuristicEnumerationThreshold = Configuration.getConfiguration()
				.getDoubleProperty("HEURISTIC_ENUMERATION_THRESHOLD");
		int maxEnumeratedColumns = Configuration.getConfiguration().getIntProperty("MAX_ENUMERATED_COLUMNS");
		int maxRMHColumns = Configuration.getConfiguration().getIntProperty("MAX_RMH_COLUMNS");
		List<U> potentialColumns = new ArrayList<>();
		boolean useHeuristic = frequency > 0 && iterationsWithoutRMH >= frequency;
		boolean useExact = gap <= enumerationThreshold;
		if (useHeuristic || useExact)
		{
			iterationsWithoutRMH = 0;
			double threshold = useExact ? gap : heuristicEnumerationThreshold;
			if (threshold > 0)
			{
				logger.startTimer(TimeQuantity.TIME_ENUMERATION);
				for (Pair<U, Double> pair : enumerationRoutine.enumerateColumns(masterProblem,
						columnGeneration.getPricingProblemSolver(true), instance, threshold, maxEnumeratedColumns))
				{
					potentialColumns.add(pair.getKey());
				}

				logger.stopTimer(TimeQuantity.TIME_ENUMERATION);
			}
			System.out.println("# Potential cols: " + potentialColumns.size());
			if (potentialColumns.size() <= maxRMHColumns)
			{
				// Add all columns.
				for (U column : potentialColumns)
				{
					masterProblem.addColumn(column);
				}

				// Apply RMH.
				logger.startTimer(TimeQuantity.TIME_HEURISTIC);
				long remainingTime = Math.max(0, timeLimit - logger.getTime());
				AbstractSolution<T, U, V> heuristicSolution = restrictedMasterHeuristic.applyHeuristic(masterProblem,
						columnGeneration, parent.getLowerBound(), remainingTime);
				logger.stopTimer(TimeQuantity.TIME_HEURISTIC);

				// Update UB.
				if (heuristicSolution != null && heuristicSolution.getObjectiveValue() < branchingTree.getUpperBound())
				{
					System.out.println("RMH found better solution: " + heuristicSolution.getObjectiveValue());
					branchingTree.setUpperBound(heuristicSolution.getObjectiveValue());
					bestSolution = heuristicSolution;
				}

				// Update LB, only if we were in exact mode.
				if (restrictedMasterHeuristic.getLowerBound() >= branchingTree.getUpperBound() && useExact)
				{
					System.out.println(
							"RMH has proved suboptimality of this node: " + restrictedMasterHeuristic.getLowerBound()
									+ ". Solution: " + heuristicSolution.getObjectiveValue());
					parent.setLowerBound(restrictedMasterHeuristic.getLowerBound());
					return;
				}
			}
		}

		// Apply reduced cost fixing.
		AbstractReducedCostFixingDecision<T, U, V> fixingDecision = null;
		logger.startTimer(TimeQuantity.TIME_REDUCED_COST_FIXING);
		if (reducedCostFixing != null)
		{
			fixingDecision = reducedCostFixing.applyReducedCostFixing(masterProblem, pricingRoutine,
					columnGeneration.getPricingProblemSolver(true), instance, gap);
		}
		logger.stopTimer(TimeQuantity.TIME_REDUCED_COST_FIXING);

		// Complete strong branching procedure.
		logger.startTimer(TimeQuantity.TIME_BRANCHING);
		StrongBranching<T, U, V> strongBranching = new StrongBranching<>();
		BranchingCandidate<T, U, V> candidate = strongBranching.determineBranchingCandidate(branchingCandidates,
				masterProblem, columnGeneration, parent.getLowerBound());
		for (BAPNode<T, U, V> child : strongBranching.getChildren(parent, candidate))
		{
			// Pass lower bound to child node.
			child.setLowerBound(parent.getLowerBound());

			// Apply reduced cost fixing.
			if (fixingDecision != null)
			{
				child.setReducedCostFixingDecision(fixingDecision);
			}

			// If the number of columns is sufficiently low, we store them in the child
			// node.
			if (potentialColumns.size() < maxEnumeratedColumns && gap <= enumerationThreshold)
			{
				System.out.println("Passing columns to child.");
				child.setPotentialColumns(potentialColumns);
			}
			branchingTree.enqueue(child);
		}
		logger.stopTimer(TimeQuantity.TIME_BRANCHING);
	}

	public void addBranchingRule(AbstractBranchingRule<T, U, V> branchingRule)
	{
		branchingRules.add(branchingRule);
		branchingRules.sort((a, b) -> Double.compare(a.priority, b.priority));
	}

	public void addCutSeparator(AbstractCutSeparator<T, U, V> cutSeparator)
	{
		this.cutSeparators.add(cutSeparator);
	}

	public AbstractSolution<T, U, V> getBestSolution()
	{
		return bestSolution;
	}

	public double getLowerBound()
	{
		return branchingTree.getLowerBound();
	}

	public double getUpperBound()
	{
		return branchingTree.getUpperBound();
	}

	public void setRootNodeHeuristic(AbstractPrimalHeuristic<T, U, V> heuristic)
	{
		this.rootNodeHeuristic = heuristic;
	}

	public void setReducedCostFixing(AbstractReducedCostFixing<T, U, V> reducedCostFixing)
	{
		this.reducedCostFixing = reducedCostFixing;
	}

	private void processBranchingDecisions(BAPNode<T, U, V> previousNode, BAPNode<T, U, V> nextNode) throws IloException
	{
		// Find lowest common ancestor.
		BAPNode<T, U, V> LCA = branchingTree.getLeastCommonAncestor(previousNode, nextNode);

		// Undo decisions on path from previous node to LCA.
		BAPNode<T, U, V> current = previousNode;
		while (!current.equals(LCA))
		{
			for (AbstractBranchingDecision<T, U, V> decision : current.getBranchingDecisions())
			{
				masterProblem.undoBranchingDecision(decision);
			}
			current = current.getParent();
		}

		// Process decisions on path from LCA to next node.
		current = nextNode;
		while (!current.equals(LCA))
		{
			for (AbstractBranchingDecision<T, U, V> decision : current.getBranchingDecisions())
			{
				masterProblem.processBranchingDecision(decision);
			}
			current = current.getParent();
		}

		// Process reduced cost fixing decisions, if any.
		if (nextNode.getReducedCostFixingDecision() != null)
		{
			nextNode.getReducedCostFixingDecision().modifyInstance(instance);
		}
	}

	public void setUpperBound(double upperBound)
	{
		branchingTree.setUpperBound(upperBound);
	}

	/**
	 * 
	 * @param timeLimit Time limit in milliseconds.
	 */
	public void setTimeLimit(long timeLimit)
	{
		this.timeLimit = timeLimit;
	}
}
