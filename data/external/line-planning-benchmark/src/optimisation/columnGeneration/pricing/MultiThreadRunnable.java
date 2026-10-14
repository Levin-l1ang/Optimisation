package optimisation.columnGeneration.pricing;

import java.util.List;

import ilog.concert.IloException;
import optimisation.BAP.AbstractBranchingDecision;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import util.Pair;

public class MultiThreadRunnable<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		implements Runnable
{
	private final List<Pair<U, Double>> generatedColumns;
	private final AbstractMasterProblem<T, U, V> masterProblem;
	private final V pricingProblem;
	private final AbstractPricingRoutine<T, U, V> pricingRoutine;
	private final AbstractPricingProblemSolver<T, U, V> pricingProblemSolver;
	private final double reducedCostThreshold;
	private final boolean enumerateColumns;

	public MultiThreadRunnable(List<Pair<U, Double>> generatedColumns, AbstractMasterProblem<T, U, V> masterProblem,
			V pricingProblem, AbstractPricingRoutine<T, U, V> pricingRoutine,
			AbstractPricingProblemSolver<T, U, V> pricingProblemSolver, double reducedCostThreshold,
			boolean enumerateColumns)
	{
		this.generatedColumns = generatedColumns;
		this.masterProblem = masterProblem;
		this.pricingProblem = pricingProblem;
		this.pricingRoutine = pricingRoutine;
		this.pricingProblemSolver = pricingProblemSolver;
		this.reducedCostThreshold = reducedCostThreshold;
		this.enumerateColumns = enumerateColumns;
	}

	@Override
	public void run()
	{
		// Ensure that the data in the pricingproblem is threadsafe.
		pricingProblem.makeThreadSafe();

		// Process branching decisions and perform problem-specific dual updating.
		for (AbstractBranchingDecision<T, U, V> branchingDecision : masterProblem.getBranchingDecisions())
		{
			if (!branchingDecision.isCompatible(pricingProblem))
			{
				return;
			}
			branchingDecision.modifyPricingProblem(pricingProblem);
		}

		try
		{
			// Reset duals.
			pricingRoutine.preProcessPricingProblem(masterProblem.getInstance(), pricingProblem);

			// Update pricing problem-specific duals.
			masterProblem.updatePricingProblemDuals(pricingProblem);
		}
		catch (IloException e)
		{
			e.printStackTrace();
		}

		// Solve the pricing problem.
		generatedColumns.addAll(pricingProblemSolver.generateColumns(masterProblem, pricingProblem,
				reducedCostThreshold, enumerateColumns));
	}
}
