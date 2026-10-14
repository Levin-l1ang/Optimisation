package optimisation.BAP.primalHeuristics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractColumn;
import optimisation.columnGeneration.AbstractInstance;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Configuration;
import util.Pair;

public class DivingBacktracking<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
		extends AbstractPrimalHeuristic<T, U, V>
{
	private final static int MAX_DEPTH = 2;
	private final static int MAX_DISCREPANCY = 2;

	private AbstractSolution<T, U, V> bestSolution;
	private long startTime;
	private long timeLimit;

	public AbstractSolution<T, U, V> applyHeuristic(AbstractMasterProblem<T, U, V> masterProblem,
			ColumnGeneration<T, U, V> columnGeneration, double initialLowerBound, long timeLimit) throws IloException
	{
		bestSolution = null;
		this.startTime = System.currentTimeMillis();
		this.timeLimit = timeLimit;
		columnGeneration.disableLogger();
		applyDiving(masterProblem, new LinkedHashSet<>(), new LinkedHashSet<>(), 1, columnGeneration);
		columnGeneration.enableLogger();
		return bestSolution;
	}

	private void applyDiving(AbstractMasterProblem<T, U, V> masterProblem, Set<U> fixedColumns, Set<U> tabuColumns,
			int depth, ColumnGeneration<T, U, V> columnGeneration) throws IloException
	{
		// Step 0. Break if time limit is exceeded.
		long elapsedTime = System.currentTimeMillis() - startTime;
		if (elapsedTime >= timeLimit)
		{
			return;
		}

		// Step 1 + 2. Update RMP.
		for (U column : fixedColumns)
		{
			masterProblem.addColumn(column);
			masterProblem.fixColumn(column, 1);
		}

		// Step 3. Solve RMP. Unfix columns before proceeding.
		columnGeneration.applyColumnGeneration(masterProblem, masterProblem.getInstance(), Double.MAX_VALUE);
		AbstractSolution<T, U, V> solution = masterProblem.getSolution();
		boolean feasible = masterProblem.isFeasible();
		for (U column : solution.getColumnMap().keySet())
		{
			masterProblem.unfixColumn(column);
		}
		if (!feasible)
		{
			return;
		}
		if (bestSolution != null && solution.getObjectiveValue() > bestSolution.getObjectiveValue())
		{
			return;
		}

		// Step 4. Retrieve fractional columns.
		Set<U> fixedColumnsCopy = new LinkedHashSet<>(fixedColumns);
		List<Pair<U, Double>> fractionalColumns = new ArrayList<>();
		double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
		boolean integral = true;
		for (Entry<U, Double> pair : solution.getColumnMap().entrySet())
		{
			double value = pair.getValue();
			if (value > PRECISION && value < 1 - PRECISION)
			{
				integral = false;
				if (!tabuColumns.contains(pair.getKey()))
				{
					fractionalColumns.add(new Pair<>(pair.getKey(), value));
				}
			}
			if (value > 1 - PRECISION)
			{
				fixedColumnsCopy.add(pair.getKey());
			}
		}
		fractionalColumns.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

		// If no columns are fractional, we are done.
		if (integral)
		{
			// Unfix all columns.
			for (U column : solution.getColumnMap().keySet())
			{
				masterProblem.unfixColumn(column);
			}
			if (bestSolution == null || bestSolution.getObjectiveValue() > solution.getObjectiveValue())
			{
				System.out.println("Updated incumbent solution. Objective: " + solution.getObjectiveValue());
				bestSolution = solution;
			}
			return;
		}

		// Step 5. Fix a column and call method again.
		Set<U> newTabuColumns = new LinkedHashSet<>(tabuColumns);
		for (int i = 0; i < fractionalColumns.size(); i++)
		{
			U column = fractionalColumns.get(i).getKey();
			Set<U> newlyFixedColumns = new LinkedHashSet<>(fixedColumnsCopy);
			newlyFixedColumns.add(column);
			applyDiving(masterProblem, newlyFixedColumns, newTabuColumns, depth + 1, columnGeneration);
			newTabuColumns.add(column);
			if (newTabuColumns.size() > MAX_DISCREPANCY || depth > MAX_DEPTH)
			{
				return;
			}
		}
	}
}
