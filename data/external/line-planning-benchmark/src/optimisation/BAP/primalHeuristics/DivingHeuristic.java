package optimisation.BAP.primalHeuristics;

import java.util.Map.Entry;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Configuration;

public class DivingHeuristic<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		extends AbstractPrimalHeuristic<T, U, V>
{
	public AbstractSolution<T, U, V> applyHeuristic(AbstractMasterProblem<T, U, V> masterProblem,
			ColumnGeneration<T, U, V> columnGeneration, double initialLowerBound, long timeLimit) throws IloException
	{
		long startTime = System.currentTimeMillis();
		AbstractSolution<T, U, V> bestSolution = null;
		columnGeneration.disableLogger();

		while (true)
		{
			// Terminate if time limit is exceeded.
			long elapsedTime = System.currentTimeMillis() - startTime;
			if (elapsedTime > timeLimit)
			{
				return bestSolution;
			}

			// Apply column generation.
			columnGeneration.applyColumnGeneration(masterProblem, masterProblem.getInstance(), Double.MAX_VALUE);
			AbstractSolution<T, U, V> solution = masterProblem.getSolution();

			// Fix the most fractional column to one.
			double highestValue = -1;
			double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
			U highestValueColumn = null;
			for (Entry<U, Double> pair : solution.getColumnMap().entrySet())
			{
				double value = pair.getValue();
				if (value > highestValue && value < 1 - PRECISION)
				{
					highestValue = value;
					highestValueColumn = pair.getKey();
				}
			}
			if (highestValue > -1)
			{
				masterProblem.fixColumn(highestValueColumn, 1);
			}
			else
			{
				// Unfix all columns.
				for (U column : solution.getColumnMap().keySet())
				{
					masterProblem.unfixColumn(column);
				}
				bestSolution = solution;
				System.out.println("Diving solution: " + solution.getObjectiveValue());
				break;
			}
		}
		columnGeneration.enableLogger();

		// Return best found solution.
		return bestSolution;
	}
}
