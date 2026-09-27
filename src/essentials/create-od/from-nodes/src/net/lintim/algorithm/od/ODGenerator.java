package net.lintim.algorithm.od;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.LinTimException;
import net.lintim.exception.OutputFileException;
import net.lintim.io.CsvWriter;
import net.lintim.model.*;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.model.impl.MapOD;
import net.lintim.model.od.RoutingEdge;
import net.lintim.model.od.RoutingNode;
import net.lintim.util.Config;
import net.lintim.util.od.EdgeWeightProcessor;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

import java.io.IOException;
import java.util.HashMap;

public class ODGenerator {

    private static Logger logger = new Logger(ODGenerator.class.getCanonicalName());

    public static OD generateOD(Graph<Stop, Link> ptn, Graph<InfrastructureNode, WalkingEdge> walkingGraph,
                                double waitingTime, EdgeWeightProcessor.DRIVE_WEIGHT driveModel, double walkingUtility,
                                OD nodeDemand, boolean writeAssignment) {
        CsvWriter assignmentWriter = null;
        if (writeAssignment) {
            assignmentWriter = new CsvWriter(Config.getStringValueStatic("filename_od_node_assignment_file"), Config.getStringValueStatic("od_nodes_assignment_header"));
        }
        // First, build a common routingGraph to route the passengers
        logger.debug("Create routing graph");
        Graph<RoutingNode, RoutingEdge> routingGraph = constructRoutingGraph(ptn, walkingGraph, waitingTime, driveModel, walkingUtility);
        logger.debug("Constructed routing graph with " + routingGraph.getNodes().size() + " nodes and " + routingGraph.getEdges().size() + " edges.");
        OD stopOd = new MapOD();
        logger.debug("Start routing");
        int index = 1;
        double missedDemand = 0;
        int missedODPairs = 0;
        int numberOfOdPairs = nodeDemand.getODPairs().size();
        for (RoutingNode origin: routingGraph.getNodes()) {
            Dijkstra<RoutingNode, RoutingEdge, Graph<RoutingNode, RoutingEdge>> dijkstra = null;
            if (origin.getType() != RoutingNode.NodeType.BOARD) {
                continue;
            }
            for (RoutingNode destination: routingGraph.getNodes()) {
                if (destination.getType() != RoutingNode.NodeType.ALIGHT) {
                    continue;
                }
                double demand = nodeDemand.getValue(origin.getOriginalNode().getId(), destination.getOriginalNode().getId());
                if (demand == 0) {
                    continue;
                }
                if (dijkstra == null) {
                    // Lazy initialization
                    dijkstra = new Dijkstra<>(routingGraph, origin, RoutingEdge::getLength);
                }
                double length = dijkstra.computeShortestPath(destination);
                if (length == Double.POSITIVE_INFINITY) {
//                    logger.error("Outgoing edges: ");
//                    for (RoutingEdge edge: routingGraph.getOutgoingEdges(origin)) {
//                        logger.error(edge.toString());
//                    }
//                    logger.error("Incoming at destination: ");
//                    for (RoutingEdge edge: routingGraph.getIncomingEdges(destination)) {
//                        logger.error(edge.toString());
//                    }
                    missedODPairs += 1;
                    missedDemand += demand;
                    logger.error("Found no connection between node " + origin.getOriginalNode().getId() + "(" + origin.getOriginalNode().getName() + ") and " + destination.getOriginalNode().getId() + "(" + destination.getOriginalNode().getName() + ")");
                    continue;
                    //throw new LinTimException("Found no connection between node " + origin.getOriginalNode().getId() + " and " + destination.getOriginalNode().getId());
                }
                Path<RoutingNode, RoutingEdge> sp = dijkstra.getPath(destination);
                if (sp.getEdges().size() == 1) {
                    // Passenger only walks, can ignore
                    continue;
                }
                InfrastructureNode boardingNode = sp.getNodes().get(1).getOriginalNode();
                InfrastructureNode alightingNode = sp.getNodes().get(sp.getNodes().size() - 2).getOriginalNode();
                // Get the corresponding stops
                Stop boardingStop = ptn.getNode(Stop::getLongName, String.valueOf(boardingNode.getId()));
                Stop alightingStop = ptn.getNode(Stop::getLongName, String.valueOf(alightingNode.getId()));
                if (boardingStop == alightingStop) {
                    // This may happen when the shortest path for a passenger is walking to a stop and walking directly
                    // from there to its destination. Just ignore those, we don't  want those in the od matrix
                    continue;
                }
                stopOd.setValue(boardingStop.getId(), alightingStop.getId(), stopOd.getValue(boardingStop.getId(), alightingStop.getId()) + demand);
                if (index % 10000 == 1) {
                    logger.debug("Processed od pair " + index + " of " + numberOfOdPairs);
                }
                index += 1;
                if (writeAssignment) {
                    try {
                        assignmentWriter.writeLine(String.valueOf(origin.getOriginalNode().getId()), String.valueOf(boardingStop.getId()),
                            String.valueOf(alightingStop.getId()), String.valueOf(destination.getOriginalNode().getId()), String.valueOf(demand));
                    } catch (IOException e) {
                        throw new OutputFileException(Config.getStringValueStatic("filename_od_node_assignment_file"));
                    }
                }
            }
        }
        logger.info("Missed " + missedODPairs + "od pairs with a demand sum of " + missedDemand);
        return stopOd;
    }


