package net.lintim.util.timetabling;

import net.lintim.model.*;
import net.lintim.util.GraphHelper;
import net.lintim.util.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 */
public class RoutingNetworkBuilder {

	public static void enrichEan(Graph<PeriodicEvent, PeriodicActivity> ean, Graph<Stop, Link> ptn) {
		Logger logger = new Logger(RoutingNetworkBuilder.class.getCanonicalName());
		int nextEventId = GraphHelper.getMaxNodeId(ean) + 1;
		int nextActivityId = GraphHelper.getMaxEdgeId(ean) + 1;
		for (Stop stop : ptn.getNodes()) {
			Collection<PeriodicEvent> stopEvents =
					ean.getNodes().stream().filter(e -> e.getStopId() == stop.getId()).collect(Collectors.toCollection(HashSet::new));
			Collection<PeriodicEvent> departureEvents = stopEvents.stream()
					.filter(e -> e.getType() == EventType.DEPARTURE)
					.collect(Collectors.toCollection(HashSet::new));
			Collection<PeriodicEvent> arrivalEvents = stopEvents.stream()
					.filter(e -> e.getType() == EventType.ARRIVAL)
					.collect(Collectors.toCollection(HashSet::new));
			PeriodicEvent originEvent = new PeriodicEvent(nextEventId, stop.getId(), EventType.ORIGIN, -1, -1, 0,
					LineDirection.FORWARDS, 0);
			nextEventId += 1;
			ean.addNode(originEvent);
			for (PeriodicEvent departure : departureEvents) {
				PeriodicActivity odActivity = new PeriodicActivity(nextActivityId, ActivityType.OD, originEvent,
						departure, 0, 0, 0);
				nextActivityId += 1;
				ean.addEdge(odActivity);
			}
			PeriodicEvent destinationEvent = new PeriodicEvent(nextEventId, stop.getId(), EventType.DESTINATION, -1,
			-1, 0, LineDirection.FORWARDS, 0);
			nextEventId += 1;
			ean.addNode(destinationEvent);
			for (PeriodicEvent arrival : arrivalEvents) {
				PeriodicActivity odActivity = new PeriodicActivity(nextActivityId, ActivityType.OD, arrival,
				destinationEvent, 0, 0, 0);
				nextActivityId += 1;
				ean.addEdge(odActivity);
			}
		}
	}
}
