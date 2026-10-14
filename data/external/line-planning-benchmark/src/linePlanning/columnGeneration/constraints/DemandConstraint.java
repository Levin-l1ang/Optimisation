package linePlanning.columnGeneration.constraints;

import java.util.Objects;

import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import optimisation.columnGeneration.AbstractConstraint;

public class DemandConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final ODPair ODPair;

	public DemandConstraint(ODPair ODPair)
	{
		super(ConstraintType.GREATER, ODPair.getVolume());

		this.ODPair = ODPair;
	}

	public ODPair getODPair()
	{
		return ODPair;
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
		if (column instanceof RouteColumn)
		{
			RouteColumn pathColumn = (RouteColumn) column;
			return pathColumn.getODPair().equals(ODPair);
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
		if (pricingProblem.getODPairs().contains(ODPair))
		{
			pricingProblem.setODPairDual(ODPair, dual);
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(ODPair);
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
		DemandConstraint other = (DemandConstraint) obj;
		return Objects.equals(ODPair, other.ODPair);
	}
}
