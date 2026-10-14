package optimisation.columnGeneration;

import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public abstract class AbstractColumn<T extends AbstractInstance, V extends AbstractPricingProblem<T>>
{
	private final double coefficient;
	private final boolean canBeRemoved;
	private final boolean isIntegerValued;
	
	private int numIterUnused = 0;

	public AbstractColumn(double coefficient, boolean canBeRemoved, boolean isIntegerValued)
	{
		this.coefficient = coefficient;
		this.canBeRemoved = canBeRemoved;
		this.isIntegerValued = isIntegerValued;
	}
	
	public double getCoefficient()
	{
		return coefficient;
	}

	public boolean canBeRemoved()
	{
		return canBeRemoved;
	}
	
	public boolean isIntegerValued()
	{
		return isIntegerValued;
	}

	public int getNumIterUnused()
	{
		return numIterUnused;
	}

	public void increaseNumIterUnused()
	{
		numIterUnused++;
	}

	public void resetNumIterUnused()
	{
		numIterUnused = 0;
	}

	public abstract boolean equals(Object o);

	public abstract int hashCode();

	public abstract String toString();
}
