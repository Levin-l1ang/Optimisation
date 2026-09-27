package net.lintim.algorithm.timetabling;

import net.lintim.exception.LinTimException;
import net.lintim.model.EventType;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.PeriodicEanHelper;

import java.util.Map;

/**
 * Class for postprocessing an ean, after computing a timetable with fixed times.
 */
public class EanPostprocessor {

	private static Logger logger = new Logger(EanPostprocessor.class.getCanonicalName());
	private Graph<PeriodicEvent, PeriodicActivity> ean;
	private int periodLength;

	/**
	 * Postprocess the given ean. This will remove the fixed event and all incident edges. Afterwards, a time shift
	 * will be applied to fulfill the given time bounds on the events. This will not restore the
	 * original ean, i.e., there may be changed activities that were infeasible after the preprocessing.
	 * @param ean the ean to postprocess
	 * @param periodLength the period length
	 */
	public static void postprocessEan(Graph<PeriodicEvent, PeriodicActivity> ean, int periodLength) {
		EanPostprocessor eanPostprocessor = new EanPostprocessor(ean, periodLength);
		PeriodicEvent fixedEvent = eanPostprocessor.findFixedEvent();
		ean.removeNode(fixedEvent);
		int timeShift = fixedEvent.getTime();
		eanPostprocessor.applyTimeshift(timeShift);
	}

	/**
	 * Check whether the given time bounds are fulfilled. Will log warnings for every infeasible event.
	 * @param eventTimeBounds the time bounds on the events
	 * @return whether all events lie within their bounds
	 */
	public static boolean checkTimeBounds(Map<PeriodicEvent, Pair<Integer, Integer>> eventTimeBounds, int periodLength) {
		boolean feasible = true;
		for (Map.Entry<PeriodicEvent, Pair<Integer, Integer>> eventBoundEntry : eventTimeBounds.entrySet()) {
			int time = eventBoundEntry.getKey().getTime() % periodLength;
			int lowerBound = eventBoundEntry.getValue().getFirstElement() % periodLength;
			int upperBound = eventBoundEntry.getValue().getSecondElement() % periodLength;
			if (lowerBound > time || time > upperBound) {
				feasible = false;
				logInfeasibleEvent(eventBoundEntry);
			}
		}
		return feasible;
	}

	private EanPostprocessor(Graph<PeriodicEvent, PeriodicActivity> ean, int periodLength) {
		this.ean = ean;
		this.periodLength = periodLength;
	}

	private PeriodicEvent findFixedEvent() {
		PeriodicEvent fixedEvent = ean.getNode(PeriodicEvent::getType, EventType.FIX);
		if (fixedEvent == null) {
			throw new LinTimException("Could not find fix event in ean, cannot do postprocessing!");
		}
		return fixedEvent;
	}

	private void applyTimeshift(int timeshift) {
		for (PeriodicEvent event : ean.getNodes()) {
			int newTime = PeriodicEanHelper.transformTimeToPeriodic(event.getTime() - timeshift, periodLength);
			event.setTime(newTime);
		}
	}

	private static void logInfeasibleEvent(Map.Entry<PeriodicEvent, Pair<Integer, Integer>> eventTimeBoundEntry) {
		logger.warn("Found infeasible time bound for event " + eventTimeBoundEntry.getKey() + " with " +
				"time " + eventTimeBoundEntry.getKey().getTime() + ", has " +
				"bounds (" + eventTimeBoundEntry.getValue().getFirstElement() + "," + eventTimeBoundEntry.getValue()
				.getSecondElement() + ").");
	}
}
