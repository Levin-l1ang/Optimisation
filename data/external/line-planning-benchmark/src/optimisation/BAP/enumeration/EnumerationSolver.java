package optimisation.BAP.enumeration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblemSolver;
import util.Pair;

public class EnumerationSolver<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		implements AbstractEnumerationRoutine<T, U, V>
{
	private final int numThreads;
	private final int bucketSize = 2500;

	private final List<U> potentialColumns;

	public EnumerationSolver(List<U> potentialColumns)
	{
		this.numThreads = util.Configuration.getConfiguration().getIntProperty("NUM_THREADS");
		this.potentialColumns = potentialColumns;
	}

	@Override
	public List<Pair<U, Double>> enumerateColumns(AbstractMasterProblem<T, U, V> masterProblem,
			AbstractPricingProblemSolver<T, U, V> pricingProblemSolver, T instance, double gap, int maximumNumberColumns)
	{
		if (numThreads > 1)
		{
			// Initialise a synchronized list of columns.
			List<Pair<U, Double>> generatedColumns = Collections.synchronizedList(new ArrayList<>());

			// Initialise a thread pool.
			ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);

			// Generate a list of pricing problems to populate thread pool.
			List<Future<?>> futures = new ArrayList<>();
			int startIndex = 0;
			while (startIndex < potentialColumns.size())
			{
				// Submit job.
				List<U> columns = potentialColumns.subList(startIndex,
						Math.min(potentialColumns.size(), startIndex + bucketSize));
				MultiThreadEnumerationRunnable<T, U, V> runnable = new MultiThreadEnumerationRunnable<>(
						generatedColumns, columns, masterProblem, gap);
				Future<?> future = threadPool.submit(() -> runnable.run());
				futures.add(future);

				// Increment index.
				startIndex += bucketSize;
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
			for (U potentialColumn : potentialColumns)
			{
				double reducedCost = masterProblem.getReducedCost(potentialColumn, false);
				if (reducedCost < gap)
				{
					potentialColumn.resetNumIterUnused();
					generatedColumns.add(new Pair<>(potentialColumn, reducedCost));
				}
			}
			return generatedColumns;
		}
	}
}
