package optimisation.columnGeneration;

import ilog.concert.IloException;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Configuration;

public abstract class AbstractConstraint<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	public enum ConstraintType
	{
		EQUALITY, LESSER, GREATER
	}

	protected final ConstraintType constraintType;
	protected final double bound;

	private int numIterUnused = 0;

	public AbstractConstraint(ConstraintType constraintType, double bound)
	{
		this.constraintType = constraintType;
		this.bound = bound;
	}

	public double getBound()
	{
		return bound;
	}

	public ConstraintType getModelConstraintType()
	{
		return constraintType;
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

	public final void addSlackVariable(AbstractMasterProblem<T, U, V> masterProblem) throws IloException
	{
		double SLACK_COST = Configuration.getConfiguration().getDoubleProperty("SLACK_COST");
		if (constraintType.equals(ConstraintType.GREATER))
		{
			masterProblem.addSlackVariable(this, true, SLACK_COST, 0, bound);
		}
		if (constraintType.equals(ConstraintType.LESSER))
		{
			masterProblem.addSlackVariable(this, false, SLACK_COST, 0, bound);
		}
		if (constraintType.equals(ConstraintType.EQUALITY))
		{
			masterProblem.addSlackVariable(this, true, SLACK_COST, 0, bound);
			masterProblem.addSlackVariable(this, false, SLACK_COST, 0, bound);
		}
	}

	public abstract boolean containsColumn(U column);

	public abstract double getCoefficient(U column);

	public abstract void updateGenericDuals(T instance, double dual);

	public abstract void updatePricingProblemDuals(V pricingProblem, double dual);

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(bound);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((constraintType == null) ? 0 : constraintType.hashCode());
		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;

		AbstractConstraint<T, U, V> other = (AbstractConstraint<T, U, V>) obj;
		if (Double.doubleToLongBits(bound) != Double.doubleToLongBits(other.bound))
			return false;
		if (constraintType != other.constraintType)
			return false;
		return true;
	}
}
