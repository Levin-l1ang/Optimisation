package optimisation.columnGeneration.columnManagement;

import java.util.List;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Pair;

public class AllColumnSelector<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		extends AbstractColumnSelector<T, U, V>
{
	@Override
	public List<Pair<U, Double>> selectColumns(List<Pair<U, Double>> generatedColumns)
	{
		return generatedColumns;
	}
}
