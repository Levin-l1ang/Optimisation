package linePlanning.columnGeneration.constraints;

import linePlanning.columnGeneration.columns.LineActivationColumn;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import optimisation.columnGeneration.AbstractConstraint;

public class BudgetConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	public BudgetConstraint(double bound)
	{
		super(ConstraintType.LESSER, bound);
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		return (column instanceof LineFrequencyColumn) || (column instanceof LineActivationColumn);
	}

	@Override
	public double getCoefficient(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
			return lineColumn.getLine().getCost();
		}
		else
		{
			return LinePlanningParameters.LINE_ACTIVATION_COST;
		}
	}

	@Override
	public void updateGenericDuals(LinePlanningInstance instance, double dual)
	{
		// Do nothing.
	}

	@Override
	public void updatePricingProblemDuals(LinePlanningPricingProblem pricingProblem, double dual)
	{
		// Do nothing.
	}

	@Override
	public int hashCode()
	{
		return super.hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		return true;
	}
}
