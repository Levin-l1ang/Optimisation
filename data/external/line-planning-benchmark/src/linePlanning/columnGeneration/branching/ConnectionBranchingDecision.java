package linePlanning.columnGeneration.branching;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import linePlanning.PTN.Connection;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import optimisation.BAP.AbstractBranchingDecision;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractConstraint.ConstraintType;

public class ConnectionBranchingDecision
		extends AbstractBranchingDecision<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Connection connection;
	private final int bound;
	private final boolean isLowerBound;

	public ConnectionBranchingDecision(Connection connection, int bound, boolean isLowerBound)
	{
		this.connection = connection;
		this.bound = bound;
		this.isLowerBound = isLowerBound;
	}

	@Override
	public Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> getBranchingConstraints()
	{
		Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> constraints = new LinkedHashSet<>();
		ConstraintType constraintType = isLowerBound ? ConstraintType.GREATER : ConstraintType.LESSER;
		constraints.add(new ConnectionBranchingConstraint(constraintType, bound, connection));
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
		return Objects.hash(bound, connection, isLowerBound);
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
		ConnectionBranchingDecision other = (ConnectionBranchingDecision) obj;
		return bound == other.bound && Objects.equals(connection, other.connection)
				&& isLowerBound == other.isLowerBound;
	}
}
