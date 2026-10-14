package optimisation.columnGeneration.columnManagement;

import java.util.List;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Pair;

public abstract class AbstractColumnSelector<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	public abstract List<Pair<U, Double>> selectColumns(List<Pair<U, Double>> generatedColumns);
}
