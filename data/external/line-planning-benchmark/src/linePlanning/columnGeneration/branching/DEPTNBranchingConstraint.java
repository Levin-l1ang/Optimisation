package linePlanning.columnGeneration.branching;

import java.util.Objects;

import linePlanning.DPTN.DPTNArc;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import optimisation.columnGeneration.AbstractConstraint;

public class DEPTNBranchingConstraint
		extends AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final DPTNArc arc;

	public DEPTNBranchingConstraint(ConstraintType constraintType, double bound, DPTNArc arc)
	{
		super(constraintType, bound);

		this.arc = arc;
	}

	@Override
	public boolean containsColumn(LinePlanningColumn column)
	{
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
		result = prime * result + Objects.hash(arc);
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
		DEPTNBranchingConstraint other = (DEPTNBranchingConstraint) obj;
		return Objects.equals(arc, other.arc);
	}
}
