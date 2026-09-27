package net.lintim.util;

import net.lintim.algorithm.RoutingAlgorithm;
import net.lintim.algorithm.vehiclescheduling.IPModelGurobi;
import net.lintim.algorithm.vehiclescheduling.IPModelSolver;
import net.lintim.io.AperiodicEANWriter;
import net.lintim.io.Debug;
import net.lintim.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class GeneticAlgorithmData {

    private final Map<Integer, GeneticAlgorithmDataEntry> data;
    private Map<Integer, Set<Integer>> passengersByActivityId;
    private final Map<Integer, Double> travelTimeByPassenger;
    private final Map<Integer, Integer> changesByPassenger;
    private final Map<Integer, Integer> weightByPassenger;
    private final Map<Integer, ActivityInformation> activityInformationMap;
    private final Map<Integer, Integer> currentTimesForLineStartEvents;
    private final double[] linkLoadDistribution;
    private final double[] travelTimeDistribution;
    private final double[] odChangeDistribution;
    private final double[] averageDwellingSlack;
    private final double[] averageChangeSlack;
    private final double[] changeDistribution;
    private static double[] lineDistribution;
    private static double[] eventDistribution;
    private final int[] turnaroundDistribution;
    private final int[] waitsPerStation;
    private final int[] changesPerStation;
    private final static Map<Integer, Integer> stopIndices = new HashMap<>();
    private final static IPModelSolver solver = new IPModelGurobi();
    private final Graph<Stop, Link> ptn;
    private final OD od;
    private final Graph<AperiodicEvent, AperiodicActivity> aperiodicEan;
    private final Graph<PeriodicEvent, PeriodicActivity> periodicEan;
    private VehicleSchedule vs;
    private final static Collection<Trip> trips = new ArrayList<>();
    private final GeneticAlgorithmParameters parameters;
    private static Random rand;
    private final boolean debugOutput;

    private static final Logger logger = new Logger(GeneticAlgorithmData.class);

    public GeneticAlgorithmData(Graph<Stop, Link> ptn, Graph<PeriodicEvent, PeriodicActivity> periodicEan,
                                Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, OD od, LinePool lines,
                                Timetable<AperiodicEvent> timetable, Collection<Trip> trips, GeneticAlgorithmParameters parameters, boolean debugOutput) {
        data = new HashMap<>(aperiodicEan.getEdges().size());
        this.debugOutput = debugOutput;
        this.od = od;
        this.periodicEan = periodicEan;
        this.aperiodicEan = aperiodicEan;
        this.parameters = parameters;
        this.ptn = ptn;
        activityInformationMap = new HashMap<>();
        travelTimeByPassenger = new HashMap<>();
        changesByPassenger = new HashMap<>();
        weightByPassenger = new HashMap<>();
        // Find the line start events and set the original times
        // We handle line start events seperately, since those may be needed later on when determining a timetable
        // only from slacks. We need to handle events without incoming activities and this can only be the case
        // for line start events.
        currentTimesForLineStartEvents = new HashMap<>();
        List<AperiodicEvent> lineStartEvents = aperiodicEan.getNodes().stream()
            .filter(e -> aperiodicEan.getIncomingEdges(e).stream()
                .noneMatch(a -> a.getType() == ActivityType.DRIVE || a.getType() == ActivityType.WAIT))
            .collect(Collectors.toList());
        for (AperiodicEvent lineStartEvent: lineStartEvents) {
            currentTimesForLineStartEvents.put(lineStartEvent.getId(), timetable.get(lineStartEvent).intValue());
        }
        linkLoadDistribution = new double[101];
        travelTimeDistribution = new double[parameters.getMaxTravelTime()];
        odChangeDistribution = new double[parameters.getMaxChanges()];
        averageDwellingSlack = new double[ptn.getNodes().size()];
        averageChangeSlack = new double[ptn.getNodes().size()];
        changeDistribution = new double[ptn.getNodes().size()];
        turnaroundDistribution = new int[parameters.getMaxTurnAroundTime()];
        waitsPerStation = new int[ptn.getNodes().size()];
        changesPerStation = new int[ptn.getNodes().size()];
        // Initialization of the fixed parts of the data. These are not dependent of the current slack distribution or
        // the current routing
        int index;
        if (stopIndices.isEmpty()) {
            index = 0;
            for (Stop stop: ptn.getNodes()) {
                stopIndices.put(stop.getId(), index);
                index += 1;
            }
        }
        if (lineDistribution == null) {
            logger.debug("Initializing line distribution");
            lineDistribution = new double[ptn.getNodes().size()];
            Arrays.fill(lineDistribution, 0);
            int numberOfLines = lines.getLineConcept().size();
            for (Line line: lines.getLineConcept()) {
                for (Stop stop : line.getLinePath().getNodes()) {
                    lineDistribution[stopIndices.get(stop.getId())] += line.getFrequency() / (double) numberOfLines;
                }
            }
        }
        if (eventDistribution == null) {
            logger.debug("Initializing event distribution");
            eventDistribution = new double[ptn.getNodes().size()];
            Arrays.fill(eventDistribution, 0);
            int totalNumberOfEvents = 0;
            for (AperiodicEvent event: aperiodicEan.getNodes()) {
                totalNumberOfEvents += 1;
                eventDistribution[stopIndices.get(event.getStopId())] += 1;
            }
            for (int stopIndex = 0; stopIndex < ptn.getNodes().size(); stopIndex++) {
                eventDistribution[stopIndex] /= totalNumberOfEvents;
            }
        }
        if (GeneticAlgorithmData.trips.isEmpty()) {
            GeneticAlgorithmData.trips.addAll(trips);
        }
        // Apply the times to the aperiodic Ean and route the passengers
        timetable.forEach((e, t) -> e.setTime(t.intValue()));
        new AperiodicEANWriter.Builder(aperiodicEan).setEventFileName("delay-management/Events-expanded-ga.giv").setActivityFileName("delay-management/Activities-expanded-ga.giv").writeActivities(true).build().write();
        routePassengers();
    }

    public GeneticAlgorithmData(Graph<Stop, Link> ptn, Graph<PeriodicEvent, PeriodicActivity> periodicEan,
                                Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, OD od, LinePool lines,
                                Timetable<AperiodicEvent> timetable, Collection<Trip> trips, GeneticAlgorithmParameters parameters) {
        this(ptn, periodicEan, aperiodicEan, od, lines, timetable, trips, parameters, false);
    }

    private GeneticAlgorithmData(GeneticAlgorithmData parent) {
        this.debugOutput = false;
        this.passengersByActivityId = new HashMap<>(parent.passengersByActivityId);
        this.travelTimeByPassenger = new HashMap<>(parent.travelTimeByPassenger);
        this.changesByPassenger = new HashMap<>(parent.changesByPassenger);
        this.weightByPassenger = new HashMap<>(parent.weightByPassenger);
        this.currentTimesForLineStartEvents = new HashMap<>(parent.currentTimesForLineStartEvents);
        this.data = new HashMap<>();
        activityInformationMap = new HashMap<>();
        assert lineDistribution != null;
        assert eventDistribution != null;
        linkLoadDistribution = Arrays.copyOf(parent.linkLoadDistribution, 101);
        travelTimeDistribution = Arrays.copyOf(parent.travelTimeDistribution, parent.travelTimeDistribution.length);
        odChangeDistribution = Arrays.copyOf(parent.odChangeDistribution, parent.odChangeDistribution.length);
        averageDwellingSlack = Arrays.copyOf(parent.averageDwellingSlack, parent.averageDwellingSlack.length);
        averageChangeSlack = Arrays.copyOf(parent.averageChangeSlack, parent.averageChangeSlack.length);
        changeDistribution = Arrays.copyOf(parent.changeDistribution, parent.changeDistribution.length);
        turnaroundDistribution = Arrays.copyOf(parent.turnaroundDistribution, parent.turnaroundDistribution.length);
        waitsPerStation = Arrays.copyOf(parent.waitsPerStation, parent.waitsPerStation.length);
        changesPerStation = Arrays.copyOf(parent.changesPerStation, parent.changesPerStation.length);
        this.ptn = parent.ptn;
        this.od = parent.od;
        this.periodicEan = parent.periodicEan;
        this.aperiodicEan = parent.aperiodicEan;
        this.parameters = parent.parameters;
    }

    private void routePassengers() {
        RoutingAlgorithm routingAlgo = new RoutingAlgorithm(ptn, aperiodicEan, periodicEan, od, parameters);
        RoutingResult result = routingAlgo.routePassengers(false, true);
        this.passengersByActivityId = result.getPassengersByActivityId();
        data.clear();
        List<AperiodicActivity> sortedListOfActivities = aperiodicEan.getEdges().stream()
            .sorted(Comparator.comparingInt(
                AperiodicActivity::getId)
            )
            .collect(Collectors.toList());
        int slack;
        activityInformationMap.clear();
        // TODO: Only insert data for change activities that are used?
        for (AperiodicActivity activity: sortedListOfActivities) {
            slack = activity.getRightNode().getTime() - activity.getLeftNode().getTime() - activity.getLowerBound();
            data.put(activity.getId(), new GeneticAlgorithmDataEntry(slack, activity.getNumberOfPassengers()));
            activityInformationMap.put(activity.getId(), new ActivityInformation(activity.getLeftNode().getId(), activity.getLeftNode().getStopId()));
        }
        // DEBUG: Check for consistency of data and times
        Map<Integer, Integer> newTimes = computeNewEventTimes();
        for (Map.Entry<Integer, Integer> newTime: newTimes.entrySet()) {
            if (aperiodicEan.getNode(newTime.getKey()).getTime() != newTime.getValue()) {
                throw new RuntimeException("Found inconsistent times for " + aperiodicEan.getNode(newTime.getKey()).getTime());
            }
        }
        travelTimeByPassenger.clear();
        changesByPassenger.clear();
        weightByPassenger.clear();
        for (Passenger passenger: result.getPassengers().getSortedPassengersById()) {
            travelTimeByPassenger.put(passenger.getIndex(), passenger.getTravelTime());
            changesByPassenger.put(passenger.getIndex(), passenger.getChanges());
            weightByPassenger.put(passenger.getIndex(), passenger.getWeight());
        }
        if (debugOutput) {
            Debug.writePassengerData(result.getPassengers(), "ga");
            Debug.writeCapacitatedRouting(result.getPassengers(), result.getPassengersByEventId(), result.getPassengersByActivityId());
        }
        computeFixedNetworkData();
        computeVariableNetworkData();
    }

    public VehicleSchedule getVs() {
        return vs;
    }

    public void reroutePassengers() {
        readSolutionInEan();
        routePassengers();
    }

    public void readSolutionInEan() {
        Map<Integer, Integer> newEventTimes = computeNewEventTimes();
        newEventTimes.forEach((e, t) -> aperiodicEan.getNode(e).setTime(t));
    }

    public static GeneticAlgorithmData breed(GeneticAlgorithmData parent1, GeneticAlgorithmData parent2, GeneticAlgorithmParameters parameters) {
        GeneticAlgorithmData child = new GeneticAlgorithmData(parent1);
        if (rand == null && parameters.getSeed() > 0) {
            rand = new Random(parameters.getSeed());
        }
        else if (rand == null) {
            rand = new Random();
        }
        // Breed the two arrays. Passenger weights will only be used from parent1, to still maintain a feasible routing
        // Slack will be used randomly from one of the two parents
        int slack;
        GeneticAlgorithmDataEntry entryParent1;
        for (int i: parent1.data.keySet()) {
            entryParent1 = parent1.data.get(i);
            // It may be the case that parent 2 does not have this activity (may be an unused change activity)
            // Then we will always choose parent1. Otherwise, call random
            if (!parent2.data.containsKey(i) || rand.nextInt(2) == 0) {
                slack = entryParent1.getSlack();
            }
            else {
                slack = parent2.data.get(i).getSlack();
                // Change the travel time for all corresponding passengers
                if (!child.passengersByActivityId.containsKey(i)) {
                    continue;
                }
                for (int passenger: child.passengersByActivityId.get(i)) {
                    child.travelTimeByPassenger.put(passenger, child.travelTimeByPassenger.get(passenger) + (slack - entryParent1.getSlack()));
                }
            }
            child.data.put(i, new GeneticAlgorithmDataEntry(slack, entryParent1.getWeight()));
            child.activityInformationMap.put(i, new ActivityInformation(parent1.activityInformationMap.get(i)));
        }
        // Check if there are any activities only in parent2
        for (int i: parent2.data.keySet()) {
            if (!parent1.data.containsKey(i)) {
                child.data.put(i, new GeneticAlgorithmDataEntry(parent2.data.get(i).getSlack(), 0));
                child.activityInformationMap.put(i, parent2.activityInformationMap.get(i));
            }
        }
        child.mutateSelf(parameters.getNumberOfRandomMutationsBreeding(), parameters.getMaxMutationAmount());
        return child;
    }

    public GeneticAlgorithmData mutate(int numberOfMutations, int maxMutationAmount) {
        GeneticAlgorithmData newData = new GeneticAlgorithmData(this);
        for (int i: data.keySet()) {
            newData.data.put(i, new GeneticAlgorithmDataEntry(data.get(i).getSlack(), data.get(i).getWeight()));
            newData.activityInformationMap.put(i, this.activityInformationMap.get(i));
        }
        newData.mutateSelf(numberOfMutations, maxMutationAmount);
        return newData;
    }

    public double getTravelTime() {
        double weightedTravelTime = 0;
        double passengers = 0;
        for (int passenger: travelTimeByPassenger.keySet()) {
            weightedTravelTime += weightByPassenger.get(passenger) * (int) (travelTimeByPassenger.get(passenger) / Parameters.SECONDS_PER_MINUTE);
            passengers += weightByPassenger.get(passenger);
        }
        return weightedTravelTime/passengers;
    }

    private void mutateSelf(int numberOfMutations, int maxMutationAmount) {
        int entryToChange;
        int mutationAmount;
        int newSlack;
        int oldSlack;
        int activityIdToChange;
        GeneticAlgorithmDataEntry dataToChange;
        if (rand == null) {
            if (parameters.getSeed() > 0) {
                rand = new Random(parameters.getSeed());
            } else {
                rand = new Random();
            }
        }
        int countMutation = 0;
        List<Integer> activityIds = new ArrayList<>(activityInformationMap.keySet());
        for (int i = 0; i < numberOfMutations; i++) {
            // Choose randomly a data entry to mutate
            entryToChange = rand.nextInt(activityIds.size());
            activityIdToChange = activityIds.get(entryToChange);
            dataToChange = data.get(activityIdToChange);
            // Scale nextInt auf -parameters.getMaxMutationAmount(), ..., parameters.getMaxMutationAmount()
            mutationAmount = rand.nextInt(maxMutationAmount*2+1) - maxMutationAmount;
            newSlack = Math.max(0, dataToChange.getSlack() + mutationAmount);
            oldSlack = data.put(activityIdToChange, new GeneticAlgorithmDataEntry(newSlack, dataToChange.getWeight())).getSlack();
            countMutation += Math.abs(oldSlack - newSlack);
            // Change the travel time for all corresponding passengers
            if (passengersByActivityId.containsKey(activityIdToChange)) {
                for (int passenger : passengersByActivityId.get(activityIdToChange)) {
                    travelTimeByPassenger.put(passenger, travelTimeByPassenger.get(passenger) + (newSlack - oldSlack));
                }
            }
        }
        logger.debug("Mutated vector by " + countMutation);
        readSolutionInEan();
        computeVariableNetworkData();
    }

    public Map<Integer, Integer> computeNewEventTimes() {
        Map<Integer, Integer> newTimes = new HashMap<>();
        AperiodicEvent nextStartEvent, currentEvent, targetEvent;
        Queue<AperiodicEvent> eventQueue;
        while (true) {
            // Search for an event without incoming activity that was not handled yet
            nextStartEvent = aperiodicEan.getNodes().stream()
                .filter(e -> aperiodicEan.getIncomingEdges(e).stream().noneMatch(a -> a.getType() != ActivityType.CHANGE || data.containsKey(a.getId())))
                .filter(e -> !newTimes.containsKey(e.getId()))
                .findAny().orElse(null);
            if (nextStartEvent == null) {
                // We have handled all events, continue
                break;
            }
            newTimes.put(nextStartEvent.getId(), currentTimesForLineStartEvents.get(nextStartEvent.getId()));
            eventQueue = new LinkedList<>();
            eventQueue.add(nextStartEvent);
            int localStartTime, localEndTime;
            while (eventQueue.size() > 0) {
                currentEvent = eventQueue.poll();
                localStartTime = newTimes.get(currentEvent.getId());
                for (AperiodicActivity outgoingActivity: aperiodicEan.getOutgoingEdges(currentEvent)) {
                    // It may happen that outgoingActivity is not contained in data, when it is a change activity
                    // that is not used in data. Then we just skip here
                    if (outgoingActivity.getType() == ActivityType.CHANGE && !data.containsKey(outgoingActivity.getId())) {
                        continue;
                    }
                    else if (!data.containsKey(outgoingActivity.getId())) {
                        throw new RuntimeException("Activity " + outgoingActivity + " is not contained in data?");
                    }
                    localEndTime = localStartTime + outgoingActivity.getLowerBound() + data.get(outgoingActivity.getId()).getSlack();
                    targetEvent = outgoingActivity.getRightNode();
                    if (newTimes.containsKey(targetEvent.getId())) {
                        if (localEndTime > newTimes.get(targetEvent.getId())) {
                            // We already handled this event, but the new time is later. We therefore need to handle it
                            // and all later nodes again
                            newTimes.put(targetEvent.getId(), localEndTime);
                            eventQueue.add(targetEvent);
                            if (currentTimesForLineStartEvents.containsKey(targetEvent.getId())) {
                                currentTimesForLineStartEvents.put(targetEvent.getId(), localEndTime);
                            }
                        }
                        else {
                            // We already handled this event, but the new time is the same or earlier. Therefore we
                            // dont have to do anything here
                            continue;
                        }
                    }
                    else {
                        // We have not handled this event yet. Add the time and add to queue.
                        newTimes.put(targetEvent.getId(), localEndTime);
                        eventQueue.add(targetEvent);
                        if (currentTimesForLineStartEvents.containsKey(targetEvent.getId())) {
                            currentTimesForLineStartEvents.put(targetEvent.getId(), localEndTime);
                        }
                    }
                }
            }
        }
        return newTimes;
    }

    private void computeFixedNetworkData() {
        // Set all arrays to 0
        Arrays.fill(changeDistribution, 0);
        Arrays.fill(changesPerStation, 0);
        Arrays.fill(waitsPerStation, 0);
        Arrays.fill(linkLoadDistribution, 0);
        int numberOfDriveActivities = 0;
        int load;
        double activityWeight;
        AperiodicActivity activity;
        ActivityType type;
        // First, iterate all activities and fill the corresponding arrays
        for (Map.Entry<Integer, GeneticAlgorithmDataEntry> dataEntry: data.entrySet()) {
            if (!MLHelper.timeIsInRoutingInterval(parameters, aperiodicEan.getNode(activityInformationMap.get(dataEntry.getKey()).getLeftEventId()).getTime())) {
                continue;
            }
            activityWeight = dataEntry.getValue().getWeight();
            activity = aperiodicEan.getEdge(dataEntry.getKey());
            if (activity == null) {
                // We may have an unused change activity that was deleted from the ean. But that is the only
                // reason to delete activities later on, so we assume a change activity here
                type = ActivityType.CHANGE;
            }
            else {
                type = activity.getType();
            }
            switch (type) {
                case DRIVE:
                    load = (int) (activityWeight / parameters.getCapacity() * 100);
                    assert load <= 100;
                    linkLoadDistribution[load] += 1;
                    numberOfDriveActivities += 1;
                    break;
                case WAIT:
                    waitsPerStation[stopIndices.get(activityInformationMap.get(dataEntry.getKey()).getLeftStopId())] += 1;
                    break;
                case CHANGE:
                    if (activityWeight == 0) {
                        continue;
                    }
                    changeDistribution[stopIndices.get(activityInformationMap.get(dataEntry.getKey()).getLeftStopId())] += activityWeight;
                    changesPerStation[stopIndices.get(activityInformationMap.get(dataEntry.getKey()).getLeftStopId())] += activityWeight;
                    break;
            }
        }
        for (int i = 0; i < 101; i++) {
            linkLoadDistribution[i] /= numberOfDriveActivities;
        }
    }

    private void computeVariableNetworkData() {
        Arrays.fill(travelTimeDistribution, 0);
        Arrays.fill(odChangeDistribution, 0);
        Arrays.fill(averageDwellingSlack, 0);
        Arrays.fill(averageChangeSlack, 0);
        Arrays.fill(turnaroundDistribution, 0);
        // Update trips with current times from aperiodicEan
        for (Trip trip: trips) {
            if (aperiodicEan.getNode(trip.getEndAperiodicEventId()) == null) {
                throw new RuntimeException("Could not find " + trip.getEndAperiodicEventId() + " in ean, needed by " + trip);
            }
            if (aperiodicEan.getNode(trip.getStartAperiodicEventId()) == null) {
                throw new RuntimeException("Could not find " + trip.getStartAperiodicEventId() + " in ean, needed by " + trip);
            }
            trip.setEndTime(aperiodicEan.getNode(trip.getEndAperiodicEventId()).getTime());
            trip.setStartTime(aperiodicEan.getNode(trip.getStartAperiodicEventId()).getTime());
        }
        // Create trip graph and compute vehicle schedule
        vs = solver.solveVehicleSchedulingIPModel(ptn, trips, parameters);
        // Compute the turnaround slack values
        int slack, duration;
        for (Circulation circulation: vs.getCirculations()) {
            for (VehicleTour tour: circulation.getVehicleTourList()) {
                for (Trip currentTrip: tour.getTripList()) {
                    // only consider empty trips
                    if (currentTrip.getStartAperiodicEventId() == 1018 && currentTrip.getEndAperiodicEventId() == 2120) {
                        logger.debug("Found trip " + currentTrip + " between " + aperiodicEan.getNode(currentTrip.getStartAperiodicEventId()) + " and " + aperiodicEan.getNode(currentTrip.getEndAperiodicEventId()));
                        logger.debug("Current data entry: " + System.identityHashCode(this));
                    }
                    if (currentTrip.getTripType() == TripType.TRIP) {
                        continue;
                    }
                    // only consider trips in the routing interval
                    if (!MLHelper.timeIsInRoutingInterval(parameters, currentTrip.getStartTime())) {
                        continue;
                    }
                    duration = currentTrip.getEndTime() - currentTrip.getStartTime();
                    slack = (duration - parameters.getTurnoverTime()) / Parameters.SECONDS_PER_MINUTE;
                    slack = Math.min(parameters.getMaxTurnAroundTime() - 1, slack);
                    if (slack < 0) {
                        throw new RuntimeException("Found invalid vehicle schedule: Empty trip " + currentTrip + " is too short");
                    }
                    turnaroundDistribution[slack] += 1;
                }
                Trip lastService = tour.getTripList().get(tour.getTripList().size() - 1);
                if (lastService.getTripType() != TripType.TRIP) {
                    lastService = tour.getTripList().get(tour.getTripList().size() - 2);
                }
                int endTime = lastService.getEndTime();
                if (MLHelper.timeIsInRoutingInterval(parameters, endTime)) {
                    turnaroundDistribution[parameters.getMaxTurnAroundTime() - 1] += 1;
                }
            }
        }
        double activityWeight;
        int numberOfChanges = 0;
        AperiodicActivity activity;
        ActivityType type;
        // First, iterate all activities and fill the corresponding arrays
        for (Map.Entry<Integer, GeneticAlgorithmDataEntry> dataEntry: data.entrySet()) {
            if (!MLHelper.timeIsInRoutingInterval(parameters, aperiodicEan.getNode(activityInformationMap.get(dataEntry.getKey()).getLeftEventId()).getTime())) {
                continue;
            }
            activityWeight = dataEntry.getValue().getWeight();
            activity = aperiodicEan.getEdge(dataEntry.getKey());
            if (activity == null) {
                // We may have an unused change activity that was deleted from the ean. But that is the only
                // reason to delete activities later on, so we assume a change activity here
                type = ActivityType.CHANGE;
            }
            else {
                type = activity.getType();
            }
            switch (type) {
                case WAIT:
                    averageDwellingSlack[stopIndices.get(activityInformationMap.get(dataEntry.getKey()).getLeftStopId())]
                        += dataEntry.getValue().getSlack();
                    break;
                case CHANGE:
                    if (activityWeight == 0) {
                        continue;
                    }
                    numberOfChanges += activityWeight;
                    averageChangeSlack[stopIndices.get(activityInformationMap.get(dataEntry.getKey()).getLeftStopId())] += dataEntry.getValue().getSlack();
                    break;
            }
        }
        for (int i = 0; i < changeDistribution.length; i++) {
            if (waitsPerStation[i] > 0) {
                averageDwellingSlack[i] /= waitsPerStation[i];
            }
            changeDistribution[i] /= numberOfChanges;
            if (changesPerStation[i] > 0) {
                averageChangeSlack[i] /= changesPerStation[i];
            }
        }
        int travelTime;
        int changes;
        int totalNumberOfPassengers = weightByPassenger.values().stream().mapToInt(Integer::intValue).sum();
        for (int passenger = 0; passenger < travelTimeByPassenger.size(); passenger++) {
            travelTime = (int) (travelTimeByPassenger.get(passenger) / Parameters.SECONDS_PER_MINUTE);
            travelTime = Math.min(travelTime, parameters.getMaxTravelTime() - 1);
            travelTimeDistribution[travelTime] += (double) weightByPassenger.get(passenger)/totalNumberOfPassengers;
            changes = changesByPassenger.get(passenger);
            changes = Math.min(changes, parameters.getMaxChanges() - 1);
            odChangeDistribution[changes] += (double) weightByPassenger.get(passenger) / totalNumberOfPassengers;
        }
    }

    public double[] generateModelInput() {
        int resultLength = parameters.getMaxChanges() + parameters.getMaxTravelTime() +
            parameters.getMaxTurnAroundTime() + 101 + 5 * lineDistribution.length;
        double[] result = new double[resultLength];
        int count = 0;
        for (double v: linkLoadDistribution) {
            result[count] = v;
            count += 1;
        }
        for (double v: travelTimeDistribution) {
            result[count] = v;
            count += 1;
        }
        for (double v: odChangeDistribution) {
            result[count] = v;
            count += 1;
        }
        for (double v: averageDwellingSlack) {
            result[count] = v;
            count += 1;
        }
        for (double v: averageChangeSlack) {
            result[count] = v;
            count += 1;
        }
        for (double v: changeDistribution) {
            result[count] = v;
            count += 1;
        }
        for (double v: lineDistribution) {
            result[count] = v;
            count += 1;
        }
        for (double v: eventDistribution) {
            result[count] = v;
            count += 1;
        }
        for (int v: turnaroundDistribution) {
            result[count] = v;
            count += 1;
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("ML Data: \nTravel time distribution: (" + travelTimeDistribution.length + ")");
        buffer.append(Arrays.toString(travelTimeDistribution));
        buffer.append("\nOD Change Distribution: (" + odChangeDistribution.length + ")");
        buffer.append(Arrays.toString(odChangeDistribution));
        buffer.append("\nRelative link load: (" + linkLoadDistribution.length + ")");
        buffer.append(Arrays.toString(linkLoadDistribution));
        buffer.append("\nAverage dwelling slack: (" + averageDwellingSlack.length + ")");
        buffer.append(Arrays.toString(averageDwellingSlack));
        buffer.append("\nAverage change slack: (" + averageChangeSlack.length + ")");
        buffer.append(Arrays.toString(averageChangeSlack));
        buffer.append("\nChange distribution: (" + changeDistribution.length + ")");
        buffer.append(Arrays.toString(changeDistribution));
        buffer.append("\nEvent distribution: (" + eventDistribution.length + ")");
        buffer.append(Arrays.toString(eventDistribution));
        buffer.append("\nLine frequencies: (" + lineDistribution.length + ")");
        buffer.append(Arrays.toString(lineDistribution));
        buffer.append("\nTurnaround distribution: (" + turnaroundDistribution.length + ")");
        buffer.append(Arrays.toString(turnaroundDistribution));
        return buffer.toString();
    }


    private static class GeneticAlgorithmDataEntry {
        private final int slack;
        private final double weight;

        public GeneticAlgorithmDataEntry(int slack, double weight) {
            this.slack = slack;
            this.weight = weight;
        }

        public int getSlack() {
            return slack;
        }

        public double getWeight() {
            return weight;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GeneticAlgorithmDataEntry that = (GeneticAlgorithmDataEntry) o;
            return slack == that.slack && Double.compare(that.weight, weight) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(slack, weight);
        }

        @Override
        public String toString() {
            return "GeneticAlgorithmDataEntry{" +
                "slack=" + slack +
                ", weight=" + weight +
                '}';
        }
    }

    private static class ActivityInformation {
        private final int leftStopId;
        private final int leftEventId;

        public ActivityInformation(ActivityInformation other) {
            this.leftEventId = other.leftEventId;
            this.leftStopId = other.leftStopId;
        }

        public ActivityInformation(int leftEventId, int leftStopId) {
            this.leftStopId = leftStopId;
            this.leftEventId = leftEventId;
        }

        public int getLeftStopId() {
            return leftStopId;
        }

        public int getLeftEventId() {
            return leftEventId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ActivityInformation that = (ActivityInformation) o;
            return leftStopId == that.leftStopId && leftEventId == that.leftEventId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(leftStopId, leftEventId);
        }

        @Override
        public String toString() {
            return "ActivityInformation{" +
                "leftStopId=" + leftStopId +
                ", leftEventId=" + leftEventId +
                '}';
        }
    }


}
