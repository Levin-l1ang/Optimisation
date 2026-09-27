package net.lintim.util;

import net.lintim.algorithm.RoutingAlgorithm;
import net.lintim.exception.LinTimException;
import net.lintim.io.Debug;
import net.lintim.model.*;

import java.util.*;

public class LocalSearchData {

    private final static Logger logger = new Logger(LocalSearchData.class.getCanonicalName());

    private final Graph<AperiodicEvent, AperiodicActivity> aperiodicEan;
    private final Graph<Stop, Link> ptn;
    private final OD od;
    private final Graph<PeriodicEvent, PeriodicActivity> periodicEan;
    private final LinePool lines;
    private final VehicleSchedule vehicleSchedule;
    private final Parameters parameters;
    private final double[] linkLoadDistribution;
    private final double[] odTravelTimeDistribution;
    private final double[] odChangeDistribution;
    private final double[] averageDwellingSlack;
    private final double[] averageChangeSlack;
    private final double[] changeDistribution;
    private final double[] lineDistribution;
    private final double[] eventDistribution;
    private final int[] turnaroundDistribution;
    private final int[] waitsPerStation;
    private final int[] changesPerStation;
    private final Map<Integer, Integer> stopIndices;
    private Passengers passengers;
    private Map<Integer, Set<Integer>> passengerByEventId;
    private Map<Integer, Set<Integer>> passengerByActivityId;

    public LocalSearchData(Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, Graph<Stop, Link> ptn, OD od,
                           Graph<PeriodicEvent, PeriodicActivity> periodicEan, LinePool lines,
                           VehicleSchedule vehicleSchedule, Parameters parameters) {
        this.aperiodicEan = aperiodicEan;
        this.ptn = ptn;
        this.od = od;
        this.periodicEan = periodicEan;
        this.lines = lines;
        this.vehicleSchedule = vehicleSchedule;
        this.parameters = parameters;
        linkLoadDistribution = new double[101];
        odTravelTimeDistribution = new double[parameters.getMaxTravelTime()];
        odChangeDistribution = new double[parameters.getMaxChanges()];
        averageDwellingSlack = new double[ptn.getNodes().size()];
        averageChangeSlack = new double[ptn.getNodes().size()];
        changeDistribution = new double[ptn.getNodes().size()];
        lineDistribution = new double[ptn.getNodes().size()];
        eventDistribution = new double[ptn.getNodes().size()];
        turnaroundDistribution = new int[parameters.getMaxTurnAroundTime()];
        waitsPerStation = new int[ptn.getNodes().size()];
        changesPerStation = new int[ptn.getNodes().size()];
        stopIndices = new HashMap<>();
    }

    public Passengers initializeNetwork() {
        initializePassengerRouting();
        initializeIndexMaps();
        // Now evaluate the network
        logger.debug("Start evaluating network");
        recomputeAllNetworkData();
        logger.debug("Finished evaluation");
        return passengers;
    }

    private void initializeIndexMaps() {
        int index = 0;
        for (Stop stop: ptn.getNodes()) {
            this.stopIndices.put(stop.getId(), index);
            index += 1;
        }
    }

    public void recomputeAllNetworkData() {
        computeFixedNetworkData();
        computeVariableNetworkData();
    }

