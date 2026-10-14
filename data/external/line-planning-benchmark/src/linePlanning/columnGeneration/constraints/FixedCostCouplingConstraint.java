package linePlanning.columnGeneration.constraints;

import java.util.Objects;

import linePlanning.columnGeneration.columns.LineActivationColumn;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;

public class FixedCostCouplingConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Line line;

	public FixedCostCouplingConstraint(Line line)
	{
		super(ConstraintType.LESSER, 0);

		this.line = line;
	}

	public Line getLine()
	{
		return line;
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
			return lineColumn.getLine().equals(line);
		}
		if (column instanceof LineActivationColumn)
		{
			LineActivationColumn lineActivationColumn = (LineActivationColumn) column;
			return lineActivationColumn.getLine().equals(line);
		}
		return false;
	}

	@Override
	public double getCoefficient(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			return 1;
		}
		return -1 * line.getMaxFrequency();
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
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(line);
		return result;
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
		FixedCostCouplingConstraint other = (FixedCostCouplingConstraint) obj;
		return Objects.equals(line, other.line);
	}
}
