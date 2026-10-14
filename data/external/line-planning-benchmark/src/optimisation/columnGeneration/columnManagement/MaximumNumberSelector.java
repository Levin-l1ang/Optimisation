package optimisation.columnGeneration.columnManagement;

import java.util.ArrayList;
import java.util.List;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Pair;

public class MaximumNumberSelector<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		extends AbstractColumnSelector<T, U, V>
{
	private final int maximumNumberColumns;

	public MaximumNumberSelector(int maximumNumberColumns)
	{
		this.maximumNumberColumns = maximumNumberColumns;
	}

	@Override
	public List<Pair<U, Double>> selectColumns(List<Pair<U, Double>> generatedColumns)
	{
		List<Pair<U, Double>> columns = new ArrayList<>(generatedColumns);
		columns.sort(new ReducedCostComparator<>());
		columns = columns.subList(0, Math.min(columns.size(), maximumNumberColumns));
		return columns;
	}

}
