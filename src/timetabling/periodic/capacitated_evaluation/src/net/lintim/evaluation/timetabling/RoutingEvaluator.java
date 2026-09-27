package net.lintim.evaluation.timetabling;

import net.lintim.model.ActivityType;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;
import net.lintim.util.Config;
import net.lintim.util.Logger;
import net.lintim.util.Statistic;

/**
 */
public class RoutingEvaluator {

	public static void evaluateRouting(Graph<PeriodicEvent, PeriodicActivity> ean, Statistic statistic, Config config,
	                                   double numberOfPassengers) {
		Logger logger = new Logger(RoutingEvaluator.class.getCanonicalName());
		int capacity = config.getIntegerValue("gen_passengers_per_vehicle");
		int periodLength = config.getIntegerValue("period_length");
		double changePenalty = config.getDoubleValue("ean_change_penalty");
        boolean useTimetable = config.getBooleanValue("tim_cap_eval_tt");

		double travelTime = 0;
		double perceivedTravelTime = 0;
		double maxLoad = 0;
		for (PeriodicActivity activity : ean.getEdges()) {
			if (activity.getType() != ActivityType.WAIT && activity.getType() != ActivityType.DRIVE
					&& activity.getType() != ActivityType.CHANGE) {
				continue;
			}
			int duration = activity.getType() == ActivityType.OD ? 0 : useTimetable ? activity.getDuration(periodLength) : (int) activity.getLowerBound();
			travelTime += activity.getNumberOfPassengers() * duration;
			if (activity.getType() == ActivityType.CHANGE) {
				duration += changePenalty;
			}
			perceivedTravelTime += activity.getNumberOfPassengers() * duration;
			if (activity.getType() != ActivityType.DRIVE) {
				continue;
			}
			double load = activity.getNumberOfPassengers() / capacity;
			if (load > maxLoad) {
				logger.debug("Found new max load activity " + activity.getId());
				maxLoad = load;
			}
		}
		statistic.put("tim_capacitated_travel_time", travelTime);
		statistic.put("tim_capacitated_travel_time_average", travelTime / numberOfPassengers);
		statistic.put("tim_capacitated_perceived_travel_time", perceivedTravelTime);
		statistic.put("tim_capacitated_perceived_travel_time_average", perceivedTravelTime / numberOfPassengers);
		statistic.put("tim_capacitated_max_load", maxLoad);
	}
}
