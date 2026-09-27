package net.lintim.algorithm;

import net.lintim.exception.LinTimException;
import net.lintim.model.*;
import net.lintim.util.*;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RoutingAlgorithm {

    private final Graph<Stop, Link> ptn;
    private final Graph<AperiodicEvent, AperiodicActivity> aperiodicEan;
    private final Graph<PeriodicEvent, PeriodicActivity> periodicEan;
    private final OD od;
    private final Parameters parameters;
    private final Map<Pair<Integer, Integer>, AperiodicActivity> activityMap;
    private final Map<Integer, Set<Integer>> passengersByActivityId;
    private final Map<Integer, Set<Integer>> passengersByEventId;
    private final Passengers passengers;
    private final Map<Integer, RoutingNode> routingNodesById;
    private final Map<Integer, RoutingNode> endNodesByStopId;
    private final Map<Integer, List<RoutingNode>> startNodesByStopId;
    private final Map<Integer, List<AperiodicEvent>> arrivalsByStopId;
    private final Map<Integer, List<AperiodicEvent>> departuresByStopId;
    private final SimpleDirectedWeightedGraph<RoutingNode, DefaultWeightedEdge> routingGraph;
    private final HashMap<Integer, Integer> lineIdByPeriodicEventId;
    private static final Logger logger = new Logger(RoutingAlgorithm.class.getCanonicalName());
    public static String debugIdentifier = "";

    public RoutingAlgorithm(Graph<Stop, Link> ptn, Graph<AperiodicEvent, AperiodicActivity> aperiodicEan, Graph<PeriodicEvent, PeriodicActivity> periodicEan, OD od, Parameters parameters) {
        this.ptn = ptn;
        this.aperiodicEan = aperiodicEan;
        this.periodicEan = periodicEan;
        this.od = od;
        this.parameters = parameters;
        this.activityMap = new HashMap<>();
        this.passengersByActivityId = new HashMap<>();
        this.passengersByEventId = new HashMap<>();
        this.passengers = new Passengers();
        this.routingNodesById = new HashMap<>();
        this.arrivalsByStopId = new HashMap<>();
        this.departuresByStopId = new HashMap<>();
        this.endNodesByStopId = new HashMap<>();
        this.startNodesByStopId = new HashMap<>();
        this.routingGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        this.lineIdByPeriodicEventId = new HashMap<>();
        for (PeriodicEvent periodicEvent: periodicEan.getNodes()) {
            lineIdByPeriodicEventId.put(periodicEvent.getId(), periodicEvent.getLineId());
        }
    }

    public RoutingResult routePassengers(boolean renewRouting, boolean removeUnusedChangeActivities) {
        logger.debug("Initialize routing data");
        // First, route all passengers to have consistent data
        int count = 0;
        for (AperiodicActivity activity: aperiodicEan.getEdges()) {
            if (activity.getType().equals(ActivityType.CHANGE) || activity.getType().equals(ActivityType.DRIVE) || activity.getType().equals(ActivityType.WAIT)) {
                activity.setNumberOfPassengers(0);
                if (activity.getType().equals(ActivityType.CHANGE)) {
                    continue;
                }
                activityMap.put(new Pair<>(activity.getLeftNode().getId(), activity.getRightNode().getId()), activity);
            }
        }
        logger.debug("Added " + count + " change activities to the activity map");
        if (renewRouting && routingGraph.vertexSet().size() != 0) {
            resetRoutingGraph();
        }
        else {
            buildRoutingGraph();
        }
        if (parameters.isUseGreedyRouting()) {
            computeGreedyRouting();
        }
        else {
            throw new RuntimeException("Non-Greedy Routing is currently not implemented");
        }
        if (removeUnusedChangeActivities) {
            count = 0;
            // Remove all unused change activities
            for (AperiodicActivity changeActivity : aperiodicEan.getEdges()) {
                if (changeActivity.getType().equals(ActivityType.CHANGE) && changeActivity.getNumberOfPassengers() == 0) {
                    aperiodicEan.removeEdge(changeActivity);
                    activityMap.remove(new Pair<>(changeActivity.getLeftNode().getId(), changeActivity.getRightNode().getId()));
                    count += 1;
                }
            }
            logger.debug("Removed " + count + " unused change activities");
        }
        logger.debug("Currently have " + aperiodicEan.getEdges().stream().filter(a -> a.getType().equals(ActivityType.CHANGE)).count() + " change activities");
        // DEBUG: Evaluate the routing
        double weightedTravelTime = 0;
        long numberChanges = 0;
        double weight = 0;
        for (Passenger passenger: passengers.getSortedPassengersById()) {
            weightedTravelTime += passenger.getTravelTime() * passenger.getWeight();
            numberChanges += passenger.getChanges() * passenger.getWeight();
            weight += passenger.getWeight();
        }
        logger.debug("Average travel time: " + weightedTravelTime / weight);
        logger.debug("Average changes: " + numberChanges / weight);
        return new RoutingResult(passengers, passengersByEventId, passengersByActivityId);
    }

    private void computeGreedyRouting() {
        createPassengers();
        routeGreedy();
    }

    private void routeGreedy() {
        logger.debug("Start greedy routing");
        BufferedWriter debugOutputWriter = null;
        if (parameters.writeDebugFiles()) {
            try {
                debugOutputWriter = new BufferedWriter(new FileWriter("lintim-passenger-routes_java_" + debugIdentifier + ".giv"));
            } catch (IOException e) {
                throw new LinTimException(e.getMessage());
            }
        }
        int i = 1;
        Map<Pair<RoutingNode, RoutingNode>, List<RoutingNode>> cachedPaths = new HashMap<>();
        DijkstraShortestPath<RoutingNode, DefaultWeightedEdge> spAlgo = new DijkstraShortestPath<>(routingGraph);
        RoutingNode startNodeTemp = routingNodesById.get(22494);
        RoutingNode endNodeTemp = routingNodesById.get(22540);
        DefaultWeightedEdge searchedEdge = routingGraph.getEdge(startNodeTemp, endNodeTemp);
        if (searchedEdge != null) {
            logger.debug("Searched edge has weight " + routingGraph.getEdgeWeight(searchedEdge));
        }
        for (DefaultWeightedEdge edge: routingGraph.edgeSet()) {
            if (edge == searchedEdge) {
                logger.debug("Found searched edge");
            }
            if (routingGraph.getEdgeWeight(edge) < 0) {
                throw new RuntimeException("Häh?");
            }
        }
        GraphPath<RoutingNode, DefaultWeightedEdge> jGraphTPath;
        for (Passenger passenger: passengers.getSortedPassengersByTime()) {
            if (i % 1000 == 1) {
                logger.debug("Passenger " + i + " of " + passengers.size());
            }
            i += 1;
            RoutingNode endNode = endNodesByStopId.get(passenger.getEndStopId());
            RoutingNode startNode = passenger.getStartNode();
            double length = -1;
            List<RoutingNode> shortestPath = cachedPaths.getOrDefault(new Pair<>(startNode, endNode), null);
            // Check if a potential cached shortest path is still feasible
            if (shortestPath != null) {
                boolean feasible = true;
                for (int pathIndex = 0; pathIndex < shortestPath.size() - 1; pathIndex++) {
                    RoutingNode leftNode = shortestPath.get(pathIndex);
                    RoutingNode rightNode = shortestPath.get(pathIndex + 1);
                    if (leftNode.isStart() || leftNode.getEvent() == null || rightNode.getEvent() == null) {
                        continue;
                    }
                    AperiodicActivity activity = activityMap.get(new Pair<>(leftNode.getEvent().getId(), rightNode.getEvent().getId()));
                    if (activity.getNumberOfPassengers() + passenger.getWeight() > parameters.getCapacity()) {
                        feasible = false;
                        break;
                    }
                }
                if (!feasible) {
                    shortestPath = null;
                }
            }
            // Do we have a feasible shortest path? Otherwise compute a new one
            if (shortestPath == null) {
                jGraphTPath = spAlgo.getPath(startNode, endNode);
                if (jGraphTPath == null) {
                    logger.error("Did not find a path between " + startNode + " and " + endNode);
                    throw new LinTimException("Could not find path!");
                }
                shortestPath = jGraphTPath.getVertexList();
                length = jGraphTPath.getWeight();
                for (RoutingNode node: shortestPath) {
                    if (node.isStart()) {
                        cachedPaths.put(new Pair<>(node, endNode), shortestPath);
                    }
                    else {
                        break;
                    }
                }
            }
            passenger.setNewPath(shortestPath);
            double duration = 0;
            int changes = 0;
            if (parameters.writeDebugFiles()) {
                try {
                    debugOutputWriter.write(passenger.getStartStopId() + ";" + passenger.getEndStopId() + ";" + passenger.getWeight() + ";" + passenger.getStartNode().getEvent().getTime());
                } catch (IOException e) {
                    throw new LinTimException(e.getMessage());
                }
            }
            for (int pathIndex = 0; pathIndex < shortestPath.size() - 1; pathIndex++) {
                RoutingNode leftNode = shortestPath.get(pathIndex);
                RoutingNode rightNode = shortestPath.get(pathIndex + 1);
                if (leftNode.isStart() || leftNode.getEvent() == null || rightNode.getEvent() == null) {
                    continue;
                }
                AperiodicActivity activity = activityMap.get(new Pair<>(leftNode.getEvent().getId(), rightNode.getEvent().getId()));
                if (parameters.writeDebugFiles()) {
                    try {
                        debugOutputWriter.write(";" + activity.getType() + " (" + activity.getLeftNode().getStopId() + "," + lineIdByPeriodicEventId.get(activity.getLeftNode().getPeriodicEventId()) + "," + activity.getLeftNode().getType() + "); (" + activity.getRightNode().getStopId() + "," + lineIdByPeriodicEventId.get(activity.getRightNode().getPeriodicEventId()) + "," + activity.getRightNode().getType() + ")");
                    } catch (IOException e) {
                        throw new LinTimException(e.getMessage());
                    }
                }
                activity.setNumberOfPassengers(activity.getNumberOfPassengers() + passenger.getWeight());
                // Remove edge if it is full
                if (activity.getNumberOfPassengers() == parameters.getCapacity()) {
                    routingGraph.removeEdge(leftNode, rightNode);

                }
                passengersByActivityId.get(activity.getId()).add(passenger.getIndex());
                passengersByEventId.get(leftNode.getEvent().getId()).add(passenger.getIndex());
                passengersByEventId.get(rightNode.getEvent().getId()).add(passenger.getIndex());
                if (activity.getType().equals(ActivityType.CHANGE)) {
                    changes += 1;
                    duration += parameters.getChangePenalty();
                }
                duration += activity.getRightNode().getTime() - activity.getLeftNode().getTime();
            }
            if (parameters.writeDebugFiles()) {
                try {
                    debugOutputWriter.write("\n");
                } catch (IOException e) {
                    throw new LinTimException(e.getMessage());
                }
            }
            passenger.setChanges(changes);
            passenger.setTravelTime(duration);
        }
        if (parameters.writeDebugFiles()) {
            try {
                debugOutputWriter.close();
            } catch (IOException e) {
                throw new LinTimException(e.getMessage());
            }
        }
    }

    private void createPassengers() {
        passengers.clear();
        int index = 0;
        BufferedWriter debugWriter = null;
        if (parameters.writeDebugFiles()) {
            try {
                debugWriter = new BufferedWriter(new FileWriter("uncapacitated_routing_start_java_" + debugIdentifier + ".giv"));
            } catch (IOException e) {
                throw new LinTimException(e.getMessage());
            }
        }
        logger.debug("Routing start time is " + parameters.getRoutingStartTime());
        for (Stop origin: ptn.getNodes()) {
            for (Stop destination: ptn.getNodes()) {
                double remainder = 1; // Emulate that the first passenger arrives at time 0
                double demand = od.getValue(origin.getId(), destination.getId());
                if (demand == 0) {
                    continue;
                }
                int currentTime = parameters.getRoutingStartTime();
                for (RoutingNode startNode: startNodesByStopId.get(origin.getId())) {
                    int startTime = startNode.getEvent().getTime();
                    if (startTime < currentTime) {
                        continue;
                    }
                    if (currentTime >= parameters.getRoutingEndTime() - 1) {
                        // we already handled the last second where someone can leave, break
                        break;
                    }
                    // If we are at or after routing_end_time, only consider the passengers that arrived before
                    // routing_end_time
                    startTime = Math.min(startTime, parameters.getRoutingEndTime()-1);
                    // Compute the interval in fractions of an hour
                    double time_interval = (startTime - currentTime) / 3600.0;
                    double currentDemand = demand * time_interval + remainder;
                    if (Math.abs(Math.round(currentDemand) - currentDemand) < 0.00001) {
                        currentDemand = Math.round(currentDemand);
                    }
                    int integerDemand = (int) currentDemand;
                    remainder = currentDemand - integerDemand;
                    currentTime = startTime;
                    while (integerDemand > 0) {
                        int localDemand = Math.min(integerDemand, parameters.getMaxGroupSize());
                        Passenger passenger = new Passenger(index, localDemand, null, origin.getId(), destination.getId(), startNode);
                        if (parameters.writeDebugFiles()) {
                            try {
                                debugWriter.write(origin.getId() + ";" + destination.getId() + ";" + startTime + "\n");
                            } catch (IOException e) {
                                throw new LinTimException(e.getMessage());
                            }
                        }
                        index += 1;
                        integerDemand -= localDemand;
                        passengers.addPassenger(passenger);
                    }
                }
            }
        }
        if (parameters.writeDebugFiles()) {
            try {
                debugWriter.close();
            } catch (IOException e) {
                throw new LinTimException(e.getMessage());
            }
        }
        logger.debug("Created " + passengers.size() + " passengers");
    }

    private void buildRoutingGraph() {
        logger.debug("Build routing graph");
        if (routingGraph.vertexSet().size() != 0) {
            throw new RuntimeException("Try to build routing graph, but graph is not empty!");
        }
        for (Stop stop: ptn.getNodes()) {
            RoutingNode endNode = new RoutingNode(stop.getId(), null, false);
            endNodesByStopId.put(stop.getId(), endNode);
            routingGraph.addVertex(endNode);
            startNodesByStopId.put(stop.getId(), new ArrayList<>());
            departuresByStopId.put(stop.getId(), new ArrayList<>());
            arrivalsByStopId.put(stop.getId(), new ArrayList<>());
        }
        for (AperiodicEvent event: aperiodicEan.getNodes()) {
            passengersByEventId.put(event.getId(), new HashSet<>());
            RoutingNode node = new RoutingNode(event.getStopId(), event, false);
            routingNodesById.put(event.getId(), node);
            routingGraph.addVertex(node);
            if (event.getType().equals(EventType.ARRIVAL)) {
                arrivalsByStopId.get(event.getStopId()).add(event);
                DefaultWeightedEdge edge = routingGraph.addEdge(node, endNodesByStopId.get(event.getStopId()));
                routingGraph.setEdgeWeight(edge, 0);
            }
            else {
                departuresByStopId.get(event.getStopId()).add(event);
                if (event.getTime() < parameters.getRoutingStartTime()
                    || event.getTime() > parameters.getRoutingEndTime() + parameters.getPeriodLength()) {
                    continue;
                }
                RoutingNode startNode = new RoutingNode(event.getStopId(), event, true);
                startNodesByStopId.get(event.getStopId()).add(startNode);
                routingGraph.addVertex(startNode);
                DefaultWeightedEdge edge = routingGraph.addEdge(startNode, node);
                routingGraph.setEdgeWeight(edge, 0);
            }
        }
        int nextActivityId = createOrUpdateActivities();
        if (parameters.createMissingChanges()) {
            createOrUpdateMissingChanges(nextActivityId);
        }
        createOrUpdateBoardingEdges();
    }

    private void createOrUpdateBoardingEdges() {
        // Connect all start nodes to later start nodes at the same stop
        RoutingNode firstNode, secondNode;
        List<RoutingNode> nodes;
        DefaultWeightedEdge edge;
        for (Stop stop: ptn.getNodes()) {
            // Sort the start nodes by departure time
            nodes = startNodesByStopId.get(stop.getId());
            nodes.sort(Comparator.comparingInt(r -> r.getEvent().getTime()));
            for (int firstIndex = 0; firstIndex < nodes.size()-1; firstIndex++) {
                firstNode = nodes.get(firstIndex);
                secondNode = nodes.get(firstIndex+1);
                edge = routingGraph.getEdge(firstNode, secondNode);
                if (edge == null) {
                    edge = routingGraph.addEdge(firstNode, secondNode);
                }
                // Add a little penalty for waiting in a station to encourage earlier leaving. This breaks the
                // symmetry for some shortest path decisions
                routingGraph.setEdgeWeight(edge, secondNode.getEvent().getTime() - firstNode.getEvent().getTime() + 0.0001);
                if (routingGraph.getEdgeWeight(edge) < 0) {
                    throw new RuntimeException("Created edge with negative weight between " + firstNode.getEvent() + " and " + secondNode.getEvent());
                }
            }
        }
    }

    private void resetRoutingGraph() {
        logger.debug("Reset routing graph");
        createOrUpdateBoardingEdges();
        int nextActivityId = createOrUpdateActivities();
        if (parameters.createMissingChanges()) {
            createOrUpdateMissingChanges(nextActivityId);
        }
        // Reset passengers by event id
        for (AperiodicEvent event: aperiodicEan.getNodes()) {
            passengersByEventId.get(event.getId()).clear();
        }
    }

    private int createOrUpdateActivities() {
        passengersByActivityId.clear();
        int nextActivityId = 0;
        DefaultWeightedEdge routingEdge;
        RoutingNode leftNode, rightNode;
        for (AperiodicActivity activity: aperiodicEan.getEdges()) {
            passengersByActivityId.put(activity.getId(), new HashSet<>());
            nextActivityId = Math.max(nextActivityId, activity.getId());
            if (parameters.createMissingChanges() && activity.getType().equals(ActivityType.CHANGE)) {
                continue;
            }
            if (activity.getType().equals(ActivityType.DRIVE) || activity.getType().equals(ActivityType.WAIT) || activity.getType().equals(ActivityType.CHANGE)) {
                leftNode = routingNodesById.get(activity.getLeftNode().getId());
                rightNode = routingNodesById.get(activity.getRightNode().getId());
                double duration = activity.getRightNode().getTime() - activity.getLeftNode().getTime();
                if (duration < 0) {
                    logger.debug("Found invalid activity " + activity);
                    throw new RuntimeException("Created edge with negative weight between " + activity.getLeftNode() + " and " + activity.getRightNode());
                }
                if (activity.getType().equals(ActivityType.CHANGE)) {
                    duration *= 0.99;
                    duration += parameters.getChangePenalty();
                }
                routingEdge = routingGraph.getEdge(leftNode, rightNode);
                if (routingEdge == null) {
                    routingEdge = routingGraph.addEdge(leftNode, rightNode);
                }
                routingGraph.setEdgeWeight(routingEdge, duration);
            }
        }
        return nextActivityId + 1;
    }

    private void createOrUpdateMissingChanges(int nextActivityId) {
        int count = 0;
        // First, remove all current changes
//        for (AperiodicActivity activity: aperiodicEan.getEdges()) {
//            if (activity.getType() == ActivityType.CHANGE) {
//                aperiodicEan.removeEdge(activity);
//                if (activity.getNumberOfPassengers() > 0) {
//                    throw new RuntimeException("Have passengers?");
//                }
//                activityMap.remove(new Pair<>(activity.getLeftNode().getId(), activity.getRightNode().getId()));
//                RoutingNode leftNode = routingNodesById.get(activity.getLeftNode().getId());
//                RoutingNode rightNode = routingNodesById.get(activity.getRightNode().getId());
//                DefaultWeightedEdge routingEdge = routingGraph.getEdge(leftNode, rightNode);
//                if (routingEdge != null) {
//                    routingGraph.removeEdge(routingEdge);
//                }
//                count += 1;
//            }
//        }
//        logger.debug("Removed " + count + " old change activities");
//        count = 0;
        for (Stop stop: ptn.getNodes()) {
            for (AperiodicEvent arrival: arrivalsByStopId.get(stop.getId())) {
                for (AperiodicEvent departure: departuresByStopId.get(stop.getId())) {
                    if (periodicEan.getNode(departure.getPeriodicEventId()).getLineId() == periodicEan.getNode(arrival.getPeriodicEventId()).getLineId()) {
                        continue;
                    }
//                    if (arrival.getTime() < parameters.getRoutingStartTime()) {
//                        continue;
//                    }
                    if (arrival.getTime() > departure.getTime() - parameters.getMinChangeTime()) {
                        continue;
                    }
                    if (activityMap.containsKey(new Pair<>(arrival.getId(), departure.getId()))) {
//                        throw new RuntimeException("Found old activity (" + arrival + "," + departure + ")");
                        continue;
                    }

                    int temp = -1;
//                    if (oldIds.containsKey(new Pair<>(arrival.getId(), departure.getId()))) {
//                        temp = nextActivityId;
//                        nextActivityId = oldIds.get(new Pair<>(arrival.getId(), departure.getId()));
//                    }
                    AperiodicActivity newChangeActivity = new AperiodicActivity(nextActivityId, -1, ActivityType.CHANGE, arrival, departure, parameters.getMinChangeTime(), parameters.getMaxChangeTime(), 0);
                    if (temp == -1) {
                        nextActivityId += 1;
                    }
                    else {
                        nextActivityId = temp;
                    }
//                    Double oldDuration = null;
//                    if (removedChanges.containsKey(new Pair<>(arrival.getId(), departure.getId()))) {
//                        oldDuration = removedChanges.remove(new Pair<>(arrival.getId(), departure.getId()));
//                        if (oldDuration == null) {
//                            throw new RuntimeException("Null old duration!");
//                        }
//                    }
                    boolean added = aperiodicEan.addEdge(newChangeActivity);
                    if (!added) {
                        throw new RuntimeException("Could not add change activity to ean!");
                    }
                    activityMap.put(new Pair<>(arrival.getId(), departure.getId()), newChangeActivity);
                    passengersByActivityId.put(newChangeActivity.getId(), new HashSet<>());
                    count += 1;
                    RoutingNode leftNode = routingNodesById.get(arrival.getId());
                    RoutingNode rightNode = routingNodesById.get(departure.getId());
                    double duration = 0.99 * (departure.getTime() - arrival.getTime()) + parameters.getChangePenalty();
                    if (duration < 0) {
                        throw new RuntimeException("Created edge with negative weight between " + arrival + " and " + departure);
                    }
//                    if (oldDuration != null) {
//                        if (Math.abs(duration - oldDuration) > 0.001) {
//                            logger.debug("Old duration " + oldDuration);
//                            logger.debug("New duration " + duration);
//                            logger.debug("Depature time: " + departure.getTime());
//                            logger.debug("Arrival time: " + arrival.getTime());
//                            throw new RuntimeException("Have different duration for activity " + newChangeActivity);
//                        }
//                    }
                    DefaultWeightedEdge routingEdge = routingGraph.getEdge(leftNode, rightNode);
                    if (routingEdge == null) {
                        routingEdge = routingGraph.addEdge(leftNode, rightNode);
                    }
                    else {
                        throw new RuntimeException("Found old activity");
                    }
                    routingGraph.setEdgeWeight(routingEdge, duration);
                }
            }
        }
//        logger.debug("Have " + removedChanges.size() + " remaining entries");
//        for (Pair<Integer, Integer> activity: removedChanges.keySet()) {
//            logger.debug("Activity (" + activity.getFirstElement() + ", " + activity.getSecondElement() + ")");
//            AperiodicEvent arrival = aperiodicEan.getNode(activity.getFirstElement());
//            AperiodicEvent departure = aperiodicEan.getNode(activity.getSecondElement());
//            if (periodicEan.getNode(departure.getPeriodicEventId()).getLineId() == periodicEan.getNode(arrival.getPeriodicEventId()).getLineId()) {
//                logger.debug("Have same line id");
//                continue;
//            }
//            if (arrival.getTime() < parameters.getRoutingStartTime()) {
//                logger.debug("Starts too early");
//                continue;
//            }
//            if (arrival.getTime() > departure.getTime() - parameters.getMinChangeTime()) {
//                logger.debug("Does not have enough time");
//                continue;
//            }
//            if (activityMap.containsKey(new Pair<>(arrival.getId(), departure.getId()))) {
//                logger.debug("Is already contained in activity map");
//                continue;
//            }
//        }
        logger.debug("Created " + count + " missing change activities");
    }
}
