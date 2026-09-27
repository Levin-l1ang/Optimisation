package net.lintim.algorithm.timetabling;

import net.lintim.model.*;
import net.lintim.util.*;

import java.util.*;

/**
 * Class for preprocessing an ean for timetabling with fixed times. A new fixed event will be added, which is
 * connected with all events that should be fixed.
 */
public class EanPreProcessor {
	private Graph<PeriodicEvent, PeriodicActivity> ean;
	private Map<PeriodicEvent, Pair<Integer, Integer>> eventTimeBounds;
	private int periodLength;
	private static Logger logger = new Logger(EanPreProcessor.class.getCanonicalName());

	/**
	 * Preprocess the given ean with the given timebounds. We will add a new fix event, connect it with all events
	 * that have fixed times and update infeasible activities accordingly.
	 * @param ean the ean to change
	 * @param timeBounds the time bounds on events
	 * @param periodLength the period length
	 */
	public static void preprocessEan(Graph<PeriodicEvent, PeriodicActivity> ean, Map<PeriodicEvent, Pair<Integer,
			Integer>> timeBounds, int periodLength) {
		EanPreProcessor processor = new EanPreProcessor(ean, timeBounds, periodLength);
		processor.addEventTimeBounds();
		processor.updateInfeasibleActivities();
	}


	private EanPreProcessor(Graph<PeriodicEvent, PeriodicActivity> ean, Map<PeriodicEvent, Pair<Integer, Integer>>
			eventTimeBounds, int periodLength) {
		this.ean = ean;
		this.eventTimeBounds = eventTimeBounds;
		this.periodLength = periodLength;
	}

	private void addEventTimeBounds() {
		PeriodicEvent fixedEvent = new PeriodicEvent(GraphHelper.getMaxNodeId(ean) + 1, -1, EventType.FIX, -1, 0, 0,
				LineDirection.FORWARDS, 1);
		ean.addNode(fixedEvent);
		int nextEdgeId = GraphHelper.getMaxEdgeId(ean) + 1;
		List<Map.Entry<PeriodicEvent, Pair<Integer, Integer>>> timeBoundEntries =
				new ArrayList<>(eventTimeBounds.entrySet());
		timeBoundEntries.sort(Comparator.comparingInt(e -> e.getKey().getId()));
		for (Map.Entry<PeriodicEvent, Pair<Integer, Integer>> timeBoundEntry : timeBoundEntries) {
			ean.addEdge(new PeriodicActivity(nextEdgeId, ActivityType.SYNC, fixedEvent, timeBoundEntry.getKey(),
					timeBoundEntry.getValue().getFirstElement(), timeBoundEntry.getValue().getSecondElement(), 0));
			nextEdgeId += 1;
		}
	}

	private void updateInfeasibleActivities() {
	    // We may fix events to times such that some activities are infeasible (i.e. a longer wait then allowed).
        // If this happens, we update the corresponding activities, since the given fixed times should take priority
        // above generic time bounds on activities. But the log will contain all updates, allowing developers to
        // debug this if the behavior is unintended by them.
		for (Map.Entry<PeriodicEvent, Pair<Integer, Integer>> sourceEventEntry : eventTimeBounds.entrySet()) {
		    if (isNotFixedOnOneTime(sourceEventEntry)) {
		        continue;
            }
			for (Map.Entry<PeriodicEvent, Pair<Integer, Integer>> targetEventEntry : eventTimeBounds.entrySet()) {
				if (isNotFixedOnOneTime(targetEventEntry) || sourceEventEntry.equals(targetEventEntry)) {
					continue;
				}
				Optional<PeriodicActivity> activity = ean.getEdge(sourceEventEntry.getKey(), targetEventEntry.getKey());
				activity.ifPresent(this::updateActivity);
			}
		}
	}

	private boolean isNotFixedOnOneTime(Map.Entry<PeriodicEvent, Pair<Integer, Integer>> mapEntry) {
	    return !mapEntry.getValue().getFirstElement().equals(mapEntry.getValue().getSecondElement());
    }

	private void updateActivity(PeriodicActivity activity) {
		if(activity.getType() == ActivityType.CHANGE) {
			return;
		}
		Pair<Integer, Integer> eventTimeBound = getTimeBoundFromEvents(activity);
        setNewBounds(activity, eventTimeBound);
	}

	private Pair<Integer, Integer> getTimeBoundFromEvents(PeriodicActivity activity) {
		int lowerBound = eventTimeBounds.get(activity.getRightNode()).getFirstElement() - eventTimeBounds.get
				(activity.getLeftNode()).getSecondElement();
		int upperBound = eventTimeBounds.get(activity.getRightNode()).getSecondElement() - eventTimeBounds.get
				(activity.getLeftNode()).getFirstElement();
		return new Pair<>(PeriodicEanHelper.transformTimeToPeriodic(lowerBound, periodLength),
				PeriodicEanHelper.transformTimeToPeriodic(upperBound, periodLength));
	}

	private void setNewBounds(PeriodicActivity activity, Pair<Integer, Integer> bounds) {
		PeriodicActivity newActivity = new PeriodicActivity(activity.getId(), activity.getType(), activity
				.getLeftNode(), activity.getRightNode(), bounds.getFirstElement(), bounds.getSecondElement(),
				activity.getNumberOfPassengers());
		ean.removeEdge(activity);
		logReplacedActivity(activity, newActivity);
		ean.addEdge(newActivity);
	}

	private void logReplacedActivity(PeriodicActivity oldActivity, PeriodicActivity newActivity) {
		logger.debug(String.format("Replaced activity %d with new bounds (%f,%f), old were (%f,%f)" +
						"", oldActivity.getId(), newActivity.getLowerBound(), newActivity.getUpperBound(),
				oldActivity.getLowerBound(), oldActivity.getUpperBound()));
	}

}
