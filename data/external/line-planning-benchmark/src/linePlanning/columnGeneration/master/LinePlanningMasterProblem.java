package linePlanning.columnGeneration.master;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ilog.concert.IloColumn;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Callback.Function;
import linePlanning.PTN.LightBasicDirectRouteConstraint;
import linePlanning.columnGeneration.columns.LineActivationColumn;
import linePlanning.columnGeneration.columns.LineFrequencyColumn;
import linePlanning.columnGeneration.columns.LinePlanningColumn;
import linePlanning.columnGeneration.columns.RouteColumn;
import linePlanning.columnGeneration.constraints.BudgetConstraint;
import linePlanning.columnGeneration.constraints.CapacityConstraint;
import linePlanning.columnGeneration.constraints.DemandConstraint;
import linePlanning.columnGeneration.constraints.FixedCostCouplingConstraint;
import linePlanning.columnGeneration.constraints.FrequencyConstraint;
import linePlanning.columnGeneration.cuts.LineActivationStrongInequality;
import linePlanning.columnGeneration.cuts.StrongInequality;
import linePlanning.columnGeneration.pricing.LinePlanningPricingProblem;
import linePlanning.columnGeneration.pricing.LinePlanningPricingRoutine.NetworkType;
import linePlanning.columnGeneration.pricing.NetworkArc;
import linePlanning.instance.LinePlanningInstance;
import linePlanning.instance.ODPair;
import linePlanning.lineGeneration.Line;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.AbstractSolution;