    private static Graph<RoutingNode, RoutingEdge> constructRoutingGraph(Graph<Stop, Link> ptn,
                                                                         Graph<InfrastructureNode,
                                                                             WalkingEdge> walkingGraph,
                                                                         double waitingTime,
                                                                         EdgeWeightProcessor.DRIVE_WEIGHT driveModel,
                                                                         double walkingUtility) {
        Graph<RoutingNode, RoutingEdge> routingGraph = new ArrayListGraph<>();
        int nextNodeId = 1;
        int nextEdgeId = 1;
        HashMap<InfrastructureNode, RoutingNode> boardingNodeByInfrastructure = new HashMap<>();
        HashMap<InfrastructureNode, RoutingNode> alightingNodeByInfrastructure = new HashMap<>();
        HashMap<InfrastructureNode, RoutingNode> departureNodeByInfrastructure = new HashMap<>();
        HashMap<InfrastructureNode, RoutingNode> arrivalNodeByInfrastructure = new HashMap<>();
        // First, create all boarding and alighting nodes for the infrastructure nodes
        for (InfrastructureNode infrastructureNode: walkingGraph.getNodes()) {
            RoutingNode boardingNode = new RoutingNode(nextNodeId, infrastructureNode, RoutingNode.NodeType.BOARD);
            routingGraph.addNode(boardingNode);
            nextNodeId += 1;
            boardingNodeByInfrastructure.put(infrastructureNode, boardingNode);
            RoutingNode alightingNode = new RoutingNode(nextNodeId, infrastructureNode, RoutingNode.NodeType.ALIGHT);
            routingGraph.addNode(alightingNode);
            nextNodeId += 1;
            alightingNodeByInfrastructure.put(infrastructureNode, alightingNode);
            routingGraph.addEdge(new RoutingEdge(nextEdgeId, boardingNode, alightingNode, 0, RoutingEdge.EdgeType.WALK));
            nextEdgeId += 1;
        }
        // Now, add departure and arrival nodes for all stops
        for (Stop stop: ptn.getNodes()) {
            // Get the corresponding infrastructure node
            InfrastructureNode infrastructureNode = walkingGraph.getNode(InfrastructureNode::getId, Integer.parseInt(stop.getLongName()));
            RoutingNode boardingNode = boardingNodeByInfrastructure.get(infrastructureNode);
            RoutingNode alightingNode = alightingNodeByInfrastructure.get(infrastructureNode);
            RoutingNode arrNode = new RoutingNode(nextNodeId, infrastructureNode, RoutingNode.NodeType.IN);
            routingGraph.addNode(arrNode);
            nextNodeId += 1;
            arrivalNodeByInfrastructure.put(infrastructureNode, arrNode);
            RoutingNode depNode = new RoutingNode(nextNodeId, infrastructureNode, RoutingNode.NodeType.OUT);
            routingGraph.addNode(depNode);
            nextNodeId += 1;
            departureNodeByInfrastructure.put(infrastructureNode, depNode);
            routingGraph.addEdge(new RoutingEdge(nextEdgeId, arrNode, depNode, waitingTime, RoutingEdge.EdgeType.WAIT));
            nextEdgeId += 1;
            routingGraph.addEdge(new RoutingEdge(nextEdgeId, depNode, alightingNode, 0, RoutingEdge.EdgeType.WALK));
            nextEdgeId += 1;
            routingGraph.addEdge(new RoutingEdge(nextEdgeId, boardingNode, arrNode, 0, RoutingEdge.EdgeType.WALK));
            nextEdgeId += 1;
            // Find all nodes where we can walk to, connect them
        }
        // Now, connect everything.
        // First, the walking edges. We can walk directly to a boarding node or to the corresponding departure nodes
        for (WalkingEdge edge: walkingGraph.getEdges()) {
            boolean output = false;
//            if (edge.getId() == 1343750) {
//                output = true;
//            }
            RoutingNode leftNode = boardingNodeByInfrastructure.get(edge.getLeftNode());
            RoutingNode rightNode = alightingNodeByInfrastructure.get(edge.getRightNode());
            routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftNode, rightNode, edge.getLength() * walkingUtility, RoutingEdge.EdgeType.WALK));
            nextEdgeId += 1;
            if (output) {
                logger.debug("Added edge from " + leftNode + " to " + rightNode);
            }
            if (arrivalNodeByInfrastructure.containsKey(edge.getRightNode())) {
                if (output) {
                    logger.debug("Have arrival at " + rightNode + ", add walking there");
                }
                rightNode = arrivalNodeByInfrastructure.get(edge.getRightNode());
                routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftNode, rightNode, edge.getLength() * walkingUtility, RoutingEdge.EdgeType.WALK));
                nextEdgeId += 1;
                if (output) {
                    logger.debug("From " + leftNode + " to " + rightNode);
                }
            }
            if (departureNodeByInfrastructure.containsKey(edge.getLeftNode())) {
                if (output) {
                    logger.debug("Have departure at " + leftNode + ", add walking there");
                }
                leftNode = departureNodeByInfrastructure.get(edge.getLeftNode());
                if (arrivalNodeByInfrastructure.containsKey(edge.getRightNode())) {
                    // we have both arrival and departure nodes, allow walking directly
                    routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftNode, rightNode, edge.getLength() * walkingUtility, RoutingEdge.EdgeType.WALK));
                    nextEdgeId += 1;
                    if (output) {
                        logger.debug("From " + leftNode + " to " + rightNode);
                    }
                }
                rightNode = alightingNodeByInfrastructure.get(edge.getRightNode());
                routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftNode, rightNode, edge.getLength() * walkingUtility, RoutingEdge.EdgeType.WALK));
                nextEdgeId += 1;
                if (output) {
                    logger.debug("From " + leftNode + " to " + rightNode);
                }
            }
            if (!edge.isDirected()) {
                leftNode = boardingNodeByInfrastructure.get(edge.getRightNode());
                rightNode = alightingNodeByInfrastructure.get(edge.getLeftNode());
                routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftNode, rightNode, edge.getLength() * walkingUtility, RoutingEdge.EdgeType.WALK));
                nextEdgeId += 1;
                if (output) {
                    logger.debug("Edge is not directed, add directly walking from " + leftNode + " to " + rightNode);
                }
                if (arrivalNodeByInfrastructure.containsKey(edge.getLeftNode())) {
                    if (output) {
                        logger.debug("Have arrival at " + rightNode + ", add walking there");
                    }
                    rightNode = arrivalNodeByInfrastructure.get(edge.getLeftNode());
                    routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftNode, rightNode, edge.getLength() * walkingUtility, RoutingEdge.EdgeType.WALK));
                    nextEdgeId += 1;
                    if (output) {
                        logger.debug("From " + leftNode + " to " + rightNode);
                    }
                }
                if (departureNodeByInfrastructure.containsKey(edge.getRightNode())) {
                    if (output) {
                        logger.debug("Have departure at " + leftNode + ", add walking there");
                    }
                    leftNode = departureNodeByInfrastructure.get(edge.getRightNode());
                    if (arrivalNodeByInfrastructure.containsKey(edge.getLeftNode())) {
                        // we have both arrival and departure nodes, allow walking directly
                        routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftNode, rightNode, edge.getLength() * walkingUtility, RoutingEdge.EdgeType.WALK));
                        nextEdgeId += 1;
                        if (output) {
                            logger.debug("From " + leftNode + " to " + rightNode);
                        }
                    }
                    rightNode = alightingNodeByInfrastructure.get(edge.getLeftNode());
                    routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftNode, rightNode, edge.getLength() * walkingUtility, RoutingEdge.EdgeType.WALK));
                    nextEdgeId += 1;
                    if (output) {
                        logger.debug("From " + leftNode + " to " + rightNode);
                    }
                }
            }
        }
        for (Link link: ptn.getEdges()) {
            InfrastructureNode leftNode = walkingGraph.getNode(InfrastructureNode::getId, Integer.parseInt(link.getLeftNode().getLongName()));
            InfrastructureNode rightNode = walkingGraph.getNode(InfrastructureNode::getId, Integer.parseInt(link.getRightNode().getLongName()));
            RoutingNode leftRoutingNode = routingGraph.getNode((RoutingNode n) -> n.getOriginalNode().equals(leftNode) && n.getType() == RoutingNode.NodeType.OUT, true);
            RoutingNode rightRoutingNode = routingGraph.getNode((RoutingNode n) -> n.getOriginalNode().equals(rightNode) && n.getType() == RoutingNode.NodeType.IN, true);
            routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftRoutingNode, rightRoutingNode, EdgeWeightProcessor.getDriveTime(link, driveModel), RoutingEdge.EdgeType.DRIVE));
            nextEdgeId += 1;
            if (!link.isDirected()) {
                leftRoutingNode = routingGraph.getNode((RoutingNode n) -> n.getOriginalNode().equals(rightNode) && n.getType() == RoutingNode.NodeType.OUT, true);
                rightRoutingNode = routingGraph.getNode((RoutingNode n) -> n.getOriginalNode().equals(leftNode) && n.getType() == RoutingNode.NodeType.IN, true);
                routingGraph.addEdge(new RoutingEdge(nextEdgeId, leftRoutingNode, rightRoutingNode, EdgeWeightProcessor.getDriveTime(link, driveModel), RoutingEdge.EdgeType.DRIVE));
                nextEdgeId += 1;
            }
        }
        return routingGraph;
    }
}
