import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.util.*;

import java.util.*;
import java.util.function.Function;


public class CirculationsToEAN {
    private static final Logger logger = new Logger(CirculationsToEAN.class);
    private static final int SECONDS_PER_MINUTE = 60;

    private static HashMap<Pair<Integer, Integer>, Integer> computeDistances(Graph<Stop, Link> ptn, int timeUnitsPerMinute) {
        HashMap<Pair<Integer, Integer>, Integer> distances = new HashMap<>();
        Function<Link, Double> lengthFunction = link -> (double) link.getLowerBound() * SECONDS_PER_MINUTE / timeUnitsPerMinute;
        for (Stop origin : ptn.getNodes()) {
            Dijkstra<Stop, Link, Graph<Stop, Link>> dijkstra = new Dijkstra<>(ptn, origin, lengthFunction);
            dijkstra.computeShortestPaths();
            for (Stop destination : ptn.getNodes()) {
                distances.put(new Pair<>(origin.getId(), destination.getId()), (int) dijkstra.getDistance(destination));
            }
        }
        return distances;
    }


    public static void main(String[] args) {
        // This method seems to be much more complex than needed.
        // The main problem is that the rolled-out EAN used for
        // vehicle scheduling probably is different from the
        // rolled-out EAN used for delay management (as the time
        // horizon for vehicle scheduling for example is one day,
        // but only two hours for delay management). Hence, the
        // IDs of non-periodic events in both EANs are different,
        // so we use the combination of periodic ID and non-periodic
        // time to identify non-periodic events.
        logger.info("Begin reading configuration");
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        Config config = new ConfigReader.Builder(args[0]).build().read();
        int timeUnitsPerMinute = config.getIntegerValue("time_units_per_minute");
        int turnoverTime = config.getIntegerValue("vs_turn_over_time");
        logger.info("Finished reading configuration");
        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().build().read();
        Graph<AperiodicEvent, AperiodicActivity> ean = new AperiodicEANReader.Builder().build().read().getFirstElement();
        VehicleSchedule vs = new VehicleScheduleReader.Builder().build().read();
        logger.info("Finished reading input data");

        logger.info("Begin computing turnaround activities");
        // Compute the minimal durations to drive from one stop to another
        HashMap<Pair<Integer, Integer>, Integer> distances = computeDistances(ptn, timeUnitsPerMinute);

        // Store the aperiodic events by their periodic id for faster acces later
        HashMap<Integer, List<AperiodicEvent>> aperiodicEventsByPeriodicId = new HashMap<>();
        ean.getNodes().forEach(e -> aperiodicEventsByPeriodicId.computeIfAbsent(e.getPeriodicEventId(), ArrayList::new).add(e));

        // Now we can start iterating the vehicle schedule
        int nextActivityId = GraphHelper.getMaxEdgeId(ean) + 1;
        for (Circulation circulation : vs.getCirculations()) {
            for (VehicleTour tour : circulation.getVehicleTourList()) {
                for (Trip trip : tour.getTripList()) {
                    // only consider empty trips
                    if (trip.getTripType() != TripType.EMPTY) {
                        continue;
                    }
                    // only consider non-periodic empty trips, i.e., those empty
                    // trips for which the non-periodic time of the end event is
                    // after the non-periodic time of the start event
                    if (trip.getStartTime() > trip.getEndTime()) {
                        continue;
                    }
                    // find the aperiodic start event
                    AperiodicEvent startEvent = null;
                    for (AperiodicEvent candidate : aperiodicEventsByPeriodicId.get(trip.getStartPeriodicEventId())) {
                        if (candidate.getTime() == trip.getStartTime()) {
                            startEvent = candidate;
                            break;
                        }
                    }
                    if (startEvent == null) {
                        continue;
                    }
                    // find the aperiodic end event
                    AperiodicEvent endEvent = null;
                    for (AperiodicEvent candidate : aperiodicEventsByPeriodicId.get(trip.getEndPeriodicEventId())) {
                        if (candidate.getTime() == trip.getEndTime()) {
                            endEvent = candidate;
                            break;
                        }
                    }
                    if (endEvent == null) {
                        continue;
                    }
                    int minDuration = distances.get(new Pair<>(trip.getStartStopId(), trip.getEndStopId())) + turnoverTime;
                    if (trip.getEndTime() - trip.getStartTime() < minDuration) {
                        logger.warn("Neglecting trip " + trip + " since it does not fit to the minimal duration of " + minDuration);
                        continue;
                    }
                    ean.addEdge(new AperiodicActivity(nextActivityId, -1, ActivityType.TURNAROUND,
                        startEvent, endEvent, minDuration, minDuration, 0));
                    nextActivityId += 1;
                }
            }
        }

        logger.info("Finished computing turnaround activities");

        logger.info("Begin writing output data");
        new AperiodicEANWriter.Builder(ean).writeEvents(false).writeActivities(true).build().write();
        logger.info("Finished writing output data");

    }
}