public class LinePlanningMasterProblem
		extends AbstractMasterProblem<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>
{
	private final Map<ODPair, Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>>> ODPairConstraintMap;
	private final Map<NetworkArc, Set<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>>> arcConstraintMap;
	private final List<AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem>> lineConstraints;
	private final NetworkType networkType;

	public LinePlanningMasterProblem(LinePlanningInstance instance, NetworkType networkType) throws IloException
	{
		super(instance);

		this.ODPairConstraintMap = new LinkedHashMap<>();
		this.arcConstraintMap = new LinkedHashMap<>();
		this.lineConstraints = new ArrayList<>();
		this.networkType = networkType;
	}

	@Override
	public void addConstraint(
			AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> constraint)
			throws IloException
	{
		super.addConstraint(constraint);

		// Store constraint in appropriate mapping.
		if (constraint instanceof DemandConstraint)
		{
			DemandConstraint demandConstraint = (DemandConstraint) constraint;
			ODPair pair = demandConstraint.getODPair();
			if (!ODPairConstraintMap.containsKey(pair))
			{
				ODPairConstraintMap.put(pair, new LinkedHashSet<>());
			}
			ODPairConstraintMap.get(pair).add(constraint);
		}
		if (constraint instanceof CapacityConstraint)
		{
			CapacityConstraint capacityConstraint = (CapacityConstraint) constraint;
			NetworkArc arc = capacityConstraint.getArc();
			if (!arcConstraintMap.containsKey(arc))
			{
				arcConstraintMap.put(arc, new LinkedHashSet<>());
			}
			arcConstraintMap.get(arc).add(constraint);
		}
		if (constraint instanceof LightBasicDirectRouteConstraint)
		{
			LightBasicDirectRouteConstraint directRouteConstraint = (LightBasicDirectRouteConstraint) constraint;
			NetworkArc arc = (NetworkArc) directRouteConstraint.getConnection();
			if (!arcConstraintMap.containsKey(arc))
			{
				arcConstraintMap.put(arc, new LinkedHashSet<>());
			}
			arcConstraintMap.get(arc).add(constraint);
		}
		if (constraint instanceof StrongInequality)
		{
			StrongInequality strongInequality = (StrongInequality) constraint;
			NetworkArc arc = strongInequality.getArc();
			if (!arcConstraintMap.containsKey(arc))
			{
				arcConstraintMap.put(arc, new LinkedHashSet<>());
			}
			arcConstraintMap.get(arc).add(constraint);
		}
		if (constraint instanceof LineActivationStrongInequality)
		{
			LineActivationStrongInequality strongInequality = (LineActivationStrongInequality) constraint;
			NetworkArc arc = strongInequality.getArc();
			if (!arcConstraintMap.containsKey(arc))
			{
				arcConstraintMap.put(arc, new LinkedHashSet<>());
			}
			arcConstraintMap.get(arc).add(constraint);
		}
		if (constraint instanceof BudgetConstraint)
		{
			lineConstraints.add(constraint);
		}
		if (constraint instanceof FixedCostCouplingConstraint)
		{
			lineConstraints.add(constraint);
		}
		if (constraint instanceof FrequencyConstraint)
		{
			lineConstraints.add(constraint);
		}

	}

	public void setMaximumWorkMem(int maxMB) throws IloException {
		cplex.setParam(IloCplex.Param.WorkMem, maxMB);
		cplex.setParam(IloCplex.Param.MIP.Strategy.File, 2);
	}

	@Override
	public void addColumn(LinePlanningColumn column) throws IloException
	{
		// Initialise column.
		IloColumn columnVar = cplex.column(objective, column.getCoefficient());
		
		if (columnMap.containsKey(column))
		{
			System.out.println("ALREADY CONTAINED! " + column);
		}

		// For route columns, we only iterate over relevant constraints for efficiency
		// purposes.
		if (column instanceof RouteColumn)
		{
			RouteColumn routeColumn = (RouteColumn) column;
			ODPair pair = routeColumn.getODPair();

			// Iterate over OD-constraints.
			for (AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> constraint : ODPairConstraintMap
					.get(pair))
			{
				if (constraint.containsColumn(column))
				{
					columnVar = columnVar
							.and(cplex.column(constraintMap.get(constraint), constraint.getCoefficient(column)));
				}
			}

			// Iterate over constraints of arcs in the route.
			if (routeColumn.getRoute() != null)
			{
				for (NetworkArc arc : routeColumn.getRoute().getArcs())
				{
					if (!arcConstraintMap.containsKey(arc))
					{
						continue;
					}
					for (AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> constraint : arcConstraintMap
							.get(arc))
					{
						if (constraint.containsColumn(column))
						{
							columnVar = columnVar.and(
									cplex.column(constraintMap.get(constraint), constraint.getCoefficient(column)));
						}
					}
				}
			}

			// Add column.
			columnMap.put(column, cplex.numVar(columnVar, 0, Double.MAX_VALUE));
		}
		else if (column instanceof LineActivationColumn || column instanceof LineFrequencyColumn)

		{
			// Retrieve relevant arcs.
			Line line = null;
			if (column instanceof LineActivationColumn)
			{
				line = ((LineActivationColumn) column).getLine();
			}
			else
			{
				line = ((LineFrequencyColumn) column).getLine();
			}
			List<NetworkArc> allArcs = new ArrayList<>();
			if (networkType.equals(NetworkType.PTN))
			{
				allArcs.addAll(line.getConnectionPathForward());
				allArcs.addAll(line.getConnectionPathBackward());
			}
			if (networkType.equals(NetworkType.DPTN))
			{
				allArcs.addAll(line.getArcPathForward());
				allArcs.addAll(line.getArcPathBackward());
			}
			if (networkType.equals(NetworkType.CGN))
			{
				allArcs.addAll(instance.getCGN().getArcs(line));
			}

			// Check line constraints.
			for (AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> constraint : lineConstraints)
			{
				if (constraint.containsColumn(column))
				{
					columnVar = columnVar
							.and(cplex.column(constraintMap.get(constraint), constraint.getCoefficient(column)));
				}
			}

			// Iterate over arc constraints, such as capacity constraints and strong
			// inequalities.
			for (NetworkArc arc : allArcs)
			{
				if (!arcConstraintMap.containsKey(arc))
				{
					continue;
				}
				for (AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> constraint : arcConstraintMap
						.get(arc))
				{
					if (constraint.containsColumn(column))
					{
						columnVar = columnVar
								.and(cplex.column(constraintMap.get(constraint), constraint.getCoefficient(column)));
					}
				}
			}

			// Add column
			if (column instanceof LineActivationColumn)
			{
				columnMap.put(column, cplex.numVar(columnVar, 0, 1));
			}
			if (column instanceof LineFrequencyColumn)
			{
				columnMap.put(column, cplex.numVar(columnVar, 0, line.getMaxFrequency()));
			}
		}
		else
		{
			// Add to regular constraints.
			for (AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> constraint : constraintMap
					.keySet())
			{
				if (constraint.containsColumn(column))
				{
					columnVar = columnVar
							.and(cplex.column(constraintMap.get(constraint), constraint.getCoefficient(column)));
				}
			}

			// Add column.
			if (column instanceof LineFrequencyColumn)
			{
				LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
				columnMap.put(column, cplex.numVar(columnVar, 0, lineColumn.getLine().getMaxFrequency()));
			}
			if (column instanceof LineActivationColumn)
			{
				columnMap.put(column, cplex.numVar(columnVar, 0, 1));
			}
		}
	}

	@Override
	public void unfixColumn(LinePlanningColumn column) throws IloException
	{
		if (column instanceof LineFrequencyColumn)
		{
			LineFrequencyColumn lineColumn = (LineFrequencyColumn) column;
			columnMap.get(column).setLB(0);
			columnMap.get(column).setUB(lineColumn.getLine().getMaxFrequency());
		}
		else if (column instanceof LineActivationColumn)
		{
			columnMap.get(column).setLB(0);
			columnMap.get(column).setUB(1);
		}
		else
		{
			columnMap.get(column).setLB(0);
			columnMap.get(column).setUB(Double.MAX_VALUE);
		}
	}

	public AbstractSolution<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> applyRestrictedMasterHeuristicWithCallback(
			int timeLimitSeconds, Function callback) throws IloException
	{
		// Impose callback.
		cplex.use(callback, IloCplex.Callback.Context.Id.Relaxation | IloCplex.Callback.Context.Id.Candidate);

		// Return value of RMH.
		return applyRestrictedMasterHeuristic(timeLimitSeconds);
	}

	@Override
	public double getReducedCost(LinePlanningColumn column, boolean useSeparationDuals)
	{
		if (column instanceof RouteColumn)
		{
			double reducedCost = column.getCoefficient();
			RouteColumn routeColumn = (RouteColumn) column;
			ODPair pair = routeColumn.getODPair();

			// Iterate over OD-constraints.
			for (AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> constraint : ODPairConstraintMap
					.get(pair))
			{
				if (constraint.containsColumn(column))
				{
					double dual = useSeparationDuals ? separationDuals.get(constraint) : currentDuals.get(constraint);
					double coefficient = constraint.getCoefficient(column);
					reducedCost -= dual * coefficient;
				}
			}

			// Iterate over constraints of arcs in the route.
			if (routeColumn.getRoute() != null)
			{
				for (NetworkArc arc : routeColumn.getRoute().getArcs())
				{
					if (!arcConstraintMap.containsKey(arc))
					{
						continue;
					}
					for (AbstractConstraint<LinePlanningInstance, LinePlanningColumn, LinePlanningPricingProblem> constraint : arcConstraintMap
							.get(arc))
					{
						if (constraint.containsColumn(column))
						{
							double dual = useSeparationDuals ? separationDuals.get(constraint)
									: currentDuals.get(constraint);
							double coefficient = constraint.getCoefficient(column);
							reducedCost -= dual * coefficient;
						}
					}
				}
			}
			return reducedCost;
		}
		else
		{
			return super.getReducedCost(column, useSeparationDuals);
		}
	}
}
