package linePlanning.columnGeneration.constraints;

import java.util.Objects;

import linePlanning.PTN.Connection;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;

public class FrequencyConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Connection connection;

	public FrequencyConstraint(Connection c)
	{
		super(ConstraintType.LESSER, c.getMaxFreq());

		this.connection = c;
	}

	public Connection getConnection()
	{
		return connection;
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lCol = (LineFrequencyColumn) column;
			Line line = lCol.getLine();
			if (line.getConnectionPathForward().contains(connection))
			{
				return true;
			}
			if (line.getConnectionPathBackward().contains(connection))
			{
				return true;
			}
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
		FrequencyConstraint other = (FrequencyConstraint) obj;
		return Objects.equals(connection, other.connection);
	}
}