    /**
     * Compute the fixed network data. Fixed is all network data that is not directly dependent on the slacks of
     * activities, i.e., data that needs to be recomputed after rerouting but not after changing the slack values
     * of activities (without changing the routing)
     */
    public void computeFixedNetworkData() {
        Arrays.fill(changeDistribution, 0);
        Arrays.fill(eventDistribution, 0);
        Arrays.fill(lineDistribution, 0);
        Arrays.fill(linkLoadDistribution, 0);
        Arrays.fill(waitsPerStation, 0);
        Arrays.fill(changesPerStation, 0);
        int totalNumberOfEvents = 0;
        int totalNumberOfChanges = 0;
        int totalNumberOfDriveActivities = 0;
        for (AperiodicEvent event: aperiodicEan.getNodes()) {
            totalNumberOfEvents += 1;
            eventDistribution[stopIndices.get(event.getStopId())] += 1;
        }
        for (AperiodicActivity activity: aperiodicEan.getEdges()) {
            if (!MLHelper.timeIsInRoutingInterval(parameters, activity.getLeftNode().getTime())) {
                continue;
            }
            switch (activity.getType()) {
                case DRIVE:
                    int load = (int) (activity.getNumberOfPassengers() / parameters.getCapacity() * 100);
                    if (load > 100) {
                        logger.error("Activity " + activity + " violates the capacity constraints!");
                        throw new LinTimException("Invalid activity");
                    }
                    linkLoadDistribution[load] += 1;
                    totalNumberOfDriveActivities += 1;
                    break;
                case WAIT:
                    waitsPerStation[stopIndices.get(activity.getLeftNode().getStopId())] += 1;
                    break;
                case CHANGE:
                    if (activity.getNumberOfPassengers() == 0) {
                        continue;
                    }
                    changeDistribution[stopIndices.get(activity.getLeftNode().getStopId())] += activity.getNumberOfPassengers();
                    totalNumberOfChanges += activity.getNumberOfPassengers();
                    changesPerStation[stopIndices.get(activity.getLeftNode().getStopId())] += 1;
                    break;
            }
        }
        for (Line line: lines.getLineConcept()) {
            for (Stop stop: line.getLinePath().getNodes()) {
                lineDistribution[stopIndices.get(stop.getId())] += line.getFrequency();
            }
        }
        int numberOfLines = lines.getLineConcept().size();
        for (int stopIndex = 0; stopIndex < ptn.getNodes().size(); stopIndex++) {
            lineDistribution[stopIndex] /= numberOfLines;
            changeDistribution[stopIndex] /= totalNumberOfChanges;
            eventDistribution[stopIndex] /= totalNumberOfEvents;
        }
        if (totalNumberOfDriveActivities == 0) {
            throw new LinTimException("Found no drive activities in routing window. Please check the setting of the " +
                "parameters DM_earliest_time, DM_latest_time, rob_routing_start_time and rob_routing_end_time " +
                "and check the documentation for more details.");
        }
        for (int index = 0; index < 101; index++) {
            linkLoadDistribution[index] /= totalNumberOfDriveActivities;
        }
    }

    private void evaluateRouting() {
        Arrays.fill(odTravelTimeDistribution, 0);
        Arrays.fill(odChangeDistribution, 0);
        int totalNumberOfPassengers = 0;
        int travelTime, changes;
        for (Passenger passenger: passengers.getSortedPassengersById()) {
            travelTime = (int) (passenger.getTravelTime() / Parameters.SECONDS_PER_MINUTE);
            travelTime = Math.min(travelTime, parameters.getMaxTravelTime() - 1);
            odTravelTimeDistribution[travelTime] += passenger.getWeight();
            changes = passenger.getChanges();
            changes = Math.min(changes, parameters.getMaxChanges() - 1);
            odChangeDistribution[changes] += passenger.getWeight();
            totalNumberOfPassengers += passenger.getWeight();
        }
        for (int i = 0; i < odTravelTimeDistribution.length; i++) {
            odTravelTimeDistribution[i] /= totalNumberOfPassengers;
        }
        for (int i = 0; i < odChangeDistribution.length; i++) {
            odChangeDistribution[i] /= totalNumberOfPassengers;
        }
    }

    /**
     * Compute the variable network data. Network data is variable when it does not only depend on the routing
     * of the passengers but on the slacks of the activities as well.
     */
    public void computeVariableNetworkData() {
        evaluateRouting();
        Arrays.fill(averageDwellingSlack, 0);
        Arrays.fill(averageChangeSlack, 0);
        Arrays.fill(turnaroundDistribution, 0);
        int slack;
        for (AperiodicActivity activity: aperiodicEan.getEdges()) {
            if (!MLHelper.timeIsInRoutingInterval(parameters, activity.getLeftNode().getTime())) {
                continue;
            }
            switch (activity.getType()) {
                case WAIT:
                    slack = activity.getRightNode().getTime() - activity.getLeftNode().getTime() - activity.getLowerBound();
                    averageDwellingSlack[stopIndices.get(activity.getLeftNode().getStopId())] += slack;
                    break;
                case CHANGE:
                    if (activity.getNumberOfPassengers() == 0) {
                        continue;
                    }
                    slack = activity.getRightNode().getTime() - activity.getLeftNode().getTime() - activity.getLowerBound();
                    averageChangeSlack[stopIndices.get(activity.getLeftNode().getStopId())] += slack;
                    break;
                case TURNAROUND:
                    slack = (activity.getRightNode().getTime() - activity.getLeftNode().getTime() - activity.getLowerBound()) / Parameters.SECONDS_PER_MINUTE;
                    if (slack < 0) {
                        logger.debug("Left event time: " + activity.getLeftNode().getTime());
                        logger.debug("Right event time: " + activity.getRightNode().getTime());
                        logger.debug("Lower Bound: " + activity.getLowerBound());
                        logger.debug("Resulting slack: " + slack);
                        logger.debug("Have negative slack for activity " + activity);
                    }
                    slack = Math.min(slack, parameters.getMaxTurnAroundTime() - 1);
                    turnaroundDistribution[slack] += 1;
            }
        }
        // Find ending vehicles
        for (Circulation circulation: vehicleSchedule.getCirculations()) {
            for (VehicleTour tour: circulation.getVehicleTourList()) {
                Trip lastService = tour.getTripList().get(tour.getTripList().size() - 2);
                if (lastService.getTripType() != TripType.TRIP) {
                    lastService = tour.getTripList().get(tour.getTripList().size() - 1);
                }
                int endTime = aperiodicEan.getNode(lastService.getEndAperiodicEventId()).getTime();
                if (MLHelper.timeIsInRoutingInterval(parameters, endTime)) {
                    turnaroundDistribution[parameters.getMaxTurnAroundTime() - 1] += 1;
                }
            }
        }
        // Normalize all collected values
        for (int i = 0; i < ptn.getNodes().size(); i++) {
            if (waitsPerStation[i] > 0) {
                averageDwellingSlack[i] /= waitsPerStation[i];
            }
            if (changeDistribution[i] > 0) {
                averageChangeSlack[i] /= changesPerStation[i];
            }
        }
    }

