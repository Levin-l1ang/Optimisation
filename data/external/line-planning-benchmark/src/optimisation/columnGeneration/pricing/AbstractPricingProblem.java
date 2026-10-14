package optimisation.columnGeneration.pricing;

import optimisation.columnGeneration.AbstractInstance;

public abstract class AbstractPricingProblem<T extends AbstractInstance>
{
	private final int index;
	private double fixedDual;
	
	public AbstractPricingProblem(int index)
	{
		this.index = index;
	}
	
	public void makeThreadSafe()
	{
		// Do nothing. This is an auxiliary method that can be used for multithreading purposes.
	}
	
	public int getIndex()
	{
		return index;
	}
	
	public double getFixedDual()
	{
		return fixedDual;
	}
	
	public void resetFixedDual()
	{
		fixedDual = 0;
	}
	
	public void setFixedDual(double dual)
	{
		fixedDual = dual;
	}
	
	public void addFixedDual(double dual)
	{
		fixedDual += dual;
	}
}
