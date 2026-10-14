package linePlanning.PTN.benders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplexModeler;
import linePlanning.PTN.PTNRoute;
import linePlanning.lineGeneration.Line;

public class KarbsteinCallback implements IloCplex.Callback.Function
{
	private final static double EPSILON = 0.01;

	private final Map<Line, IloNumVar> lineVariableMap;
	private final Map<PTNRoute, IloNumVar> directRouteVariableMap;
	private final DisaggregationLP model;

	public KarbsteinCallback(Map<Line, IloNumVar> lineVariableMap, Map<PTNRoute, IloNumVar> directRouteVariableMap)
			throws IloException
	{
		this.lineVariableMap = lineVariableMap;
		this.directRouteVariableMap = directRouteVariableMap;

		// Initialise DisaggregationLP once; RHS values are updated on each invocation.
		Map<Line, Double> initialLineValues = new LinkedHashMap<>();
		for (Line line : lineVariableMap.keySet())
		{
			initialLineValues.put(line, 0.0);
		}
		Map<PTNRoute, Double> initialRouteValues = new LinkedHashMap<>();
		for (PTNRoute route : directRouteVariableMap.keySet())
		{
			initialRouteValues.put(route, 0.0);
		}
		this.model = new DisaggregationLP(initialLineValues, initialRouteValues);
	}

	public void clean() throws IloException
	{
		model.clean();
	}

	private double getValue(IloCplex.Callback.Context context, IloNumVar var) throws IloException
	{
		if (context.getId() == IloCplex.Callback.Context.Id.Relaxation)
		{
			return context.getRelaxationPoint(var);
		}
		else
		{
			return context.getCandidatePoint(var);
		}
	}

	private void separateKarbsteinCuts(IloCplex.Callback.Context context) throws IloException
	{
		// Compute variable maps and total flow.
		Map<Line, Double> lineValueMap = new LinkedHashMap<>();
		for (Entry<Line, IloNumVar> entry : lineVariableMap.entrySet())
		{
			//set small negative values to 0
			lineValueMap.put(entry.getKey(), Math.max(getValue(context, entry.getValue()),0.0));
		}
		Map<PTNRoute, Double> directRouteValueMap = new LinkedHashMap<>();
		double totalFlow = 0;
		for (Entry<PTNRoute, IloNumVar> entry : directRouteVariableMap.entrySet())
		{
			double value = Math.max(getValue(context, entry.getValue()),0.0);
			totalFlow += value;
			directRouteValueMap.put(entry.getKey(), value);
		}

		// Solve disaggregation LP (synchronized: callback may be invoked from multiple
		// threads).
		synchronized (model)
		{
			model.updateValues(lineValueMap, directRouteValueMap);
			model.solve();
			double violation = totalFlow - model.getObjectiveValue();

			// Add a violated DC-metric inequality, if any.
			if (violation > EPSILON)
			{
				IloCplexModeler cplex = context.getCplex();
				IloNumExpr lhs = cplex.constant(0);
				for (Entry<Line, IloNumVar> entry : lineVariableMap.entrySet())
				{
					Line line = entry.getKey();
					double coefficient = model.getLineDual(line) * line.getCapacity();
					if(Math.abs(coefficient)>EPSILON) {
						lhs = cplex.sum(lhs, cplex.prod(coefficient, entry.getValue()));
					}
				}
				for (Entry<PTNRoute, IloNumVar> entry : directRouteVariableMap.entrySet())
				{
					double coefficient = 1.0 - model.getAssignmentDual(entry.getKey());
					if(Math.abs(coefficient)>EPSILON) {
						lhs = cplex.sum(lhs, cplex.prod(-1 * coefficient, entry.getValue()));
					}
				}
				IloRange cut = cplex.ge(lhs, 0);
				if (context.getId() == IloCplex.Callback.Context.Id.Relaxation)
				{
					context.addUserCut(cut, IloCplex.CutManagement.UseCutPurge, false);
				}
				else
				{
					context.rejectCandidate(cut);
				}
			}
		}
	}

	@Override
	public void invoke(IloCplex.Callback.Context context) throws IloException
	{
		try
		{
			separateKarbsteinCuts(context);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw e;
		}
	}
}
