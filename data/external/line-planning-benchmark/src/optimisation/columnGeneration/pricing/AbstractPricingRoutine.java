package optimisation.columnGeneration.pricing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ilog.concert.IloException;
import optimisation.BAP.AbstractBranchingDecision;
import optimisation.BAP.enumeration.AbstractEnumerationRoutine;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import util.Pair;

public abstract class AbstractPricingRoutine<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		implements AbstractEnumerationRoutine<T, U, V>
{
	private final double reducedCostThreshold;
	private final int numThreads;

	private List<V> pricingProblems;

	public AbstractPricingRoutine()
	{
		this.reducedCostThreshold = -1.0 * util.Configuration.getConfiguration().getDoubleProperty("PRECISION");
		this.numThreads = util.Configuration.getConfiguration().getIntProperty("NUM_THREADS");
	}

	public void constructPricingProblems(AbstractMasterProblem<T, U, V> masterProblem, T instance)
	{
		// Generate pricing problems and process branching decisions.
		this.pricingProblems = new ArrayList<>();
		pricingProblemLoop: for (V pricingProblem : generatePricingProblems(instance))
		{
			for (AbstractBranchingDecision<T, U, V> branchingDecision : masterProblem.getBranchingDecisions())
			{
				if (!branchingDecision.isCompatible(pricingProblem))
				{
					continue pricingProblemLoop;
				}
				branchingDecision.modifyPricingProblem(pricingProblem);
			}
			pricingProblems.add(pricingProblem);
		}
	}

	public List<Pair<U, Double>> generateColumns(AbstractMasterProblem<T, U, V> masterProblem,
			AbstractPricingProblemSolver<T, U, V> pricingProblemSolver, T instance) throws IloException
	{
		// Perform general pre-processing of pricing problems, resetting duals on task
		// graphs.
		preProcessPricingProblems(instance);

		// Update generic duals.
		masterProblem.updateGenericDuals();
		
		if (numThreads > 1)
		{
			// Initialise a synchronized list of columns.
			List<Pair<U, Double>> generatedColumns = Collections.synchronizedList(new ArrayList<>());

			// Initialise a thread pool.
			ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);

			// Generate a list of pricing problems to populate thread pool.
			List<Future<?>> futures = new ArrayList<>();
			for (V pricingProblem : pricingProblems)
			{
				// Submit job.
				MultiThreadRunnable<T, U, V> runnable = new MultiThreadRunnable<>(generatedColumns, masterProblem,
						pricingProblem, this, pricingProblemSolver, reducedCostThreshold, false);
				Future<?> future = threadPool.submit(() -> runnable.run());
				futures.add(future);
			}

			// Proceed when jobs are done.
			for (Future<?> future : futures)
			{
				try
				{
					future.get();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
				catch (ExecutionException e)
				{
					e.printStackTrace();
				}
			}

			// Shut down threadpool and return columns.
			threadPool.shutdown();
			return generatedColumns;
		}
		else
		{
			// Initialise a list of columns.
			List<Pair<U, Double>> generatedColumns = new ArrayList<>();

			// Generate a list of pricing problems and solve them.
			for (V pricingProblem : pricingProblems)
			{
				// Reset duals.
				preProcessPricingProblem(instance, pricingProblem);

				// Update pricing problem-specific duals.
				masterProblem.updatePricingProblemDuals(pricingProblem);

				// Solve the pricing problem.
				generatedColumns.addAll(
						pricingProblemSolver.generateColumns(masterProblem, pricingProblem, reducedCostThreshold,
								false));
			}
			return generatedColumns;
		}
	}

	@Override
	public List<Pair<U, Double>> enumerateColumns(AbstractMasterProblem<T, U, V> masterProblem,
			AbstractPricingProblemSolver<T, U, V> pricingProblemSolver, T instance, double gap, int maxNumberColumns)
			throws IloException
	{
		// Perform general pre-processing of pricing problems, resetting duals on task
		// graphs.
		preProcessPricingProblems(instance);

		// Update generic duals.
		masterProblem.updateGenericDuals();

		if (numThreads > 1)
		{
			// Initialise a synchronized list of columns.
			List<Pair<U, Double>> generatedColumns = Collections.synchronizedList(new ArrayList<>());

			// Initialise a thread pool.
			ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);

			// Generate a list of pricing problems to populate thread pool.
			List<Future<?>> futures = new ArrayList<>();
			for (V pricingProblem : pricingProblems)
			{
				// Submit job.
				MultiThreadRunnable<T, U, V> runnable = new MultiThreadRunnable<>(generatedColumns, masterProblem,
						pricingProblem, this, pricingProblemSolver, gap, true);
				Future<?> future = threadPool.submit(() -> runnable.run());
				futures.add(future);
			}

			// Proceed when jobs are done.
			for (Future<?> future : futures)
			{
				try
				{
					future.get();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
				catch (ExecutionException e)
				{
					e.printStackTrace();
				}
			}

			// Shut down threadpool and return columns.
			threadPool.shutdown();
			return generatedColumns;
		}
		else
		{
			// Initialise a list of columns.
			List<Pair<U, Double>> generatedColumns = new ArrayList<>();

			// Generate a list of pricing problems and solve them.
			for (V pricingProblem : pricingProblems)
			{
				// Perform problem-specific dual updating.
				preProcessPricingProblem(instance, pricingProblem);

				// Solve the pricing problem.
				generatedColumns.addAll(
						pricingProblemSolver.generateColumns(masterProblem, pricingProblem, gap, true));
			}
			return generatedColumns;
		}
	}

	public List<V> getPricingProblems()
	{
		return pricingProblems;
	}

	/**
	 * Reset generic duals etc.
	 */
	protected abstract void preProcessPricingProblems(T instance);

	/**
	 * Reset pricing-problem specific duals.
	 */
	protected abstract void preProcessPricingProblem(T instance, V pricingProblem);

	protected abstract List<V> generatePricingProblems(T instance);
}
