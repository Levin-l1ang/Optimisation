package optimisation.columnGeneration;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ilog.concert.IloColumn;
import ilog.concert.IloConversion;
import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.BasisStatus;
import ilog.cplex.IloCplex.Status;
import optimisation.BAP.AbstractBranchingDecision;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Configuration;
import util.Pair;

public abstract class AbstractMasterProblem<T extends AbstractInstance, U extends AbstractColumn<T, V>, V extends AbstractPricingProblem<T>>
{
	protected final T instance;

	protected IloCplex cplex;
	protected IloObjective objective;

	protected Map<AbstractConstraint<T, U, V>, IloRange> constraintMap;
	protected Set<AbstractConstraint<T, U, V>> cuts;

	protected Set<AbstractConstraint<T, U, V>> branchingConstraints;
	protected Set<AbstractBranchingDecision<T, U, V>> branchingDecisions;

	protected Map<U, IloNumVar> columnMap;
	protected Map<AbstractConstraint<T, U, V>, IloNumVar> positiveSlackVarMap;
	protected Map<AbstractConstraint<T, U, V>, IloNumVar> negativeSlackVarMap;

	// Dual smoothing parameters.
	protected DualVariables<T, U, V> currentDuals;
	protected DualVariables<T, U, V> incumbentDuals;
	protected DualVariables<T, U, V> separationDuals;

	protected int k = 1;
	protected final double alphaUpperBound = Configuration.getConfiguration().getDoubleProperty("ALPHA_UB");
	protected double alpha = Configuration.getConfiguration().getDoubleProperty("ALPHA");
	protected double beta = 0;

	// Restricted master heuristic information.
	protected double lowerBoundMILP;

	public AbstractMasterProblem(T instance) throws IloException
	{
		this.instance = instance;

		this.cplex = new IloCplex();
		this.objective = cplex.addMinimize();

		this.constraintMap = new LinkedHashMap<>();
		this.cuts = new LinkedHashSet<>();

		this.branchingConstraints = new LinkedHashSet<>();
		this.branchingDecisions = new LinkedHashSet<>();

		this.columnMap = new LinkedHashMap<>();
		this.positiveSlackVarMap = new LinkedHashMap<>();
		this.negativeSlackVarMap = new LinkedHashMap<>();

		this.currentDuals = new DualVariables<>();
		this.incumbentDuals = new DualVariables<>();
		this.separationDuals = new DualVariables<>();

		// Set random seed for replication purposes.
		cplex.setParam(IloCplex.Param.RandomSeed, 129012);

		// Set the MIP tolerance lower than the default.
		cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 1e-06);

		// Set number of threads available to the solver.
		cplex.setParam(IloCplex.Param.Threads, Configuration.getConfiguration().getIntProperty("NUM_THREADS_SOLVER"));

