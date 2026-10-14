package linePlanning.PTN;

import java.util.List;
import java.util.Objects;

import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
import linePlanning.columnGeneration.columns.RouteSlackColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;

public class LightBasicDirectRouteConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final List<Line> lineSet;
	private final Connection connection;

	public LightBasicDirectRouteConstraint(List<Line> lines, Connection connection)
	{
		super(ConstraintType.LESSER, 0);

		this.lineSet = lines;
		this.connection = connection;
	}

	public Connection getConnection()
	{
		return connection;
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		if (column instanceof RouteSlackColumn)
		{
			return false;
		}
		if (column instanceof RouteColumn)
		{
			RouteColumn pCol = (RouteColumn) column;
			PTNRoute routeOther = (PTNRoute) pCol.getRoute();
			if (!routeOther.isDirect() || !routeOther.getArcs().contains(connection))
			{
				return false;
			}

			// Check if supporting line set is a subset of supportingLines.
			for (Line supportingOther : routeOther.getSupportingLines())
			{
				if (!lineSet.contains(supportingOther))
				{
					return false;
				}
			}
			return true;
		}
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lCol = (LineFrequencyColumn) column;
			if (lineSet.contains(lCol.getLine()))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public double getCoefficient(LinePlanningColumn column)
	{
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
			return -lineColumn.getLine().getCapacity();
		}
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
		result = prime * result + Objects.hash(connection, lineSet);
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
		LightBasicDirectRouteConstraint other = (LightBasicDirectRouteConstraint) obj;
		return Objects.equals(connection, other.connection) && Objects.equals(lineSet, other.lineSet);
	}
}
