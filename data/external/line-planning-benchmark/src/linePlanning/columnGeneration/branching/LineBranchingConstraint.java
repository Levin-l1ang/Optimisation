package linePlanning.columnGeneration.branching;

import java.util.Objects;

import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;

public class LineBranchingConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Line line;

	public LineBranchingConstraint(ConstraintType constraintType, double bound, Line line)
	{
		super(constraintType, bound);

		this.line = line;
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
			return lineColumn.getLine().equals(line);
		}
		return false;
	}

	@Override
	public double getCoefficient(LinePlanningColumn column)
	{
		return 1;
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
		LineBranchingConstraint other = (LineBranchingConstraint) obj;
		return Objects.equals(line, other.line);
	}
}
