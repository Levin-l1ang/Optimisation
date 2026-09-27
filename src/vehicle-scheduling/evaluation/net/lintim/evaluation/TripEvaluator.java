package net.lintim.evaluation;

import net.lintim.model.*;
import net.lintim.util.LogLevel;
import net.lintim.util.Statistic;

import java.util.Collection;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Class implementing evaluation methods for trips. Call {@link #evaluateTrips(Collection, Graph)} or
 * {@link #evaluateTrips(Collection, Graph, Statistic)}, depending on where to store the resulting evaluation
 * parameters.
 */
public class TripEvaluator {
	private static Logger logger = Logger.getLogger("net.lintim.evaluation.TripEvaluator");

	/**
	 * Evaluate the given collection of trips and store the results in the given statistic.
	 *
	 * @param trips     the trips to evaluate
	 * @param statistic the statistic to write the evaluated parameters to
	 */
	public static void evaluateTrips(Collection<Trip> trips, Graph<AperiodicEvent, AperiodicActivity> ean,
	                                 Statistic statistic) {
		determineProperties(trips, statistic);
		Statistic.putStatic("ro_trips_feasible", checkFeasibility(trips, ean));
	}

	/**
	 * Evaluate the given collection of trips and store the results in the default statistic, see {@link Statistic}.
	 *
	 * @param trips the trips to evaluate
	 */
	public static void evaluateTrips(Collection<Trip> trips, Graph<AperiodicEvent, AperiodicActivity> ean) {
		evaluateTrips(trips, ean, Statistic.getDefaultStatistic());
	}

	private static int calculateNumberOfStartAndEndStations(Collection<Trip> trips) {
		HashSet<Integer> usedIds = new HashSet<>();
		for (Trip trip : trips) {
			int startStopId = trip.getStartStopId();
			int endStopId = trip.getEndStopId();
			if (startStopId > 0) {
				usedIds.add(startStopId);
			}
			if (endStopId > 0) {
				usedIds.add(endStopId);
			}
		}
		return usedIds.size();
	}

	/**
	 * Determine some basic properties of the trips and add them to the statistic
	 *
	 * @param trips     the trips to examine
	 * @param statistic the statistic to add the values to
	 */
	private static void determineProperties(Collection<Trip> trips, Statistic statistic) {
		logger.log(LogLevel.DEBUG, "Evaluating number of trips");
		int numberOfTrips = trips.size();
		statistic.put("ro_prop_trips", numberOfTrips);
		logger.log(LogLevel.DEBUG, "Evaluating number of stations, that are start and end of a trip");
		int numberOfStartAndEndStations = calculateNumberOfStartAndEndStations(trips);
		statistic.put("ro_prop_stations_at_begin_or_end", numberOfStartAndEndStations);
	}

	/**
	 * Check the feasibility of the trips, i.e., whether all lines in the aperiodic ean are covered and whether the
	 * given trips correspond to line trips in the ean
	 *
	 * @param trips the trips to check
	 * @param ean   the corresponding aperiodic ean. We assume, that the ean itself is feasible!
	 * @return whether the trips are feasible
	 */
	private static boolean checkFeasibility(Collection<Trip> trips, Graph<AperiodicEvent, AperiodicActivity> ean) {
		// We iterate the trips and all events contained in them. Found events will be removed from the ean.
		// Therefore we assure that no event is used in two different trips. In the end, there should be no more
		// events in the graph
		HashSet<AperiodicEvent> remainingEvents = new HashSet<>(ean.getNodes());
		for (Trip trip : trips) {
			// First, find the start event
			logger.log(LogLevel.DEBUG, "Looking for node " + trip.getStartAperiodicEventId());
			AperiodicEvent currentEvent = ean.getNode(trip.getStartAperiodicEventId());
			// Check the information in the start event
			if (currentEvent.getPeriodicEventId() != trip.getStartPeriodicEventId() ||
					currentEvent.getStopId() != trip.getStartStopId() ||
					currentEvent.getTime() != trip.getStartTime()) {
				logger.log(LogLevel.INFO, "Found inconsistent state in trip " + trip + " and event " + currentEvent);
				return false;
			}
			while (true) {
				AperiodicActivity nextActivity = ean.getOutgoingEdges(currentEvent).stream()
						.filter(act -> act.getType() == ActivityType.DRIVE || act.getType() == ActivityType.WAIT)
						.findAny().orElse(null);
				if (nextActivity == null) {
					logger.log(LogLevel.DEBUG, "Found end of line");
					break;
				}
				logger.log(LogLevel.DEBUG, "Removing event " + currentEvent);
				remainingEvents.remove(currentEvent);
				currentEvent = nextActivity.getRightNode();
			}
			logger.log(LogLevel.DEBUG, "Removing event " + currentEvent);
			remainingEvents.remove(currentEvent);
			// Current event holds the end event of the line, i.e., the event without outgoing wait and drive
			// activity. Check again for consistency
			if (currentEvent.getPeriodicEventId() != trip.getEndPeriodicEventId() ||
					currentEvent.getStopId() != trip.getEndStopId() ||
					currentEvent.getTime() != trip.getEndTime()) {
				logger.log(LogLevel.INFO, "Found inconsistent state in trip " + trip + " and event " + currentEvent);
				return false;
			}
		}
		if (remainingEvents.size() > 0) {
			logger.log(LogLevel.INFO, "Not every event is covered by the trips!");
			logger.log(LogLevel.DEBUG, "Uncovered events are:");
			for(AperiodicEvent event : ean.getNodes()){
				logger.log(LogLevel.DEBUG, event.toString());
			}
			return false;
		}
		// We couldn't find any infeasibility, therefore the trips are feasible
		return true;
	}
}