		// Turn off console output.
		cplex.setOut(null);
		cplex.setWarning(null);
	}

	public void updateDuals() throws IloException
	{
		for (AbstractConstraint<T, U, V> constraint : constraintMap.keySet())
		{
			// Update current duals.
			double dual = cplex.getDual(constraintMap.get(constraint));
			currentDuals.set(constraint, dual);

			// Initialise incumbent duals, if not yet available.
			if (!incumbentDuals.contains(constraint))
			{
				incumbentDuals.set(constraint, currentDuals.get(constraint));
			}
		}

		// Update dual parameter.
		k = 1;
	}

	public void updateIncumbentDuals()
	{
		for (AbstractConstraint<T, U, V> constraint : constraintMap.keySet())
		{
			incumbentDuals.set(constraint,
					alpha * incumbentDuals.get(constraint) + (1 - alpha) * currentDuals.get(constraint));
		}
	}

	public void smootheDuals() throws IloException
	{
		// Update alpha and k.
		beta = Math.max(0, 1.0 - k * (1.0 - alpha));
		k++;

		// Compute smoothed duals.
		for (AbstractConstraint<T, U, V> constraint : constraintMap.keySet())
		{
			separationDuals.set(constraint,
					beta * incumbentDuals.get(constraint) + (1.0 - beta) * currentDuals.get(constraint));
		}
	}

	public void updateAlpha(List<Pair<U, Double>> columns)
	{
		double value = 0;
		for (AbstractConstraint<T, U, V> constraint : separationDuals.getConstraints())
		{
			double g = constraint.getBound();
			for (Pair<U, Double> column : columns)
			{
				if (constraint.containsColumn(column.getKey()))
				{
					g -= constraint.getCoefficient(column.getKey());
				}
			}
			value += g * (currentDuals.get(constraint) - incumbentDuals.get(constraint));
		}
		if (value < 0)
		{
			alpha += 0.1 * (alphaUpperBound - alpha);
		}
		else
		{
			alpha = Math.max(0, alpha - 0.1);
		}
	}

	public double computeLagrangianBound(List<Pair<U, Double>> columns)
	{
		double bound = 0;
		for (AbstractConstraint<T, U, V> constraint : separationDuals.getConstraints())
		{
			bound += constraint.getBound() * separationDuals.get(constraint);
		}
		for (Pair<U, Double> column : columns)
		{
			bound += column.getValue();
		}
		return bound;
	}

	public double getBeta()
	{
		return beta;
	}

	public void setDuals(DualVariables<T, U, V> duals)
	{
		for (AbstractConstraint<T, U, V> constraint : duals.getConstraints())
		{
			double value = duals.get(constraint);
			currentDuals.set(constraint, value);
			incumbentDuals.set(constraint, value);
			separationDuals.set(constraint, value);
		}
	}

	public DualVariables<T, U, V> getSeparationDuals()
	{
		return separationDuals.getCopy();
	}

	public DualVariables<T, U, V> getCurrentDuals()
	{
		return currentDuals.getCopy();
	}

	public double getReducedCost(U column, boolean useSeparationDuals)
	{
		double reducedCost = column.getCoefficient();
		for (AbstractConstraint<T, U, V> constraint : constraintMap.keySet())
		{
			if (constraint.containsColumn(column))
			{
				double dual = useSeparationDuals ? separationDuals.get(constraint) : currentDuals.get(constraint);
				double coefficient = constraint.getCoefficient(column);
				reducedCost -= dual * coefficient;
			}
		}
		return reducedCost;
	}

	public List<AbstractConstraint<T, U, V>> getConstraints()
	{
		return new ArrayList<>(constraintMap.keySet());
	}

	public void setOut(OutputStream outputStream) throws IloException
	{
		cplex.setOut(outputStream);
		cplex.setWarning(outputStream);
	}

	public void setTimeLimit(int seconds) throws IloException
	{
		cplex.setParam(IloCplex.Param.TimeLimit, seconds);
	}

	public double getObjectiveValue() throws IloException
	{
		return cplex.getObjValue();
	}

	public double getLowerBoundMILP() throws IloException
	{
		return lowerBoundMILP;
	}

	public double getDual(AbstractConstraint<T, U, V> constraint) throws IloException
	{
		return separationDuals.get(constraint);
	}

	public void setRightHandSide(AbstractConstraint<T, U, V> constraint, double coefficient) throws IloException
	{
		constraintMap.get(constraint).setUB(coefficient);
	}

	public void addCut(AbstractConstraint<T, U, V> cut) throws IloException
	{
		cuts.add(cut);
		addConstraint(cut);
	}

	public void addConstraint(AbstractConstraint<T, U, V> constraint) throws IloException
	{
		// Add all columns to left-hand side.
		IloNumExpr lhs = cplex.constant(0);
		for (Entry<U, IloNumVar> entry : columnMap.entrySet())
		{
			if (constraint.containsColumn(entry.getKey()))
			{
				lhs = cplex.sum(lhs, cplex.prod(entry.getValue(), constraint.getCoefficient(entry.getKey())));
			}
		}

		IloRange range;
		switch (constraint.getModelConstraintType())
		{
		case EQUALITY:
			range = cplex.addEq(lhs, constraint.getBound());
			break;
		case LESSER:
			range = cplex.addLe(lhs, constraint.getBound());
			break;
		case GREATER:
			range = cplex.addGe(lhs, constraint.getBound());
			break;
		default:
			range = null;
			break;
		}
		if (constraintMap.containsKey(constraint))
		{
			System.out.println("Class: " + constraint.getClass());
			System.out.println("Type: " + constraint.getModelConstraintType());
			System.out.println("Bound: " + constraint.getBound());
			System.out.println(constraint);
			System.out.println("ALREADY CONTAINED!");
			return;
//			throw new IllegalArgumentException("ALREADY CONTAINED.");
		}
		constraintMap.put(constraint, range);

		// Add a slack variable.
		constraint.addSlackVariable(this);
	}

	public void addSlackVariable(AbstractConstraint<T, U, V> constraint, boolean positive, double cost,
			double lowerBound, double upperBound) throws IloException
	{
		IloColumn column = cplex.column(objective, cost);
		if (positive)
		{
			column = column.and(cplex.column(constraintMap.get(constraint), 1.0));
			positiveSlackVarMap.put(constraint, cplex.numVar(column, lowerBound, upperBound));
		}
		else
		{
			column = column.and(cplex.column(constraintMap.get(constraint), -1.0));
			negativeSlackVarMap.put(constraint, cplex.numVar(column, lowerBound, upperBound));
		}
	}

	public double getSlackValue(AbstractConstraint<T, U, V> constraint) throws IloException
	{
		double slack = 0;
		if (positiveSlackVarMap.containsKey(constraint))
		{
			slack += cplex.getValue(positiveSlackVarMap.get(constraint));
		}
		if (negativeSlackVarMap.containsKey(constraint))
		{
			slack += cplex.getValue(negativeSlackVarMap.get(constraint));
		}
		return slack;
	}

	public void removeSlackVariables() throws IloException
	{
		for (IloNumVar var : positiveSlackVarMap.values())
		{
			var.setLB(0);
			var.setUB(0);
		}
		for (IloNumVar var : negativeSlackVarMap.values())
		{
			var.setLB(0);
			var.setUB(0);
		}
	}

	public Map<U, Double> getFixedColumns() throws IloException
	{
		Map<U, Double> fixedColumns = new LinkedHashMap<>();
		for (Entry<U, IloNumVar> entry : columnMap.entrySet())
		{
			if (entry.getValue().getLB() == entry.getValue().getUB())
			{
				fixedColumns.put(entry.getKey(), entry.getValue().getLB());
			}
		}
		return fixedColumns;
	}

	public void fixColumn(U column, double value) throws IloException
	{
		columnMap.get(column).setLB(value);
		columnMap.get(column).setUB(value);
	}

	public void unfixColumn(U column) throws IloException
	{
		columnMap.get(column).setLB(0);
		columnMap.get(column).setUB(Double.MAX_VALUE);
	}

	public Map<AbstractConstraint<T, U, V>, Double> getActiveSlackVariables() throws IloException
	{
		Map<AbstractConstraint<T, U, V>, Double> activeSlackMap = new LinkedHashMap<>();
		for (Entry<AbstractConstraint<T, U, V>, IloNumVar> entry : positiveSlackVarMap.entrySet())
		{
			if (cplex.getValue(entry.getValue()) > Configuration.getConfiguration().getDoubleProperty("PRECISION"))
			{
				activeSlackMap.put(entry.getKey(), cplex.getValue(entry.getValue()));
			}
		}
		for (Entry<AbstractConstraint<T, U, V>, IloNumVar> entry : negativeSlackVarMap.entrySet())
		{
			if (cplex.getValue(entry.getValue()) > Configuration.getConfiguration().getDoubleProperty("PRECISION"))
			{
				activeSlackMap.put(entry.getKey(), cplex.getValue(entry.getValue()));
			}
		}
		return activeSlackMap;
	}

	public void export(String fileName) throws IloException
	{
		cplex.exportModel(fileName);
	}

	public void clean() throws IloException
	{
		cplex.clearModel();
		cplex.end();
	}

	public void solve() throws IloException
	{
		cplex.solve();
	}

	public boolean isFeasible() throws IloException
	{
		return cplex.isPrimalFeasible() && getActiveSlackVariables().size() == 0;
	}

	public Status getStatus() throws IloException
	{
		return cplex.getStatus();
	}

	public T getInstance()
	{
		return instance;
	}

	public void removeConstraint(AbstractConstraint<T, U, V> constraint) throws IloException
	{
		cplex.delete(constraintMap.get(constraint));
		constraintMap.remove(constraint);
		currentDuals.remove(constraint);
		incumbentDuals.remove(constraint);
		separationDuals.remove(constraint);
		if (positiveSlackVarMap.containsKey(constraint))
		{
			cplex.delete(positiveSlackVarMap.get(constraint));
			positiveSlackVarMap.remove(constraint);
		}
		if (negativeSlackVarMap.containsKey(constraint))
		{
			cplex.delete(negativeSlackVarMap.get(constraint));
			negativeSlackVarMap.remove(constraint);
		}
	}

	public void undoBranchingDecision(AbstractBranchingDecision<T, U, V> branchingDecision) throws IloException
	{
		for (AbstractConstraint<T, U, V> branchingConstraint : branchingDecision.getBranchingConstraints())
		{
			cplex.delete(constraintMap.get(branchingConstraint));
			constraintMap.remove(branchingConstraint);
			branchingConstraints.remove(branchingConstraint);
		}
		branchingDecisions.remove(branchingDecision);
	}

	public void printActiveBranchingConstraints()
	{
		for (AbstractConstraint<T, U, V> branchingConstraint : branchingConstraints)
		{
			System.out.println("Active branching constraint: " + branchingConstraint);
		}
	}

	public void processBranchingDecision(AbstractBranchingDecision<T, U, V> branchingDecision) throws IloException
	{
		for (AbstractConstraint<T, U, V> branchingConstraint : branchingDecision.getBranchingConstraints())
		{
			branchingConstraints.add(branchingConstraint);
			addConstraint(branchingConstraint);
		}
		branchingDecisions.add(branchingDecision);
	}

	public Set<AbstractBranchingDecision<T, U, V>> getBranchingDecisions()
	{
		return branchingDecisions;
	}

	public double getReducedCost(IloNumVar var) throws IloException
	{
		return cplex.getReducedCost(var);
	}

	public double getValue(IloNumVar var) throws IloException
	{
		return cplex.getValue(var);
	}

	public boolean isBasicVariable(IloNumVar var) throws IloException
	{
		return cplex.getBasisStatus(var).equals(BasisStatus.Basic);
	}

	public void setLowerBound(IloNumVar var, double lowerBound) throws IloException
	{
		var.setLB(lowerBound);
	}

	public void setUpperBound(IloNumVar var, double upperBound) throws IloException
	{
		var.setUB(upperBound);
	}

	public void setObjectiveCoefficient(IloNumVar var, double coefficient) throws IloException
	{
		cplex.setLinearCoef(objective, coefficient, var);
	}

	public void setConstraintCoefficient(IloNumVar var, AbstractConstraint<T, U, V> constraint, double coefficient)
			throws IloException
	{
		cplex.setLinearCoef(constraintMap.get(constraint), coefficient, var);
	}

	public List<Pair<U, IloNumVar>> getColumns()
	{
		List<Pair<U, IloNumVar>> columns = new ArrayList<>();
		for (U column : columnMap.keySet())
		{
			columns.add(new Pair<>(column, columnMap.get(column)));
		}
		return columns;
	}

	public abstract void addColumn(U column) throws IloException;

	public void updateGenericDuals() throws IloException
	{
		for (AbstractConstraint<T, U, V> constraint : constraintMap.keySet())
		{
			constraint.updateGenericDuals(instance, separationDuals.get(constraint));
		}
	}

	public void updateLagrangianDuals()
	{
		for (AbstractConstraint<T, U, V> constraint : constraintMap.keySet())
		{
			incumbentDuals.set(constraint, separationDuals.get(constraint));
		}
	}

	public void updatePricingProblemDuals(V pricingProblem) throws IloException
	{
		for (AbstractConstraint<T, U, V> constraint : constraintMap.keySet())
		{
			constraint.updatePricingProblemDuals(pricingProblem, separationDuals.get(constraint));
		}
	}

	public void fixSlackVariablesToZero() throws IloException
	{
		for (IloNumVar var : positiveSlackVarMap.values())
		{
			var.setUB(0);
		}
		for (IloNumVar var : negativeSlackVarMap.values())
		{
			var.setUB(0);
		}
	}

	public void unfixSlackVariables() throws IloException
	{
		for (Entry<AbstractConstraint<T, U, V>, IloNumVar> entry : positiveSlackVarMap.entrySet())
		{
			entry.getValue().setUB(entry.getKey().getBound());
		}
		for (Entry<AbstractConstraint<T, U, V>, IloNumVar> entry : negativeSlackVarMap.entrySet())
		{
			entry.getValue().setUB(entry.getKey().getBound());
		}
	}

	public AbstractSolution<T, U, V> applyRestrictedMasterHeuristic(int timeLimitSeconds) throws IloException
	{
		// Convert to MILP. Skip columns that need not be integer.
		List<IloConversion> conversions = new ArrayList<>();
		for (Pair<U, IloNumVar> pair : getColumns())
		{
			if (pair.getKey().isIntegerValued())
			{
				IloConversion conversion = cplex.conversion(pair.getValue(), IloNumVarType.Int);
				cplex.add(conversion);
				conversions.add(conversion);
			}
		}

		// Fix slack variables to zero.
		fixSlackVariablesToZero();

		// Solve MILP and store optimality gap.
		setTimeLimit(timeLimitSeconds);
		solve();
		AbstractSolution<T, U, V> solution = null;
		this.lowerBoundMILP = cplex.getBestObjValue();
		if (getStatus().equals(Status.Feasible) || getStatus().equals(Status.Optimal))
		{
			solution = getSolution();
		}

		// Unfix slack variables.
		unfixSlackVariables();

		// Convert back to LP.
		for (IloConversion conversion : conversions)
		{
			cplex.remove(conversion);
		}
		setTimeLimit(Integer.MAX_VALUE);
		return solution;
	}

	public AbstractSolution<T, U, V> getSolution() throws IloException
	{
		// Retrieve all columns with positive coefficients.
		Map<U, Double> solutionMap = new LinkedHashMap<>();
		for (U column : columnMap.keySet())
		{
			double value = cplex.getValue(columnMap.get(column));
			if (value > 0)
			{
				solutionMap.put(column, value);
			}
		}
		return new AbstractSolution<>(getObjectiveValue(), solutionMap);
	}

	public void updateInactiveColumns() throws IloException
	{
		// Increase the inactivity counter for columns that are not used and are not in
		// the basis.
		double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
		IloNumVar[] columns = new IloNumVar[columnMap.size()];
		int i = 0;
		Iterator<Map.Entry<U, IloNumVar>> iter = columnMap.entrySet().iterator();
		while (iter.hasNext())
		{
			Map.Entry<U, IloNumVar> entry = iter.next();
			columns[i] = entry.getValue();
			i++;
		}
		BasisStatus[] basis = cplex.getBasisStatuses(columns);
		i = 0;
		iter = columnMap.entrySet().iterator();
		while (iter.hasNext())
		{
			Map.Entry<U, IloNumVar> entry = iter.next();
			if (basis[i].equals(BasisStatus.Basic) || cplex.getValue(entry.getValue()) > PRECISION
					|| !entry.getKey().canBeRemoved())
			{
				entry.getKey().resetNumIterUnused();
			}
			else
			{
				entry.getKey().increaseNumIterUnused();
			}
			i++;
		}
	}

	public int removeInactiveColumns() throws IloException
	{
		// Settings.
		int removalThreshold = Configuration.getConfiguration().getIntProperty("COLUMN_REMOVAL_THRESHOLD");
		int minimumNonBasic = Configuration.getConfiguration().getIntProperty("MINIMUM_NUMBER_NON_BASIC");
		double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");

		// Sort columns to be removed by reduced cost.
		Map<IloNumVar, Double> reducedCostMap = new LinkedHashMap<>();
		List<Entry<U, IloNumVar>> nonBasicColumns = new ArrayList<>();
		for (Entry<U, IloNumVar> entry : columnMap.entrySet())
		{
			if (entry.getKey().getNumIterUnused() >= removalThreshold)
			{
				double reducedCost = cplex.getReducedCost(entry.getValue());
				if (reducedCost < PRECISION)
				{
					continue;
				}
				nonBasicColumns.add(entry);
				reducedCostMap.put(entry.getValue(), reducedCost);
			}
		}
		nonBasicColumns
				.sort((a, b) -> Double.compare(reducedCostMap.get(a.getValue()), reducedCostMap.get(b.getValue())));

		// Keep a minimum number of non-basic columns.
		if (nonBasicColumns.size() <= minimumNonBasic)
		{
			return 0;
		}
		nonBasicColumns = nonBasicColumns.subList(minimumNonBasic, nonBasicColumns.size());

		// Remove columns from column map.
		IloNumVar[] removeColumns = new IloNumVar[nonBasicColumns.size()];
		for (int i = 0; i < nonBasicColumns.size(); i++)
		{
			removeColumns[i] = nonBasicColumns.get(i).getValue();
			columnMap.remove(nonBasicColumns.get(i).getKey());
		}

		// Remove columns from the model.
		cplex.end(removeColumns);
		return removeColumns.length;
	}

	public void updateInactiveCuts() throws IloException
	{
		// Increase the inactivity counter for cuts that are not binding.
		IloRange[] cutArray = new IloRange[cuts.size()];
		int i = 0;
		for (AbstractConstraint<T, U, V> constraint : cuts)
		{
			cutArray[i] = constraintMap.get(constraint);
			i++;
		}
		double[] duals = cplex.getDuals(cutArray);

		i = 0;
		for (AbstractConstraint<T, U, V> constraint : cuts)
		{
			if (Math.abs(duals[i]) == 0)
			{
				constraint.increaseNumIterUnused();
			}
			else
			{
				constraint.resetNumIterUnused();
			}
			i++;
		}
	}

	public int removeInactiveCuts() throws IloException
	{
		// Settings.
		int removalThreshold = Configuration.getConfiguration().getIntProperty("CUT_REMOVAL_THRESHOLD");

		// Identify cuts to remove.
		List<AbstractConstraint<T, U, V>> remove = new ArrayList<>();
		for (AbstractConstraint<T, U, V> cut : cuts)
		{
			if (cut.getNumIterUnused() >= removalThreshold)
			{
				remove.add(cut);
			}
		}

		// Remove cuts.
		IloRange[] removeConstraints = new IloRange[remove.size()];
		for (int i = 0; i < remove.size(); i++)
		{
			AbstractConstraint<T, U, V> constraint = remove.get(i);
			removeConstraints[i] = constraintMap.get(constraint);
			constraintMap.remove(constraint);
			currentDuals.remove(constraint);
			incumbentDuals.remove(constraint);
			separationDuals.remove(constraint);
		}
		cuts.removeAll(remove);

		// Remove cuts from the model.
		cplex.end(removeConstraints);
		return removeConstraints.length;
	}
}