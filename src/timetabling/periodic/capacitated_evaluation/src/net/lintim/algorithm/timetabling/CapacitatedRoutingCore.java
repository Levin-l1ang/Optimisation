package net.lintim.algorithm.timetabling;

import net.lintim.model.*;
import net.lintim.solver.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;


/**
 */
public class CapacitatedRoutingCore {

    private static boolean isIntegerFlow;

	public static void routePassengers(Graph<PeriodicEvent, PeriodicActivity> ean, Graph<Stop, Link> ptn, OD od,
									Config config, SolverParameters parameters) {
		Logger logger = new Logger(CapacitatedRoutingCore.class.getCanonicalName());
		boolean useTimetable = Config.getBooleanValueStatic("tim_cap_eval_tt");
		// Create a multi commodity flow problem, one variable for each stop and activity. The od pairs are bundled by
		// origin. The passengers start and end at the corresponding ORIGIN and DESTINATION events

		Solver solver = Solver.createSolver(parameters.getSolverType());
		Model model = solver.createModel();

		parameters.setSolverParameters(model);

		// First, add the variables
		HashMap<Integer, HashMap<PeriodicActivity, Variable>> variables = new HashMap<>();
		logger.debug("Collecting stop ids");
		Collection<Integer> originIds = ean.getNodes().stream()
				.filter(e -> e.getType() == EventType.ORIGIN)
				.map(PeriodicEvent::getStopId)
				.collect(Collectors.toCollection(HashSet::new));
		logger.debug("Bundling od matrix");
		isIntegerFlow = config.getBooleanValue("tim_cap_eval_integer_flow");
		// How many passengers start from stop with this id -> Sum of all C_od for fixed o
		HashMap<Integer, Double> odValues = bundleOdMatrix(od, logger, originIds);
		for (int origin: originIds) {
			variables.put(origin, new HashMap<>());
		}
		int periodLength = config.getIntegerValue("period_length");
		int capacity = config.getIntegerValue("gen_passengers_per_vehicle");
		double changePenalty = config.getDoubleValue("ean_change_penalty");
		logger.debug("Creating variables");
		int duration;
		for (PeriodicActivity activity : ean.getEdges()) {
			if (!isPassengerActivity(activity)) {
				continue;
			}
			duration = activity.getType() == ActivityType.OD ? 0 : useTimetable ? activity.getDuration(periodLength) : (int) activity.getLowerBound();
			if (activity.getType() == ActivityType.CHANGE) {
				duration += changePenalty;
			}
			for (int originId : originIds) {
				if (isIntegerFlow) {
					variables.get(originId).put(activity,
							model.addVariable(0, capacity, Variable.VariableType.INTEGER, duration, "x_stop" + originId + "_activity" + activity.getId()));
				}
				else {
					variables.get(originId).put(activity,
							model.addVariable(0, capacity, Variable.VariableType.CONTINUOUS, duration, "x_stop" + originId + "_activity" + activity.getId()));
				}
			}
		}
		logger.debug("Creating flow conservation constraints");
		// Now the flow conservation constraints
		HashMap<Integer, LinearExpression> incomingFlows;
		HashMap<Integer, LinearExpression> outgoingFlows;
		for (PeriodicEvent event : ean.getNodes()) {
			incomingFlows = new HashMap<>();
			outgoingFlows = new HashMap<>();
			for (int originId : originIds) {
				incomingFlows.put(originId, model.createExpression());
				outgoingFlows.put(originId, model.createExpression());
			}
			for (PeriodicActivity incoming : ean.getIncomingEdges(event)) {
				if (!isPassengerActivity(incoming)) {
					continue;
				}
				for (int originId : originIds) {
					incomingFlows.get(originId).multiAdd(-1, variables.get(originId).get(incoming));
				}
			}
			for (PeriodicActivity outgoing : ean.getOutgoingEdges(event)) {
				if (!isPassengerActivity(outgoing)) {
					continue;
				}
				for (int originId : originIds) {
					outgoingFlows.get(originId).multiAdd(1, variables.get(originId).get(outgoing));
				}
			}
			// Are we at a origin event? Then we need to set the number of departing passengers correctly
			if (event.getType() == EventType.ORIGIN) {
				for (int originId : originIds) {
					incomingFlows.get(originId).add(outgoingFlows.get(originId));
					if (event.getStopId() == originId) {
						model.addConstraint(incomingFlows.get(originId), Constraint.ConstraintSense.EQUAL,
								odValues.get(originId), "flow_conservation_stop" + originId + "_event" + event.getId());
					} else {
						model.addConstraint(incomingFlows.get(originId), Constraint.ConstraintSense.EQUAL,
								0, "flow_conservation_" + originId + "_" + event.getId());
					}
				}
			}
			// Similar case for destination event
			else if (event.getType() == EventType.DESTINATION) {
				for (int originId : originIds) {
					incomingFlows.get(originId).add(outgoingFlows.get(originId));
					model.addConstraint(incomingFlows.get(originId), Constraint.ConstraintSense.EQUAL,
							-1 * od.getValue(originId,
									event.getStopId()), "flow_conservation_stop" + originId + "_event" + event.getId());
				}
			} else {
				for (int originId : originIds) {
					incomingFlows.get(originId).add(outgoingFlows.get(originId));
					model.addConstraint(incomingFlows.get(originId), Constraint.ConstraintSense.EQUAL,
							0, "flow_conservation_stop" + originId + "_event" + event.getId());
				}
			}
		}
		logger.debug("Creating capacity constraints");
		boolean accumulateOnLinks = config.getBooleanValue("tim_cap_eval_accumulate_on_links");
		LinearExpression commonCapacity = model.createExpression();
		// Next constraints: The common capacity of activities
		if (accumulateOnLinks) {
			int cummulatedCapacity;
			for (Link link : ptn.getEdges()) {
				commonCapacity.clear();
				Collection<PeriodicActivity> correspondingActivities = ean.getEdges().stream()
						.filter(a -> activityCorrespondsToLink(a, link))
						.collect(Collectors.toCollection(HashSet::new));
				cummulatedCapacity = capacity * correspondingActivities.size();
				for (PeriodicActivity activity : correspondingActivities) {
					for (int originId : originIds) {
						commonCapacity.multiAdd(1, variables.get(originId).get(activity));
					}
				}
				model.addConstraint(commonCapacity, Constraint.ConstraintSense.LESS_EQUAL, cummulatedCapacity,
						"capacity_activity" + link.getId());
			}
		}
		else {
			for (PeriodicActivity activity : ean.getEdges()) {
				if (activity.getType() != ActivityType.DRIVE) {
					continue;
				}
				commonCapacity.clear();
				for (int originId : originIds) {
					commonCapacity.multiAdd(1, variables.get(originId).get(activity));
				}
				model.addConstraint(commonCapacity, Constraint.ConstraintSense.LESS_EQUAL, capacity,
						"capacity_activity" + activity.getId());
			}
		}
		//Prepare the solving
		model.setSense(Model.OptimizationSense.MINIMIZE);
		if (parameters.writeLpFile()) {
			model.write("timetabling/capacitated-evaluation.lp");
		}

		logger.debug("Start optimization");
		model.solve();
		logger.debug("Finished optimization");

		// Read back solution
		Model.Status status = model.getStatus();
		if (model.getIntAttribute(Model.IntAttribute.NUM_SOLUTIONS) > 0) {
			if (status == Model.Status.OPTIMAL) {
				logger.debug("Optimal solution found");
			} else {
				logger.debug("Feasible solution found");
			}
			// Store solution
			logger.debug("Reading solution");
			int passengers;
			for (PeriodicActivity activity : ean.getEdges()) {
				if (!isPassengerActivity(activity)) {
					continue;
				}
				passengers = 0;
				for (int originId : originIds) {
					passengers += model.getValue(variables.get(originId).get(activity));
				}
				activity.setNumberOfPassengers(passengers);
			}
			// model.dispose();
			// solver.dispose();
		}
		else {
			logger.error("No feasible solution found");
			if (status == Model.Status.INFEASIBLE) {
				logger.error("The problem is infeasible!");
				model.computeIIS("timetabling/capacitated-evaluation");
				// model.dispose();
				// solver.dispose();
				throw new RuntimeException("Could not compute capacitated routing, instance is infeasible!");
			}
		}
	}