    private void initializePassengerRouting() {
        RoutingResult result;
        if (parameters.readCapacitatedRouting()) {
            result = Debug.readCapacitatedRouting();
        }
        else {
            RoutingAlgorithm routingAlgo = new RoutingAlgorithm(ptn, aperiodicEan, periodicEan, od, parameters);
            result = routingAlgo.routePassengers(false, true);
        }
        passengers = result.getPassengers();
        passengerByEventId = result.getPassengersByEventId();
        passengerByActivityId = result.getPassengersByActivityId();
        setActivityWeights();
        if (parameters.writeCapacitatedRouting()) {
            Debug.writeCapacitatedRouting(passengers, passengerByEventId, passengerByActivityId);
        }
    }

    public Passengers reroutePassengers() {
        RoutingAlgorithm routingAlgo = new RoutingAlgorithm(ptn, aperiodicEan, periodicEan, od, parameters);
        RoutingResult result = routingAlgo.routePassengers(true, true);
        passengers = result.getPassengers();
        passengerByEventId = result.getPassengersByEventId();
        passengerByActivityId = result.getPassengersByActivityId();
        setActivityWeights();
        return passengers;
    }

    private void setActivityWeights() {
        int numberOfPassengers;
        for (AperiodicActivity activity: aperiodicEan.getEdges()) {
            if (passengerByActivityId.containsKey(activity.getId())) {
                numberOfPassengers = 0;
                for (int passengerIndex: passengerByActivityId.get(activity.getId())) {
                    numberOfPassengers += passengers.getPassenger(passengerIndex).getWeight();
                }
                activity.setNumberOfPassengers(numberOfPassengers);
            }
            else {
                activity.setNumberOfPassengers(0);
            }
        }
    }

    public double computeAverageTravelTime() {
        double sum = 0;
        for (int value = 0; value < odTravelTimeDistribution.length; value++) {
            sum += value * odTravelTimeDistribution[value];
        }
        return sum;
    }

    public double[] generateModelInput() {
        int lengthOfResult = parameters.getMaxChanges() + parameters.getMaxTravelTime() +
            parameters.getMaxTurnAroundTime() + 101 + 5 * ptn.getNodes().size();
        //logger.debug("Create vector of size " + lengthOfResult);
        double[] result = new double[lengthOfResult];
        int count = 0;
        for (double v : linkLoadDistribution) {
            result[count] = v;
            count += 1;
        }
        for (double v: odTravelTimeDistribution) {
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

    public double[] getLinkLoadDistribution() {
        return linkLoadDistribution;
    }

    public double[] getOdTravelTimeDistribution() {
        return odTravelTimeDistribution;
    }

    public double[] getOdChangeDistribution() {
        return odChangeDistribution;
    }

    public double[] getAverageDwellingSlack() {
        return averageDwellingSlack;
    }

    public double[] getAverageChangeSlack() {
        return averageChangeSlack;
    }

    public double[] getChangeDistribution() {
        return changeDistribution;
    }

    public double[] getLineDistribution() {
        return lineDistribution;
    }

    public double[] getEventDistribution() {
        return eventDistribution;
    }

    public int[] getTurnaroundDistribution() {
        return turnaroundDistribution;
    }

    public Map<Integer, Set<Integer>> getPassengersByEventId() {
        return passengerByEventId;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("ML Data: \nTravel time distribution: (" + odTravelTimeDistribution.length + ")");
        buffer.append(Arrays.toString(odTravelTimeDistribution));
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
}
