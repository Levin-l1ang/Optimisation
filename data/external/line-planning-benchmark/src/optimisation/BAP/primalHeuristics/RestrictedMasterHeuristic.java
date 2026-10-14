package optimisation.BAP.primalHeuristics;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public class RestrictedMasterHeuristic<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		extends AbstractPrimalHeuristic<T, U, V>
{
	protected final int TIME_LIMIT_SECONDS;

	protected double lowerBound = 0;

	public RestrictedMasterHeuristic(int timeLimit)
	{
		this.TIME_LIMIT_SECONDS = timeLimit;
	}

	public AbstractSolution<T, U, V> applyHeuristic(AbstractMasterProblem<T, U, V> masterProblem,
			ColumnGeneration<T, U, V> columnGeneration, double initialLowerBound, long timeLimit) throws IloException
	{
		// Apply a restricted master problem once.
		int timeLimitSeconds = Math.min(TIME_LIMIT_SECONDS, (int) Math.floor(timeLimit / 1000));
		AbstractSolution<T, U, V> solution = masterProblem.applyRestrictedMasterHeuristic(timeLimitSeconds);

		// Update the lower bound.
		lowerBound = masterProblem.getLowerBoundMILP();
		if (solution != null)
		{
			lowerBound = Math.min(masterProblem.getLowerBoundMILP(), solution.getObjectiveValue());
		}

		// Return solution.
		return solution;
	}

	public double getLowerBound()
	{
		return lowerBound;
	}
}
