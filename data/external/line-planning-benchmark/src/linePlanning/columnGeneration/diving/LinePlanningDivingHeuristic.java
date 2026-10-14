package linePlanning.columnGeneration.diving;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ilog.concert.IloException;
import linePlanning.columnGeneration.columns.LineActivationColumn;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.LinePlanningParameters;
import linePlanning.lineGeneration.Line;
import optimisation.BAP.primalHeuristics.AbstractPrimalHeuristic;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import util.Configuration;
import util.Pair;

public class LinePlanningDivingHeuristic
		extends AbstractPrimalHeuristic<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final List<Line> linePool;
	private final double budget;

	public LinePlanningDivingHeuristic(List<Line> linePool, double budget)
	{
		this.linePool = linePool;
		this.budget = budget;
	}

	public AbstractSolution<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> applyHeuristic(
			AbstractMasterProblem<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> masterProblem,
			ColumnGeneration<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> columnGeneration,
			double initialLowerBound, long timeLimit) throws IloException
	{
		// Disable logger, initialise solution.
		long startTime = System.currentTimeMillis();
		columnGeneration.disableLogger();
		AbstractSolution<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> integralSolution = null;
		Map<Line, Integer> fixedLines = new LinkedHashMap<>();

		// Keep track of used budget.
		double usedBudget = 0;

		// Initialise solution
		while (true)
		{
			// Terminate if time limit is exceeded.
			long elapsedTime = System.currentTimeMillis() - startTime;
			if (elapsedTime > timeLimit)
			{
				break;
			}

			// Solve RMP with column generation.
			columnGeneration.applyColumnGeneration(masterProblem, masterProblem.getInstance(), Double.MAX_VALUE);
			if (!masterProblem.isFeasible())
			{
				System.out.println("Terminating diving due to infeasibility.");
				break;
			}

			// Retrieve fractional columns.
			AbstractSolution<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> currentSolution = masterProblem
					.getSolution();
			Map<LinePlanningColumn, Double> columnMap = currentSolution.getColumnMap();
			List<Pair<Line, Double>> fractionalLines = new ArrayList<>();
			double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
			boolean integral = true;
			for (Line line : linePool)
			{
				// Skip lines that have already been fixed.
				if (fixedLines.containsKey(line))
				{
					continue;
				}

				// Check if the line frequency column is fractional.
				LineFrequencyColumn frequencyColumn = new LineFrequencyColumn(line);
				double value = columnMap.getOrDefault(frequencyColumn, 0.0);
				double fractionalValue = Math.abs(value - Math.floor(value));
				if (fractionalValue > PRECISION)
				{
					integral = false;
					fractionalLines.add(new Pair<>(line, value));
					continue;
				}

				// Check if the line activation column is fractional.
				LineActivationColumn activationColumn = new LineActivationColumn(line);
				double activationValue = columnMap.getOrDefault(activationColumn, 0.0);
				fractionalValue = Math.abs(activationValue - Math.floor(activationValue));
				if (fractionalValue > PRECISION)
				{
					integral = false;
					fractionalLines.add(new Pair<>(line, value));
					continue;
				}
			}
			fractionalLines.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

			// If all lines are integer-valued, we are done.
			if (integral)
			{
				// Store solution.
				integralSolution = currentSolution;
				break;
			}

			// If budget allows, fix a column upwards.
			boolean fixed = false;
			for (int i = 0; i < fractionalLines.size(); i++)
			{
				Line line = fractionalLines.get(i).getKey();
				double value = fractionalLines.get(i).getValue();
				int roundedValue = Math.max(1, (int) Math.ceil(value));
				double cost = LinePlanningParameters.LINE_ACTIVATION_COST + roundedValue * line.getCost();
				if (usedBudget + cost <= budget)
				{
					fixLine(masterProblem, line, roundedValue);
					fixedLines.put(line, roundedValue);
					usedBudget += cost;
					System.out.println(
							"Fixing upwards: " + roundedValue + ". Used budget: " + usedBudget + " out of " + budget);
					fixed = true;
					break;
				}
			}

			// Otherwise, attempt to fix a column downwards.
			if (!fixed)
			{
				for (int i = 0; i < fractionalLines.size(); i++)
				{
					Line line = fractionalLines.get(i).getKey();
					double value = fractionalLines.get(i).getValue();
					int roundedValue = (int) Math.floor(value);
					double cost = roundedValue * line.getCost();
					if (roundedValue > 0)
					{
						cost += LinePlanningParameters.LINE_ACTIVATION_COST;
					}
					if (usedBudget + cost <= budget)
					{
						fixLine(masterProblem, line, roundedValue);
						fixedLines.put(line, roundedValue);
						usedBudget += cost;
						System.out.println("Fixing downwards: " + roundedValue + ". Used budget: " + usedBudget
								+ " out of " + budget);
						fixed = true;
						break;
					}
				}
			}

			// Do a pass to fix all unfixed lines to zero that are incompatible with the
			// budget anyway.
			for (Line line : linePool)
			{
				if (fixedLines.containsKey(line))
				{
					continue;
				}
				double cost = LinePlanningParameters.LINE_ACTIVATION_COST + line.getCost();
				if (usedBudget + cost > budget)
				{
					fixLine(masterProblem, line, 0);
					fixedLines.put(line, 0);
					System.out.println("Fixing to zero a line with cost " + cost + ". Used budget: " + usedBudget
							+ " out of " + budget);
				}
			}
		}

		// Unfix all line variables.
		for (Line line : fixedLines.keySet())
		{
			unfixLine(masterProblem, line);
		}

		// Return solution, if any.
		columnGeneration.enableLogger();
		return integralSolution;
	}

	private void unfixLine(
			AbstractMasterProblem<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> masterProblem,
			Line line) throws IloException
	{
		LineFrequencyColumn frequencyColumn = new LineFrequencyColumn(line);
		masterProblem.unfixColumn(frequencyColumn);
		if (LinePlanningParameters.LINE_ACTIVATION_COST > 0)
		{
			masterProblem.unfixColumn(new LineActivationColumn(line));
		}
	}

	private void fixLine(
			AbstractMasterProblem<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> masterProblem,
			Line line, double value) throws IloException
	{
		LineFrequencyColumn frequencyColumn = new LineFrequencyColumn(line);
		masterProblem.fixColumn(frequencyColumn, value);
		if (LinePlanningParameters.LINE_ACTIVATION_COST > 0)
		{
			if (value > 0)
			{
				masterProblem.fixColumn(new LineActivationColumn(line), 1);
			}
			else
			{
				masterProblem.fixColumn(new LineActivationColumn(line), 0);
			}
		}
	}
}
