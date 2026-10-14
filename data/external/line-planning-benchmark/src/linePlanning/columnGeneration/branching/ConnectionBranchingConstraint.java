package linePlanning.columnGeneration.branching;

import java.util.Objects;

import linePlanning.PTN.Connection;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;

public class ConnectionBranchingConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Connection connection;

	public ConnectionBranchingConstraint(ConstraintType constraintType, double bound, Connection connection)
	{
		super(constraintType, bound);

		this.connection = connection;
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
			Line line = lineColumn.getLine();
			return line.getConnectionPathForward().contains(connection)
					|| line.getConnectionPathBackward().contains(connection);
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
		result = prime * result + Objects.hash(connection);
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
		ConnectionBranchingConstraint other = (ConnectionBranchingConstraint) obj;
		return Objects.equals(connection, other.connection);
	}
}
