package optimisation.BAP;

import java.util.Set;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;

public abstract class AbstractBranchingDecision<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	public abstract Set<AbstractConstraint<T, U, V>> getBranchingConstraints();

	public abstract boolean isCompatible(V pricingProblem);

	public abstract void modifyPricingProblem(V pricingProblem);
}
