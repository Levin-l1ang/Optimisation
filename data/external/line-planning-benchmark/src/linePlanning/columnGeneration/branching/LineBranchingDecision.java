package linePlanning.columnGeneration.branching;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.lineGeneration.Line;
import optimisation.BAP.AbstractBranchingDecision;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractConstraint.ConstraintType;

public class LineBranchingDecision
		extends AbstractBranchingDecision<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Line line;
	private final int bound;
	private final boolean isLowerBound;

	public LineBranchingDecision(Line line, int bound, boolean isLowerBound)
	{
		this.line = line;
		this.bound = bound;
		this.isLowerBound = isLowerBound;
	}

	@Override
	public Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> getBranchingConstraints()
	{
		Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> constraints = new LinkedHashSet<>();
		ConstraintType constraintType = isLowerBound ? ConstraintType.GREATER : ConstraintType.LESSER;
		constraints.add(new LineBranchingConstraint(constraintType, bound, line));
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
		return Objects.hash(bound, isLowerBound, line);
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
		LineBranchingDecision other = (LineBranchingDecision) obj;
		return bound == other.bound && isLowerBound == other.isLowerBound && Objects.equals(line, other.line);
	}
}
