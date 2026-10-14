package optimisation.columnGeneration.columnManagement;

import java.util.Comparator;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Pair;

public class ReducedCostComparator<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>> implements Comparator<Pair<U, Double>>
{
	@Override
	public int compare(Pair<U, Double> o1, Pair<U, Double> o2)
	{
		return Double.compare(o1.getValue(), o2.getValue());
	}

}
