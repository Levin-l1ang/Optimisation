package optimisation.BAP;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblemSolver;
import optimisation.columnGeneration.pricing.AbstractPricingRoutine;

public abstract class AbstractReducedCostFixing<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	public abstract AbstractReducedCostFixingDecision<T, U, V> applyReducedCostFixing(
			AbstractMasterProblem<T, U, V> masterProblem, AbstractPricingRoutine<T, U, V> pricingRoutine,
			AbstractPricingProblemSolver<T, U, V> pricingProblemSolver, T instance, double gap);
}