	static boolean isPassengerActivity(PeriodicActivity act) {
		return act.getType() == ActivityType.DRIVE || act.getType() == ActivityType.WAIT
				|| act.getType() == ActivityType.CHANGE || act.getType() == ActivityType.OD;
	}

	static boolean activityCorrespondsToLink(PeriodicActivity act, Link link) {
		return act.getType() == ActivityType.DRIVE
				&& ((act.getLeftNode().getStopId() == link.getLeftNode().getId()
						&& act.getRightNode().getStopId() == link.getRightNode().getId())
					|| (!link.isDirected()
						&& act.getLeftNode().getStopId() == link.getRightNode().getId()
						&& act.getRightNode().getStopId() == link.getLeftNode().getId()));
	}

	static HashMap<Integer, Double> bundleOdMatrix(OD od, Logger logger, Collection<Integer> stopIds) {
		var odValues = new HashMap<Integer, Double>();
        double value;
		double sum;
		for (int origin : stopIds) {
			sum = 0;
			for (int destination : stopIds) {
				value = od.getValue(origin, destination);
				if (isIntegerFlow && Math.abs(Math.round(value) - value) > 0.001) {
					logger.warn("For non-integer od values, the flow needs to be fractional. Setting " +
							"'tim_cap_eval_integer_flow' to false!");
					isIntegerFlow = false;
				}
				sum += value;
			}
			odValues.put(origin, sum);
		}
		return odValues;
	}

}
