package optimisation.columnGeneration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ilog.concert.IloException;
import optimisation.BAP.enumeration.EnumerationSolver;
import optimisation.columnGeneration.columnManagement.AbstractColumnSelector;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblemSolver;
import optimisation.columnGeneration.pricing.AbstractPricingRoutine;
import optimisation.cuts.AbstractCutSeparator;
import util.Configuration;
import util.Logger;
import util.Logger.CountQuantity;
import util.Logger.TimeQuantity;
import util.Logger.ValueQuantity;
import util.Pair;
import util.Writer;

public class ColumnGeneration<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	private boolean debug = true;
	private boolean debugReducedCost = false;

	private boolean useLogger = true;
	private final double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");

	private final int CUT_REMOVAL_FREQUENCY = Configuration.getConfiguration().getIntProperty("CUT_REMOVAL_FREQUENCY");
	private final int COLUMN_REMOVAL_FREQUENCY = Configuration.getConfiguration()
			.getIntProperty("COLUMN_REMOVAL_FREQUENCY");

	private final AbstractPricingRoutine<T, U, V> pricingRoutine;
	private final AbstractPricingProblemSolver<T, U, V> heuristicPricingProblemSolver;
	private final AbstractPricingProblemSolver<T, U, V> exactPricingProblemSolver;
	private final AbstractColumnSelector<T, U, V> columnSelector;

	private List<AbstractCutSeparator<T, U, V>> cutSeparators;

	private List<U> potentialColumns;

	private int maxNumberIterations = Integer.MAX_VALUE;

	public ColumnGeneration(AbstractPricingRoutine<T, U, V> pricingRoutine,
			AbstractPricingProblemSolver<T, U, V> heuristicPricingProblemSolver,
			AbstractPricingProblemSolver<T, U, V> exactPricingProblemSolver,
			AbstractColumnSelector<T, U, V> columnSelector)
	{
		this.pricingRoutine = pricingRoutine;
		this.heuristicPricingProblemSolver = heuristicPricingProblemSolver;
		this.exactPricingProblemSolver = exactPricingProblemSolver;
		this.columnSelector = columnSelector;

		this.cutSeparators = new ArrayList<>();
	}

	public void setMaxNumberIterations(int maxNumber)
	{
		this.maxNumberIterations = maxNumber;
	}

	public void enableLogger()
	{
		useLogger = true;
	}

	public void disableLogger()
	{
		useLogger = false;
	}

	public void removeCutSeparators()
	{
		this.cutSeparators = new ArrayList<>();
	}

	public void setCutSeparators(List<AbstractCutSeparator<T, U, V>> cutSeparators)
	{
		this.cutSeparators = cutSeparators;
	}

	public void setPotentialColumns(List<U> potentialColumns)
	{
		this.potentialColumns = potentialColumns;
	}

	public void removePotentialColumns()
	{
		this.potentialColumns = null;
	}

	public void setDebug(boolean debug)
	{
		this.debug = debug;
	}

	public AbstractPricingProblemSolver<T, U, V> getPricingProblemSolver(boolean exact)
	{
		if (exact)
		{
			return exactPricingProblemSolver;
		}
		else
		{
			return heuristicPricingProblemSolver;
		}
	}

	public AbstractPricingRoutine<T, U, V> getPricingRoutine()
	{
		return pricingRoutine;
	}

	public void applyColumnGeneration(AbstractMasterProblem<T, U, V> masterProblem, T instance, double lowerBound)
			throws IloException
	{
		// Set pricing settings and store past objective values.
		PricingSettings.EXACT_PRICING = PricingSettings.START_EXACT_PRICING;
		double previousObjective = Double.MAX_VALUE;
		Logger logger = Logger.getLogger();
		if (!useLogger)
		{
			logger = Logger.getDummyLogger();
		}

		// Generate the pricing problems once.
		pricingRoutine.constructPricingProblems(masterProblem, instance);

		// Dual smoothing set-up.
		final boolean lagrangianSmoothing = Configuration.getConfiguration()
				.getBooleanProperty("USE_LAGRANGIAN_SMOOTHING");
		final boolean autoTuneAlpha = Configuration.getConfiguration().getBooleanProperty("AUTO_TUNE_ALPHA");
		double lagrangianBound = Double.NEGATIVE_INFINITY;

		int iteration = 0;
		while (true)
		{
			// Solve LP relaxation.
			long timeRMP = System.currentTimeMillis();
			logger.startTimer(TimeQuantity.TIME_RMP);
			masterProblem.solve();
			logger.stopTimer(TimeQuantity.TIME_RMP);
			timeRMP = System.currentTimeMillis() - timeRMP;

			// Terminate if number of iterations exceeds maximum.
			if (iteration >= maxNumberIterations)
			{
				break;
			}

			// Retrieve objective value and solution.
			double objectiveValue = masterProblem.getObjectiveValue();
			AbstractSolution<T, U, V> solution = masterProblem.getSolution();
			logger.setValue(ValueQuantity.VALUE_OBJECTIVE, objectiveValue);

			// Update dual values.
			masterProblem.updateDuals();

			// Apply column management.
			logger.startTimer(TimeQuantity.TIME_COL_MANAGEMENT);
			masterProblem.updateInactiveColumns();
			logger.stopTimer(TimeQuantity.TIME_COL_MANAGEMENT);

			// Apply cut management.
			logger.startTimer(TimeQuantity.TIME_CUT_MANAGEMENT);
			masterProblem.updateInactiveCuts();
			logger.stopTimer(TimeQuantity.TIME_CUT_MANAGEMENT);

			// Dual smoothing framework.
			double minReducedCost = 0;
			List<Pair<U, Double>> columns = new ArrayList<>();
			List<Pair<U, Double>> generatedColumns = new ArrayList<>();
			long timePricing = System.currentTimeMillis();
			while (true)
			{
				// Compute smoothed duals.
				masterProblem.smootheDuals();

				// Solve pricing problems.
				logger.incrementCount(CountQuantity.NUM_ITERATION_PRICING, 1);
				logger.startTimer(TimeQuantity.TIME_PRICING);
				generatedColumns = null;
				if (potentialColumns != null)
				{
					double reducedCostThreshold = -1.0 * PRECISION;
					EnumerationSolver<T, U, V> enumerationSolver = new EnumerationSolver<>(potentialColumns);
					generatedColumns = enumerationSolver.enumerateColumns(masterProblem, null, null,
							reducedCostThreshold, Integer.MAX_VALUE);
				}
				else
				{
					AbstractPricingProblemSolver<T, U, V> pricingProblemSolver = PricingSettings.EXACT_PRICING
							? exactPricingProblemSolver
							: heuristicPricingProblemSolver;
					generatedColumns = pricingRoutine.generateColumns(masterProblem, pricingProblemSolver, instance);
				}
				logger.stopTimer(TimeQuantity.TIME_PRICING);
				logger.incrementCount(CountQuantity.NUM_GENERATED_COL, generatedColumns.size());

				if (debugReducedCost && Math.abs(masterProblem.getBeta()) == 0)
				{
					for (Pair<U, Double> column : generatedColumns)
					{
						double reducedCost = masterProblem.getReducedCost(column.getKey(), false);
						if (Math.abs(reducedCost - column.getValue()) > 1)
						{
							throw new IllegalArgumentException("REDUCED COST ERROR");
						}
					}
				}

				// Select a subset of columns, filtering on reduced cost.
				logger.startTimer(TimeQuantity.TIME_COL_MANAGEMENT);
				List<Pair<U, Double>> filteredColumns = new ArrayList<>();
				for (Pair<U, Double> column : generatedColumns)
				{
					double reducedCost = masterProblem.getReducedCost(column.getKey(), false);
					if (reducedCost < -PRECISION)
					{
						minReducedCost = Math.min(minReducedCost, reducedCost);
						filteredColumns.add(new Pair<>(column.getKey(), reducedCost));
					}
				}
				columns = columnSelector.selectColumns(filteredColumns);
				logger.stopTimer(TimeQuantity.TIME_COL_MANAGEMENT);
				logger.incrementCount(CountQuantity.NUM_SELECTED_COL, columns.size());

				// Break from pricing iteration if we identified negative reduced cost columns.
				if (columns.size() > 0 || masterProblem.getBeta() < PRECISION || !PricingSettings.EXACT_PRICING)
				{
					break;
				}
			}
			timePricing = System.currentTimeMillis() - timePricing;

			// Dual smoothing updates.
			if (autoTuneAlpha)
			{
				masterProblem.updateAlpha(generatedColumns);
			}
			if (lagrangianSmoothing)
			{
				// Perform smoothing based on best Lagrangian bound.
				double LRBound = masterProblem.computeLagrangianBound(generatedColumns);
				System.out.println("LR Bound: " + LRBound);
				if (LRBound > lagrangianBound)
				{
					lagrangianBound = LRBound;
					masterProblem.updateLagrangianDuals();
				}
			}
			else
			{
				// Use Neame's smoothing.
				masterProblem.updateIncumbentDuals();
			}

			// Separate cuts.
			Set<AbstractConstraint<T, U, V>> cuts = new LinkedHashSet<>();
			boolean isFeasible = masterProblem.isFeasible();
			boolean isOptimal = columns.isEmpty()
					&& (PricingSettings.EXACT_PRICING || !PricingSettings.SWITCH_TO_EXACT_PRICING);

			for (AbstractCutSeparator<T, U, V> cutSeparator : cutSeparators)
			{
				if (!cutSeparator.separateInIteration(iteration, isFeasible, isOptimal))
				{
					continue;
				}
				logger.startTimer(TimeQuantity.TIME_CUT_SEPARATION);
				cuts.addAll(cutSeparator.generateCuts(solution));
				logger.stopTimer(TimeQuantity.TIME_CUT_SEPARATION);
			}
			logger.incrementCount(CountQuantity.NUM_SEPARATED_CUT, cuts.size());

			// Debugging purposes
			if (debug)
			{
				System.out.println("Itr. " + iteration + ". Obj: " + Writer.formatDouble(objectiveValue) + "RMP cols: "
						+ masterProblem.getColumns().size() + ". Exact: " + PricingSettings.EXACT_PRICING + ". Cols: "
						+ columns.size() + ". RMP (ms): " + timeRMP + ". PP (ms): " + timePricing + ". Feasible: "
						+ isFeasible + ". Min RC: " + Writer.formatDouble(minReducedCost) + ". LR Bound: "
						+ Writer.formatDouble(lagrangianBound) + ". Alpha: " + Writer.formatDouble(masterProblem.alpha)
						+ ". Cuts: " + cuts.size());
			}

			// Terminate if we have reached a known lower bound.
			if (Math.abs(objectiveValue - lowerBound) < PRECISION && cutSeparators.isEmpty())
			{
				break;
			}

			// Stop if no columns are found.
			if (columns.isEmpty())
			{
				// Switch to exact pricing if necessary.
				if (!PricingSettings.EXACT_PRICING && PricingSettings.SWITCH_TO_EXACT_PRICING
						&& (cuts.isEmpty() || iteration > 0))
				{
					PricingSettings.EXACT_PRICING = true;
					lagrangianBound = Double.NEGATIVE_INFINITY;
				}
				else
				{
					// Terminate when we are in exact pricing, no columns are found, and there are
					// no cuts or the master problem is already infeasible.
					if (cuts.isEmpty() || !masterProblem.isFeasible())
					{
						break;
					}
				}
			}
			else
			{
				// Remove columns if we are not in convergence phase.
				logger.startTimer(TimeQuantity.TIME_COL_MANAGEMENT);
				boolean converging = Math.abs(objectiveValue - previousObjective) < PRECISION;
				if (!converging && COLUMN_REMOVAL_FREQUENCY > 0 && iteration % COLUMN_REMOVAL_FREQUENCY == 0)
				{
					logger.incrementCount(CountQuantity.NUM_REMOVED_COL, masterProblem.removeInactiveColumns());
				}

				// Add columns to the RMP.
				for (Pair<U, Double> column : columns)
				{
					masterProblem.addColumn(column.getKey());
				}
				logger.stopTimer(TimeQuantity.TIME_COL_MANAGEMENT);

				// Remove cuts if we are not in a convergence phase.
				logger.startTimer(TimeQuantity.TIME_CUT_MANAGEMENT);
				if (!converging && CUT_REMOVAL_FREQUENCY > 0 && iteration % CUT_REMOVAL_FREQUENCY == 0)
				{
					logger.incrementCount(CountQuantity.NUM_REMOVED_CUT, masterProblem.removeInactiveCuts());
				}
				logger.stopTimer(TimeQuantity.TIME_CUT_MANAGEMENT);
			}

			// Add separated cuts to the RMP.
			logger.startTimer(TimeQuantity.TIME_CUT_MANAGEMENT);
			for (AbstractConstraint<T, U, V> cut : cuts)
			{
				masterProblem.addCut(cut);
			}
			logger.stopTimer(TimeQuantity.TIME_CUT_MANAGEMENT);

			// Update trackers.
			previousObjective = objectiveValue;
			iteration++;
		}
	}
}
