package net.lintim.timetabling.algorithm;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.LinTimException;
import net.lintim.io.CsvWriter;
import net.lintim.model.*;
import net.lintim.model.impl.SimpleMapGraph;
import net.lintim.timetabling.model.RoutingEdge;
import net.lintim.timetabling.model.RoutingNode;
import net.lintim.timetabling.util.WalkingParameters;
import net.lintim.util.GraphHelper;
import net.lintim.util.Logger;
import net.lintim.util.Pair;
import net.lintim.util.PeriodicEanHelper;

import java.io.IOException;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class WalkingRouter {

    private final static Logger logger = new Logger(WalkingRouter.class.getCanonicalName());

    // Input data
    protected final Graph<PeriodicEvent, PeriodicActivity> ean;
    protected final OD od;
    protected final Graph<InfrastructureNode, WalkingEdge> walkingGraph;
    private final Graph<Stop, Link> ptn;
    protected final WalkingParameters parameters;

    // Local data
    protected final Graph<RoutingNode, RoutingEdge> routingGraph;
    private final Set<Integer> nodesWithOutgoingDemand;
    private final Set<Integer> nodesWithIncomingDemand;
    private final Map<Integer, Set<RoutingNode>> startNodesByNodeId;
    private final Map<Integer, RoutingNode> endNodeByNodeId;

    public WalkingRouter(Graph<PeriodicEvent, PeriodicActivity> ean, OD od,
                         Graph<InfrastructureNode, WalkingEdge> walkingGraph, Graph<Stop, Link> ptn,
                         WalkingParameters parameters) {
        this.ean = ean;
        this.od = od;
        this.walkingGraph = walkingGraph;
        this.ptn = ptn;
        this.parameters = parameters;
        this.routingGraph = new SimpleMapGraph<>();
        this.nodesWithOutgoingDemand = new HashSet<>();
        this.nodesWithIncomingDemand = new HashSet<>();
        this.startNodesByNodeId = new HashMap<>();
        this.endNodeByNodeId = new HashMap<>();
    }

    protected void extendEan() {
        // First, store all available change activities by event id pair, for faster check if they are present
        // The same for departure and arrival events per stop id
        HashSet<Pair<Integer, Integer>> availableChanges = new HashSet<>();
        for (PeriodicActivity activity: ean.getEdges()) {
            if (activity.getType() != ActivityType.CHANGE) {
                continue;
            }
            availableChanges.add(new Pair<>(activity.getLeftNode().getId(), activity.getRightNode().getId()));
        }
        Map<Integer, Collection<PeriodicEvent>> departuresPerStopId = new HashMap<>();
        Map<Integer, Collection<PeriodicEvent>> arrivalsPerStopId = new HashMap<>();
        // TODO: Can we use the computed Collections to speed up some process later on? Needs checking!
        for (PeriodicEvent event: ean.getNodes()) {
            if (event.getType() == EventType.ARRIVAL) {
                arrivalsPerStopId.computeIfAbsent(event.getStopId(), HashSet::new).add(event);
            }
            else {
                departuresPerStopId.computeIfAbsent(event.getStopId(), HashSet::new).add(event);
            }
        }
        // Is there a restriction in change stations? Add missing change activities for evaluation
        addMissingChanges(availableChanges, departuresPerStopId, arrivalsPerStopId);
        // Should we add walking change edges?
        addWalkingChanges(availableChanges, departuresPerStopId, arrivalsPerStopId);
    }

    protected void addMissingChanges(HashSet<Pair<Integer, Integer>> availableChanges,
                                     Map<Integer, Collection<PeriodicEvent>> departuresPerStopId,
                                     Map<Integer, Collection<PeriodicEvent>> arrivalsPerStopId) {
        int count = 0;
        int nextActivityId = GraphHelper.getMaxEdgeId(ean) + 1;
        for (Stop stop: ptn.getNodes()) {
            for (PeriodicEvent arrival: arrivalsPerStopId.get(stop.getId())) {
                for (PeriodicEvent departure: departuresPerStopId.get(stop.getId())) {
                    if (arrival.getLineId() == departure.getLineId() || availableChanges.contains(new Pair<>(arrival.getId(), departure.getId()))) {
                        continue;
                    }
                    ean.addEdge(new PeriodicActivity(nextActivityId, ActivityType.CHANGE, arrival, departure, parameters.getMinChangeTime(), parameters.getMaxChangeTime(), 0));
                    nextActivityId += 1;
                    count += 1;
                }
            }
        }
        logger.debug("Added " + count + " change activities without walking");
    }

    protected void addWalkingChanges(HashSet<Pair<Integer, Integer>> availableChanges,
                                     Map<Integer, Collection<PeriodicEvent>> departuresPerStopId,
                                     Map<Integer, Collection<PeriodicEvent>> arrivalsPerStopId) {
        // TODO: Implementation

    }

    protected void buildRoutingGraph() {
        logger.debug("Building routing graph");
        // NOTE: We will build a reverse routing graph, i.e., a graph containing every edge in the opposite direction
        // that a passenger should be allowed to travel. This allows for a much faster shortest path computation
        // later on.
        Map<Integer, Collection<RoutingNode>> departuresPerNodeId = new HashMap<>();
        Map<Integer, Collection<RoutingNode>> arrivalsPerNodeId = new HashMap<>();
        Map<Integer, RoutingNode> routingNodeByEventId = new HashMap<>();
        // First, fill in the ean
        addEanNodes(departuresPerNodeId, arrivalsPerNodeId, routingNodeByEventId);
        addEanEdges(routingNodeByEventId);
        // Precompute all nodes where we have incoming and outgoing demand. Only for those we need start/end nodes
        preprocessForDemandNodes();
        // Add the walking parts of the routing network
        addWalkingEdges(departuresPerNodeId, arrivalsPerNodeId);
        // Add waiting edges at the start nodes
        addWaitAtStartEdges();
    }

    private void addWaitAtStartEdges() {
        int nextEdgeId = routingGraph.getEdges().size() + 1;
        for (InfrastructureNode infrastructureNode: walkingGraph.getNodes()) {
            Set<RoutingNode> startNodes = startNodesByNodeId.get(infrastructureNode.getId());
            if (startNodes == null || startNodes.size() < 2) {
                continue;
            }
            List<RoutingNode> sortedStartNodes = startNodes.stream().sorted(Comparator.comparingInt(RoutingNode::getStartTime)).collect(Collectors.toList());
            Iterator<RoutingNode> it = sortedStartNodes.iterator();
            RoutingNode previous = it.hasNext()? it.next() : null;
            RoutingNode firstNode = previous;
            while (it.hasNext()) {
                RoutingNode current = it.next();
                RoutingEdge waitEdge = new RoutingEdge(nextEdgeId, current, previous,
                    current.getStartTime() - previous.getStartTime(), RoutingEdge.EdgeType.WAIT_AT_START, null);
                nextEdgeId += 1;
                routingGraph.addEdge(waitEdge);
                if (current.getStartTime() == previous.getStartTime()) {
                    waitEdge = new RoutingEdge(nextEdgeId, previous, current,
                        current.getStartTime() - previous.getStartTime(), RoutingEdge.EdgeType.WAIT_AT_START, null);
                    nextEdgeId += 1;
                    routingGraph.addEdge(waitEdge);
                }
                previous = current;
            }
            // Connect the last with the first start node
            RoutingEdge waitEdge = new RoutingEdge(nextEdgeId, firstNode, previous,
                PeriodicEanHelper.transformTimeToPeriodic(firstNode.getStartTime() - previous.getStartTime(),
                    parameters.getPeriodLength()),
                RoutingEdge.EdgeType.WAIT_AT_START, null);
            nextEdgeId += 1;
            routingGraph.addEdge(waitEdge);
        }
    }

    private void addWalkingEdges(Map<Integer, Collection<RoutingNode>> departuresPerNodeId,
                                 Map<Integer, Collection<RoutingNode>> arrivalsPerNodeId) {
        int nextNodeId = routingGraph.getNodes().size() + 1;
        int nextEdgeId = routingGraph.getEdges().size() + 1;
        for (InfrastructureNode node: walkingGraph.getNodes()) {
            if (nodesWithOutgoingDemand.contains(node.getId())) {
                startNodesByNodeId.put(node.getId(), new HashSet<>());
                for (WalkingEdge outgoingWalking: walkingGraph.getOutgoingEdges(node)) {
                    // See if we can depart there
                    InfrastructureNode departureNode = outgoingWalking.getLeftNode().equals(node) ? outgoingWalking.getRightNode() : outgoingWalking.getLeftNode();
                    if (!departuresPerNodeId.containsKey(departureNode.getId())) {
                        continue;
                    }
                    for (RoutingNode departure: departuresPerNodeId.get(departureNode.getId())) {
                        RoutingNode startNode = new RoutingNode(nextNodeId, node.getId(),
                            PeriodicEanHelper.transformTimeToPeriodic(departure.getStartTime() -
                                (int) outgoingWalking.getLength(), parameters.getPeriodLength()),
                            RoutingNode.NodeType.START);
                        boolean added = startNodesByNodeId.get(node.getId()).add(startNode);
                        nextNodeId += 1;
                        routingGraph.addNode(startNode);
                        routingGraph.addEdge(new RoutingEdge(nextEdgeId, departure, startNode, outgoingWalking.getLength(), RoutingEdge.EdgeType.WALK, null));
                        nextEdgeId += 1;
                    }
                }
            }
            if (nodesWithIncomingDemand.contains(node.getId())) {
                RoutingNode endNode = new RoutingNode(nextNodeId, node.getId(), -1, RoutingNode.NodeType.END);
                nextNodeId += 1;
                routingGraph.addNode(endNode);
                endNodeByNodeId.put(node.getId(), endNode);
                for (WalkingEdge incomingWalking: walkingGraph.getIncomingEdges(node)) {
                    // See if there is any arrival here
                    InfrastructureNode arrivalNode = incomingWalking.getRightNode().equals(node) ? incomingWalking.getLeftNode() : incomingWalking.getRightNode();
                    if (!arrivalsPerNodeId.containsKey(arrivalNode.getId())) {
                        continue;
                    }
                    for (RoutingNode arrival: arrivalsPerNodeId.get(arrivalNode.getId())) {
                        routingGraph.addEdge(new RoutingEdge(nextEdgeId, endNode, arrival, incomingWalking.getLength(), RoutingEdge.EdgeType.WALK, null));
                        nextEdgeId += 1;
                    }
                }
            }
        }
    }

    private void preprocessForDemandNodes() {
        logger.debug("Preprocess demand, have " + walkingGraph.getNodes().size() + " nodes");
        for (InfrastructureNode origin: walkingGraph.getNodes()) {
            boolean foundIncoming = false;
            boolean foundOutgoing = false;
            for (InfrastructureNode destination: walkingGraph.getNodes()) {
                if (!foundOutgoing && od.getValue(origin.getId(), destination.getId()) > 0) {
                    nodesWithOutgoingDemand.add(origin.getId());
                    foundOutgoing = true;
                }
                if (!foundIncoming && od.getValue(destination.getId(), origin.getId()) > 0) {
                    nodesWithIncomingDemand.add(origin.getId());
                    foundIncoming = true;
                }
                if (foundIncoming && foundOutgoing) {
                    break;
                }
            }
        }
    }

    private void addEanEdges(Map<Integer, RoutingNode> routingNodeByEventId) {
        int nextEdgeId = 1;
        for (PeriodicActivity activity: ean.getEdges()) {
            RoutingEdge.EdgeType type;
            if (activity.getType() == ActivityType.WAIT) {
                type = RoutingEdge.EdgeType.WAIT;
            }
            else if (activity.getType() == ActivityType.DRIVE) {
                type = RoutingEdge.EdgeType.DRIVE;
            }
            else if (activity.getType() == ActivityType.CHANGE) {
                type = RoutingEdge.EdgeType.CHANGE;
            }
            else {
                // Ignore all non-passenger edges
                continue;
            }
            double length = activity.getDuration(parameters.getPeriodLength());
            RoutingNode leftNode = routingNodeByEventId.get(activity.getLeftNode().getId());
            RoutingNode rightNode = routingNodeByEventId.get(activity.getRightNode().getId());
            RoutingEdge edge = new RoutingEdge(nextEdgeId, rightNode, leftNode, length, type, activity);
            nextEdgeId += 1;
            routingGraph.addEdge(edge);
        }
    }

    private void addEanNodes(Map<Integer, Collection<RoutingNode>> departuresPerNodeId, Map<Integer,
        Collection<RoutingNode>> arrivalsPerNodeId, Map<Integer, RoutingNode> routingNodeByEventId) {
        int nextNodeId = 1;
        for (PeriodicEvent event: ean.getNodes()) {
            int nodeId = Integer.parseInt(ptn.getNode(event.getStopId()).getLongName());
            if (!departuresPerNodeId.containsKey(nodeId)) {
                departuresPerNodeId.put(nodeId, new HashSet<>());
                arrivalsPerNodeId.put(nodeId, new HashSet<>());
            }
            RoutingNode.NodeType type = event.getType() == EventType.DEPARTURE ? RoutingNode.NodeType.DEPARTURE : RoutingNode.NodeType.ARRIVAL;
            RoutingNode routingNode = new RoutingNode(nextNodeId, nodeId, event.getTime(), type);
            nextNodeId += 1;
            routingGraph.addNode(routingNode);
            if (type == RoutingNode.NodeType.DEPARTURE) {
                departuresPerNodeId.get(nodeId).add(routingNode);
            }
            else {
                arrivalsPerNodeId.get(nodeId).add(routingNode);
            }
            routingNodeByEventId.put(event.getId(), routingNode);
        }
    }

    protected void routePassengers() throws IOException {
        logger.debug("Route passengers");
        ArrayList<RoutingNode> startNodes = new ArrayList<>();
        Dijkstra<RoutingNode, RoutingEdge, Graph<RoutingNode, RoutingEdge>> dijkstra;
        // Iterate once, compute the weights of all start nodes
        Map<RoutingNode, Double> weightsPerStartNode = new HashMap<>();
        for (InfrastructureNode originNode: walkingGraph.getNodes()) {
            if (!nodesWithIncomingDemand.contains(originNode.getId())) {
                continue;
            }
            startNodes.clear();
            // Add them all to array list. This allows easy access by index, while keeping the order of the treeset
            startNodes.addAll(startNodesByNodeId.get(originNode.getId()));
            if (startNodes.size() == 1) {
                weightsPerStartNode.put(startNodes.get(0), 1.);
            }
            else if (startNodes.size() >= 2) {
                weightsPerStartNode.put(startNodes.get(0),(parameters.getPeriodLength() - startNodes.get(startNodes.size() - 1).getStartTime() + startNodes.get(0).getStartTime()) / (1.0 * parameters.getPeriodLength()));
                for (int i = 1; i < startNodes.size(); i++) {
                    weightsPerStartNode.put(startNodes.get(i),(startNodes.get(i).getStartTime() - startNodes.get(i - 1).getStartTime()) / (1.0 * parameters.getPeriodLength()));
                }
            }
            else {
                // This should not happen in a valid graph!
                logger.error("Invalid ean and walking graph! There are no start nodes for origin node " + originNode.getId() + " but passenger want to depart there!");
                throw new LinTimException("Invalid ean and walking graph!");
            }
        }

        // Now, iterate all passengers and compute the new shortest paths
        int index = 0;
        Map<Pair<Integer, Integer>, List<Pair<Pair<Double, Double>, Pair<Integer, Integer>>>> travelTimes = new HashMap<>();
        // for each OD-pair (int, int) map to a list of "paths", represented as pairs of (passengers_on_this_path, length_of_the_path) and (stop id of boarding stop, stop id of alighting stop)
        for (InfrastructureNode destinationNode: walkingGraph.getNodes()) {
            if (!nodesWithIncomingDemand.contains(destinationNode.getId())) {
                continue;
            }
            index += 1;
            logger.debug("Compute paths for end node " + index + " of " + nodesWithIncomingDemand.size());
            // Now compute all shortest paths for the different start nodes
            RoutingNode endNode = endNodeByNodeId.get(destinationNode.getId());
            dijkstra = new Dijkstra<>(routingGraph, endNode, e -> e.getLength(parameters.getWalkingUtility(),
                parameters.getAdaptionUtility(), parameters.getChangePenalty(), parameters.getChangeUtility()));
            for (InfrastructureNode originNode: walkingGraph.getNodes()) {
                double demand = this.od.getValue(originNode.getId(), destinationNode.getId());
                if (demand == 0 || originNode.equals(destinationNode)) {
                    continue;
                }
                List<Pair<Pair<Double, Double>, Pair<Integer, Integer>>> travelTimeList = new ArrayList<>();
                for (RoutingNode startNode: startNodesByNodeId.get(originNode.getId())) {
                    double length = dijkstra.computeShortestPath(startNode);
                    double weight = weightsPerStartNode.get(startNode);
                    // Set the new weights
                    int firstArrivalNodeId = -1;
                    int lastDepartureNodeId = -1;
                    for (RoutingEdge edge : dijkstra.getPath(startNode).getEdges()) {
                        if (edge.getRightNode().getType() == RoutingNode.NodeType.ARRIVAL && firstArrivalNodeId == -1) {
                            firstArrivalNodeId = edge.getRightNode().getCorrespondingId();
                        }
                        else if (edge.getRightNode().getType() == RoutingNode.NodeType.DEPARTURE) {
                            lastDepartureNodeId = edge.getRightNode().getCorrespondingId();
                        }
                        edge.addWeight(weight * demand);
                    }
                    int finalFirstArrivalNodeId = firstArrivalNodeId;
                    int firstArrivalStopId = ptn.getNode((Stop s) -> s.getLongName().equals(String.valueOf(finalFirstArrivalNodeId)), true).getId();
                    int finalLastDepartureNodeId = lastDepartureNodeId;
                    int lastDepartureStopId = ptn.getNode((Stop s) -> s.getLongName().equals(String.valueOf(finalLastDepartureNodeId)), true).getId();
                    travelTimeList.add(new Pair<>(new Pair<>(weight * demand, length), new Pair<>(lastDepartureStopId, firstArrivalStopId)));
                }
                travelTimes.put(new Pair<>(originNode.getId(), destinationNode.getId()), travelTimeList);
            }
        }
        if (parameters.isOutputTravelTimes()) {
            CsvWriter travelTimeWriter = new CsvWriter(parameters.getTravelTimeOutputFileName(), parameters.getTravelTimeOutputHeader());
            List<Pair<Integer, Integer>> keys = new ArrayList<>(travelTimes.keySet());
            keys.sort(Comparator.comparingInt((ToIntFunction<Pair<Integer, Integer>>) Pair::getFirstElement).thenComparing(Pair::getSecondElement));
            // iterate over all origin destination node pairs (infrastructure nodes)
            for (Pair<Integer, Integer> key: keys) {
                List<Pair<Pair<Double, Double>, Pair<Integer, Integer>>> values = travelTimes.get(key);
                values.sort(Comparator.comparingDouble(p -> p.getFirstElement().getSecondElement()));
                double currentLength = -1;
                double currentDemand = 0;
                int currentDepartureStop = -1;
                int currentArrivalStop = -1;
                // iterate over all computed shortest paths for this OD-Node pair
                for (Pair<Pair<Double, Double>, Pair<Integer, Integer>> value: values) {
                    // If there are paths with equal length, just add their demand and output one single path
                    if (currentLength == value.getFirstElement().getSecondElement()) {
                        currentDemand += value.getFirstElement().getFirstElement();
                    }
                    else {
                        if (currentLength != -1) {
                            travelTimeWriter.writeLine(String.valueOf(key.getFirstElement()),
                                String.valueOf(key.getSecondElement()), String.valueOf(currentDepartureStop), String.valueOf(currentArrivalStop),
                                String.valueOf(currentDemand), String.valueOf(currentLength));
                        }
                        currentLength = value.getFirstElement().getSecondElement();
                        currentDemand = value.getFirstElement().getFirstElement();
                        currentDepartureStop = value.getSecondElement().getFirstElement();
                        currentArrivalStop = value.getSecondElement().getSecondElement();
                    }
                }
                travelTimeWriter.writeLine(String.valueOf(key.getFirstElement()),
                    String.valueOf(key.getSecondElement()), String.valueOf(currentDepartureStop), String.valueOf(currentArrivalStop),
                    String.format("%.2f", currentDemand), String.valueOf(currentLength));
            }
            travelTimeWriter.close();
        }
    }
}
