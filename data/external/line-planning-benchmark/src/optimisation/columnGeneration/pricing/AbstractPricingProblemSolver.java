package optimisation.columnGeneration.pricing;

import java.util.List;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import util.Pair;

public abstract class AbstractPricingProblemSolver<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	public abstract List<Pair<U, Double>> generateColumns(AbstractMasterProblem<T, U, V> masterProblem,
			V pricingProblem, double reducedCostThreshold, boolean enumerateColumns);
}