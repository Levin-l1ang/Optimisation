package optimisation.BAP;

import java.util.LinkedHashSet;
import java.util.Set;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public class BranchingCandidate<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	private final Set<AbstractBranchingDecision<T, U, V>> branchingDecisions;
	private final double fractionalValue;

	public BranchingCandidate(double fractionalValue)
	{
		this.branchingDecisions = new LinkedHashSet<>();
		this.fractionalValue = fractionalValue;
	}

	public void addBranchingDecision(AbstractBranchingDecision<T, U, V> decision)
	{
		branchingDecisions.add(decision);
	}

	public Set<AbstractBranchingDecision<T, U, V>> getBranchingDecisions()
	{
		return branchingDecisions;
	}

	public double getFractionalValue()
	{
		return fractionalValue;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((branchingDecisions == null) ? 0 : branchingDecisions.hashCode());
		long temp;
		temp = Double.doubleToLongBits(fractionalValue);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BranchingCandidate other = (BranchingCandidate) obj;
		if (branchingDecisions == null)
		{
			if (other.branchingDecisions != null)
				return false;
		}
		else
			if (!branchingDecisions.equals(other.branchingDecisions))
				return false;
		if (Double.doubleToLongBits(fractionalValue) != Double.doubleToLongBits(other.fractionalValue))
			return false;
		return true;
	}
}
