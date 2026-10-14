package linePlanning.columnGeneration.columns;

import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import optimisation.columnGeneration.AbstractColumn;

public abstract class LinePlanningColumn extends AbstractColumn<LinePlanningInstance, LinePlanningPricingProblem>
{
	public LinePlanningColumn(double coefficient, boolean isRemovableColumn, boolean isIntegerValued)
	{
		super(coefficient, isRemovableColumn, isIntegerValued);
	}
}
