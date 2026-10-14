package optimisation.BAP.enumeration;

import java.util.ArrayList;
import java.util.List;

import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Pair;

public class MultiThreadEnumerationRunnable<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		implements Runnable
{
	private final List<Pair<U, Double>> generatedColumns;
	private final List<U> potentialColumns;
	private final AbstractMasterProblem<T, U, V> masterProblem;
	private final double reducedCostThreshold;

	public MultiThreadEnumerationRunnable(List<Pair<U, Double>> generatedColumns, List<U> potentialColumns,
			AbstractMasterProblem<T, U, V> masterProblem, double reducedCostThreshold)
	{
		this.generatedColumns = generatedColumns;
		this.potentialColumns = potentialColumns;
		this.masterProblem = masterProblem;
		this.reducedCostThreshold = reducedCostThreshold;
	}

	@Override
	public void run()
	{
		// Initialise a list of columns.
		List<Pair<U, Double>> columns = new ArrayList<>();

		// Generate a list of pricing problems and solve them.
		for (U potentialColumn : potentialColumns)
		{
			double reducedCost = masterProblem.getReducedCost(potentialColumn, false);
			if (reducedCost < reducedCostThreshold)
			{
				potentialColumn.resetNumIterUnused();
				columns.add(new Pair<>(potentialColumn, reducedCost));
			}
		}

		// Solve the pricing problem.
		generatedColumns.addAll(columns);
	}
}
