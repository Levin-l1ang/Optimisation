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

public class LineActivationBranchingDecision
		extends AbstractBranchingDecision<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Line line;
	private final boolean isAllowed;

	public LineActivationBranchingDecision(Line line, boolean isAllowed)
	{
		this.line = line;
		this.isAllowed = isAllowed;
	}

	@Override
	public Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> getBranchingConstraints()
	{
		Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> constraints = new LinkedHashSet<>();
		double bound = isAllowed ? 1 : 0;
		constraints.add(new LineActivationBranchingConstraint(line, bound));
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
		return Objects.hash(isAllowed, line);
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
		LineActivationBranchingDecision other = (LineActivationBranchingDecision) obj;
		return isAllowed == other.isAllowed && Objects.equals(line, other.line);
	}
}
