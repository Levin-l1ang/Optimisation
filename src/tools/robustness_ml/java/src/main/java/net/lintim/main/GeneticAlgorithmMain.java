package net.lintim.main;

import net.lintim.algorithm.GeneticAlgorithm;
import net.lintim.exception.ConfigNoFileNameGivenException;
import net.lintim.io.*;
import net.lintim.model.*;
import net.lintim.model.impl.MapOD;
import net.lintim.util.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GeneticAlgorithmMain {
    private static final Logger logger = new Logger(GeneticAlgorithmMain.class.getCanonicalName());

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new ConfigNoFileNameGivenException();
        }
        logger.info("Begin reading configuration");
        Config config = new ConfigReader.Builder(args[0]).build().read();
        GeneticAlgorithmParameters parameters = new GeneticAlgorithmParameters(config);
        logger.info("Finished reading configuration");

        logger.info("Begin reading input data");
        Graph<Stop, Link> ptn = new PTNReader.Builder().setConfig(config).build().read();
        OD od = new ODReader.Builder(new MapOD()).setConfig(config).build().read();
        LinePool lines = new LineReader.Builder(ptn).readFrequencies(true).readCosts(false).setConfig(config).build().read();
        Graph<PeriodicEvent, PeriodicActivity> periodicEan = new PeriodicEANReader.Builder().setConfig(config).build().read().getFirstElement();
        Collection<Trip> trips = new TripReader.Builder().build().read();
        Graph<AperiodicEvent, AperiodicActivity> startAperiodicEan = new AperiodicEANReader.Builder().setConfig(config)
            .build().read().getFirstElement();
        for (AperiodicActivity activity: startAperiodicEan.getEdges()) {
            if (activity.getType().equals(ActivityType.TURNAROUND)) {
                startAperiodicEan.removeEdge(activity);
            }
            else if (activity.getType().equals(ActivityType.CHANGE)) {
                startAperiodicEan.removeEdge(activity);
            }
        }
        List<Graph<AperiodicEvent, AperiodicActivity>> listOfEans;
        listOfEans = ZipHelper.getRandomEans(parameters.getInputFile(), parameters.getNumberOfTimetablesToRead(), parameters.getSeed());
        for (Graph<AperiodicEvent, AperiodicActivity> ean: listOfEans) {
            for (AperiodicActivity activity: ean.getEdges()) {
                if (activity.getType().equals(ActivityType.TURNAROUND)) {
                    ean.removeEdge(activity);
                }
                else if (activity.getType().equals(ActivityType.CHANGE)) {
                    ean.removeEdge(activity);
                }
            }
        }
        parameters.setRoutingStartTime(Math.max(parameters.getRoutingStartTime(), ZipHelper.getHighestRoutingStartTime()));
        logger.info("Finished reading input data");

        logger.info("Begin executing genetic algorithm");
        logger.debug("Transform input eans to timetables");
        // Transform the different eans to Timetables
        ArrayList<Timetable<AperiodicEvent>> timetables = new ArrayList<>();
        // First, the start ean
        Timetable<AperiodicEvent> startTimetable = new Timetable<>(parameters.getTimeUnitsPerMinute());
        // Store the aperiodic events by their periodic id for faster access later
        HashMap<Integer, ArrayList<AperiodicEvent>> startAperiodicEventsByPeriodicId = new HashMap<>();
        for (AperiodicEvent event: startAperiodicEan.getNodes()) {
            startAperiodicEventsByPeriodicId.computeIfAbsent(event.getPeriodicEventId(), ArrayList::new).add(event);
            startTimetable.put(event, (long) event.getTime());
        }
        timetables.add(startTimetable);
        // First, find all line starts, there should `frequency` many in every (considered) hour
        Collection<AperiodicEvent> lineStartEvents = startAperiodicEan.getNodes().stream()
            .filter(e -> startAperiodicEan.getIncomingEdges(e).stream()
                    .noneMatch(a -> a.getType() == ActivityType.DRIVE || a.getType() == ActivityType.WAIT))
            .sorted(Comparator.comparingInt(AperiodicEvent::getTime))
            .collect(Collectors.toList());
        // There should be corresponding events to all of those start events, since the line plan is identical and the
        // lines need to start in every hour
        HashSet<AperiodicEvent> eventsToRemove = new HashSet<>();
        AperiodicEvent correspondingEvent, currentStartEvent;
        for (Graph<AperiodicEvent, AperiodicActivity> otherEan: listOfEans) {
            Timetable<AperiodicEvent> otherTimetable = new Timetable<>(parameters.getTimeUnitsPerMinute());
            timetables.add(otherTimetable);
            // Now: Iterate all those start event, follow the line trips and their corresponding line trips in the
            // other eans and set the times accordingly
            for (AperiodicEvent lineStartEvent: lineStartEvents) {
                correspondingEvent = otherEan.getNodes().stream().filter(e -> e.getPeriodicEventId() == lineStartEvent.getPeriodicEventId() && e.getTime() / Parameters.SECONDS_PER_HOUR == lineStartEvent.getTime() / Parameters.SECONDS_PER_HOUR)
                    .findAny().orElse(null);
                // It may be the case due to the rollout behaviour of LinTim that we do not find a corresponding
                // event for every otherEvent. But if this is the case the time of the otherEvent should be well outside
                // the routing time window
                if (correspondingEvent == null && lineStartEvent.getTime() >= parameters.getRoutingStartTime()
                    && lineStartEvent.getTime() <= parameters.getRoutingEndTime()) {
                    throw new RuntimeException("Found no corresponding event for " + lineStartEvent + " in the start aperiodic" +
                        "event");
                }
                else if (correspondingEvent == null) {
                    continue;
                }
                // Iterate both line trips together, add them to the timetable
                currentStartEvent = lineStartEvent;
                // Both lines ended or only one line ended but we reached the end of the routing interval
                do {
                    otherTimetable.put(currentStartEvent, (long) correspondingEvent.getTime());
                    // Find both next events
                    correspondingEvent = otherEan.getOutgoingEdges(correspondingEvent).stream().filter(a -> a.getType() == ActivityType.DRIVE || a.getType() == ActivityType.WAIT).findAny().map(AperiodicActivity::getRightNode).orElse(null);
                    currentStartEvent = startAperiodicEan.getOutgoingEdges(currentStartEvent).stream().filter(a -> a.getType() == ActivityType.DRIVE || a.getType() == ActivityType.WAIT).findAny().map(AperiodicActivity::getRightNode).orElse(null);
                    if (currentStartEvent != null && correspondingEvent == null && currentStartEvent.getTime() >= parameters.getRoutingStartTime()
                        && currentStartEvent.getTime() <= parameters.getRoutingEndTime()) {
                        throw new RuntimeException("Line in other ean ended earlier, could not find " + currentStartEvent);
                    }
                    if (correspondingEvent != null && currentStartEvent == null && correspondingEvent.getTime() >= parameters.getRoutingStartTime()
                        && correspondingEvent.getTime() <= parameters.getRoutingEndTime()) {
                        throw new RuntimeException("Line in start ean ended earlier, could not find " + correspondingEvent);
                    }
                } while (correspondingEvent != null && currentStartEvent != null);
            }
            // Now, check the other way around. Are there any events in the start ean that don't have corresponding
            // events in otherEan? If this is the case, remove the event from all timetables and startEan
            for (AperiodicEvent event: startTimetable.keySet()) {
                if (!otherTimetable.containsKey(event)) {
                    eventsToRemove.add(event);
                }
            }
        }
        // Remove all trips that start or end at one removed event
        List<Trip> tripsToRemove = new ArrayList<>();
        for (Trip trip: trips) {
            if (eventsToRemove.contains(startAperiodicEan.getNode(trip.getStartAperiodicEventId())) || eventsToRemove.contains(startAperiodicEan.getNode(trip.getEndAperiodicEventId()))) {
                tripsToRemove.add(trip);
            }
        }
        tripsToRemove.forEach(trips::remove);
        // Remove events that are not in every ean, we checked before that this only happens well outside the
        // routing window
        for (AperiodicEvent event: eventsToRemove) {
            startAperiodicEan.removeNode(event);
            for (Timetable<AperiodicEvent> timetable: timetables) {
                timetable.remove(event);
            }
        }
        // Check if all sets are equal
        HashSet<AperiodicEvent> startEanEvents = new HashSet<>(startAperiodicEan.getNodes());
        if (startEanEvents.size() != startAperiodicEan.getNodes().size()) {
            throw new RuntimeException("Don't have all events in the start ean");
        }
        for (Timetable<AperiodicEvent> timetable: timetables) {
            if (!startEanEvents.containsAll(timetable.keySet()) || startEanEvents.size() != timetable.size()) {
                throw new RuntimeException("Have unfitting timetables");
            }
        }
        GeneticAlgorithm.robustify(startAperiodicEan, ptn, od, periodicEan, lines, timetables, trips, parameters);
        logger.info("Finished executing genetic algorithm");

        logger.info("Begin writing output data");
        new AperiodicEANWriter.Builder(startAperiodicEan)
            .setConfig(config)
            .setEventFileName("delay-management/Events-expanded-robustified.giv")
            .setActivityFileName("delay-management/Activities-expanded-robustified.giv")
            .build().write();
        logger.info("Finished writing output data");


    }
}
