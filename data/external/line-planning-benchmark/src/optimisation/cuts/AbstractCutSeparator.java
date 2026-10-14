package optimisation.cuts;

import java.util.Set;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public abstract class AbstractCutSeparator<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	protected final double violationThreshold;

	private final boolean separateInfeasible;
	private final boolean separateSuboptimal;

	private final int maxIterationsRoot;
	private final int maxIterationsNonRoot;

	public int maxIterations;

	public AbstractCutSeparator(double violationThreshold)
	{
		this.violationThreshold = violationThreshold;

		this.separateInfeasible = true;
		this.separateSuboptimal = true;

		this.maxIterationsRoot = Integer.MAX_VALUE;
		this.maxIterationsNonRoot = Integer.MAX_VALUE;
	}

	public AbstractCutSeparator(double violationThreshold, boolean separateInfeasible, boolean separateSuboptimal)
	{
		this.violationThreshold = violationThreshold;

		this.separateInfeasible = separateInfeasible;
		this.separateSuboptimal = separateSuboptimal;

		this.maxIterationsRoot = Integer.MAX_VALUE;
		this.maxIterationsNonRoot = Integer.MAX_VALUE;
	}

	public AbstractCutSeparator(double violationThreshold, int maxIterationsRoot, int maxIterationsNonRoot,
			boolean separateInfeasible, boolean separateSuboptimal)
	{
		this.violationThreshold = violationThreshold;

		this.separateInfeasible = separateInfeasible;
		this.separateSuboptimal = separateSuboptimal;

		this.maxIterationsRoot = maxIterationsRoot;
		this.maxIterationsNonRoot = maxIterationsNonRoot;
	}

	public void setMaxNumberIterations(boolean isRootNode)
	{
		this.maxIterations = isRootNode ? maxIterationsRoot : maxIterationsNonRoot;
	}

	public boolean separateInIteration(int iteration, boolean isFeasible, boolean isOptimal)
	{
		return (isFeasible || separateInfeasible) && (isOptimal || separateSuboptimal) && (iteration < maxIterations);
	}

	public abstract boolean separate(boolean isRootNode);

	public abstract Set<AbstractConstraint<T, U, V>> generateCuts(AbstractSolution<T, U, V> solution)
			throws IloException;
}
