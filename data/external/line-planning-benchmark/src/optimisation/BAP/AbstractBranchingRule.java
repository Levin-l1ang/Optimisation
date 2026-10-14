package optimisation.BAP;

import java.util.List;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public abstract class AbstractBranchingRule<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	protected final double priority;

	public AbstractBranchingRule(double priority)
	{
		this.priority = priority;
	}
	
	public abstract List<BranchingCandidate<T, U, V>> getBranchingCandidates(BAPNode<T, U, V> parent) throws IloException;
}
