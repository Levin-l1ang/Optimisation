package optimisation.BAP.strongBranching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ilog.concert.IloException;
import optimisation.BAP.AbstractBranchingDecision;
import optimisation.BAP.BAPNode;
import optimisation.BAP.BranchingCandidate;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Configuration;
import util.Pair;

public class StrongBranching<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	private final static double MU = 1.0 / 6.0;
	private final static int NUMBER_CANDIDATES = Configuration.getConfiguration()
			.getIntProperty("NUM_STRONG_BRANCHING_CANDIDATES");
	private final static int NUMBER_STRONG_CANDIDATES = Configuration.getConfiguration()
			.getIntProperty("NUM_STRONG_BRANCHING_STRONG_CANDIDATES");
	private final static int NUMBER_ITERATIONS = Configuration.getConfiguration()
			.getIntProperty("NUM_STRONG_BRANCHING_ITERATIONS");

	public BranchingCandidate<T, U, V> determineBranchingCandidate(
			List<BranchingCandidate<T, U, V>> branchingCandidates, AbstractMasterProblem<T, U, V> masterProblem,
			ColumnGeneration<T, U, V> columnGeneration, double originalObjective) throws IloException
	{
		// Sort candidates.
		branchingCandidates.sort(new ValueComparator());
		branchingCandidates = branchingCandidates.subList(0, Math.min(branchingCandidates.size(), NUMBER_CANDIDATES));

		// Return the first candidate if it is the only one.
		if (branchingCandidates.size() == 1)
		{
			return branchingCandidates.get(0);
		}

		// Apply pseudo-strong branching on the candidates. Start by initialising a
		// synchronised map.
		Map<BranchingCandidate<T, U, V>, Double> minDeltaMap = Collections.synchronizedMap(new LinkedHashMap<>());
		Map<BranchingCandidate<T, U, V>, Double> maxDeltaMap = Collections.synchronizedMap(new LinkedHashMap<>());

		// Initialise a thread pool.
		int numThreads = util.Configuration.getConfiguration().getIntProperty("NUM_THREADS_STRONG_BRANCHING");
		ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);

		// Generate a list of models to solve.
		List<Future<?>> futures = new ArrayList<>();
		int index = 0;
		for (BranchingCandidate<T, U, V> candidate : branchingCandidates)
		{
			try
			{
				for (AbstractBranchingDecision<T, U, V> decision : candidate.getBranchingDecisions())
				{
					// Submit first job.
					String model = "model_" + index + ".lp";
					masterProblem.processBranchingDecision(decision);
					masterProblem.export(model);
					masterProblem.undoBranchingDecision(decision);

					MultiThreadBranchingSolver<BranchingCandidate<T, U, V>> runnable = new MultiThreadBranchingSolver<>(
							minDeltaMap, maxDeltaMap, candidate, model);
					Future<?> future = threadPool.submit(() -> runnable.run());
					futures.add(future);
					index++;
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		// Proceed when jobs are done.
		for (Future<?> future : futures)
		{
			try
			{
				future.get();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		// Shut down threadpool and return columns.
		threadPool.shutdown();

		// Gather the best candidates
		List<Pair<BranchingCandidate<T, U, V>, Double>> scorePairs = new ArrayList<>();
		for (BranchingCandidate<T, U, V> candidate : branchingCandidates)
		{
			if (minDeltaMap.containsKey(candidate) && maxDeltaMap.containsKey(candidate))
			{
				double deltaMin = minDeltaMap.get(candidate) - originalObjective;
				double deltaMax = maxDeltaMap.get(candidate) - originalObjective;
				double score = (1.0 - MU) * deltaMin + MU * deltaMax;
				scorePairs.add(new Pair<>(candidate, score));
			}
		}
		scorePairs.sort(new PairComparator());
		scorePairs = scorePairs.subList(0, Math.min(scorePairs.size(), NUMBER_STRONG_CANDIDATES));
		if (scorePairs.size() == 1)
		{
			return scorePairs.get(0).getKey();
		}

		// Assess the best candidates using heuristic column generation.
		double highestScore = -1;
		BranchingCandidate<T, U, V> bestCandidate = null;
		columnGeneration.setMaxNumberIterations(NUMBER_ITERATIONS);
		columnGeneration.disableLogger();
		for (Pair<BranchingCandidate<T, U, V>, Double> pair : scorePairs)
		{
			double deltaMin = Double.MAX_VALUE;
			double deltaMax = 0;
			for (AbstractBranchingDecision<T, U, V> decision : pair.getKey().getBranchingDecisions())
			{
				masterProblem.processBranchingDecision(decision);
				columnGeneration.applyColumnGeneration(masterProblem, masterProblem.getInstance(), Double.MAX_VALUE);
				double delta = masterProblem.getObjectiveValue() - originalObjective;
				if (delta < -0.01)
				{
					throw new IllegalArgumentException("NEGATIVE DELTA: " + delta);
				}
				deltaMin = Math.min(deltaMin, delta);
				deltaMax = Math.max(deltaMax, delta);
				masterProblem.undoBranchingDecision(decision);
			}
			double score = (1.0 - MU) * deltaMin + MU * deltaMax;
			if (score > highestScore)
			{
				bestCandidate = pair.getKey();
				highestScore = score;
			}
		}
		columnGeneration.setMaxNumberIterations(Integer.MAX_VALUE);
		columnGeneration.enableLogger();
		return bestCandidate;
	}

	public Set<BAPNode<T, U, V>> getChildren(BAPNode<T, U, V> parent, BranchingCandidate<T, U, V> candidate)
	{
		Set<BAPNode<T, U, V>> children = new LinkedHashSet<>();
		for (AbstractBranchingDecision<T, U, V> decision : candidate.getBranchingDecisions())
		{
			BAPNode<T, U, V> child = new BAPNode<>(parent);
			child.addBranchingDecision(decision);
			children.add(child);
		}
		return children;
	}

	private class ValueComparator implements Comparator<BranchingCandidate<T, U, V>>
	{
		@Override
		public int compare(BranchingCandidate<T, U, V> o1, BranchingCandidate<T, U, V> o2)
		{
			return Double.compare(o2.getFractionalValue(), o1.getFractionalValue());
		}
	}

	private class PairComparator implements Comparator<Pair<BranchingCandidate<T, U, V>, Double>>
	{
		@Override
		public int compare(Pair<BranchingCandidate<T, U, V>, Double> o1, Pair<BranchingCandidate<T, U, V>, Double> o2)
		{
			return Double.compare(o2.getValue(), o1.getValue());
		}
	}
}
