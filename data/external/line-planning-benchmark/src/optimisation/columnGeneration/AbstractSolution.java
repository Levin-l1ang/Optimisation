package optimisation.columnGeneration;

import java.util.Map;
import java.util.Map.Entry;

import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Configuration;

public class AbstractSolution<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	private final double objectiveValue;
	protected Map<U, Double> columnMap;

	public AbstractSolution(double objectiveValue, Map<U, Double> columnMap)
	{
		this.objectiveValue = objectiveValue;
		this.columnMap = columnMap;
	}
	
	public double getObjectiveValue()
	{
		return objectiveValue;
	}

	public Map<U, Double> getColumnMap()
	{
		return columnMap;
	}

	public boolean isInteger()
	{
		for (Double value : columnMap.values())
		{
			if (!isInteger(value))
			{
				return false;
			}
		}
		return true;
	}

	public void printSolution()
	{
		for (Entry<U, Double> entry : columnMap.entrySet())
		{
			System.out.println(entry.getValue() + " " + entry.getKey().toString());
		}
	}

	public static boolean isInteger(double value)
	{
		return Math.abs(value - Math.rint(value)) < Configuration.getConfiguration().getDoubleProperty("PRECISION");
	}
}
