package linePlanning.columnGeneration.branching;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import linePlanning.DPTN.DPTNArc;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import optimisation.BAP.AbstractBranchingDecision;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractConstraint.ConstraintType;

public class DEPTNBranchingDecision
		extends AbstractBranchingDecision<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final DPTNArc arc;
	private final int bound;
	private final boolean isLowerBound;

	public DEPTNBranchingDecision(DPTNArc arc, int bound, boolean isLowerBound)
	{
		this.arc = arc;
		this.bound = bound;
		this.isLowerBound = isLowerBound;
	}

	@Override
	public Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> getBranchingConstraints()
	{
		Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> constraints = new LinkedHashSet<>();
		ConstraintType constraintType = isLowerBound ? ConstraintType.GREATER : ConstraintType.LESSER;
		constraints.add(new DEPTNBranchingConstraint(constraintType, bound, arc));
		return constraints;
	}

	@Override
	public boolean isCompatible(LinePlanningPricingProblem pricingProblem)
	{
		return true;
	}

	@Override
	public void modifyPricingProblem(LinePlanningPricingProblem pricingProblem)
	{
		// Do nothing.
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(arc, bound, isLowerBound);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DEPTNBranchingDecision other = (DEPTNBranchingDecision) obj;
		return Objects.equals(arc, other.arc) && bound == other.bound && isLowerBound == other.isLowerBound;
	}
}
