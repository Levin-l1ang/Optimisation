package optimisation.columnGeneration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public class DualVariables<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	private final Map<AbstractConstraint<T, U, V>, Double> dualMap;

	public DualVariables()
	{
		this.dualMap = new LinkedHashMap<>();
	}

	public void set(AbstractConstraint<T, U, V> constraint, double dual)
	{
		dualMap.put(constraint, dual);
	}

	public double get(AbstractConstraint<T, U, V> constraint)
	{
		return dualMap.get(constraint);
	}

	public boolean contains(AbstractConstraint<T, U, V> constraint)
	{
		return dualMap.containsKey(constraint);
	}

	public void remove(AbstractConstraint<T, U, V> constraint)
	{
		dualMap.remove(constraint);
	}
	
	public Set<AbstractConstraint<T, U, V>> getConstraints()
	{
		return dualMap.keySet();
	}

	public double getNorm()
	{
		double norm = 0;
		for (Double value : dualMap.values())
		{
			norm += Math.pow(value, 2.0);
		}
		norm = Math.sqrt(norm);
		return norm;
	}

	public DualVariables<T, U, V> sum(DualVariables<T, U, V> other)
	{
		DualVariables<T, U, V> sum = new DualVariables<>();
		for (Entry<AbstractConstraint<T, U, V>, Double> entry : dualMap.entrySet())
		{
			sum.set(entry.getKey(), entry.getValue() + other.get(entry.getKey()));
		}
		return sum;
	}

	public DualVariables<T, U, V> multiply(double coefficient)
	{
		DualVariables<T, U, V> result = new DualVariables<>();
		for (Entry<AbstractConstraint<T, U, V>, Double> entry : dualMap.entrySet())
		{
			result.set(entry.getKey(), entry.getValue() * coefficient);
		}
		return result;
	}

	public DualVariables<T, U, V> getCopy()
	{
		DualVariables<T, U, V> copy = new DualVariables<>();
		for (Entry<AbstractConstraint<T, U, V>, Double> entry : dualMap.entrySet())
		{
			copy.set(entry.getKey(), entry.getValue());
		}
		return copy;
	}
}
