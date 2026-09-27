package net.lintim.algorithm.stop_location;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.LinTimException;
import net.lintim.model.*;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.model.stop_location.TTEdge;
import net.lintim.model.stop_location.TTNode;
import net.lintim.solver.*;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;
import net.lintim.util.stop_location.Parameters;

import java.util.*;
import java.util.stream.Collectors;

public class TravelTimeModel {

    private static final Logger logger = new Logger(TravelTimeModel.class.getCanonicalName());

    // Given data
    private final Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph;
    private final Graph<InfrastructureNode, WalkingEdge> walkingGraph;
    private final double waitTime;
    private final OD od;

    // Model data
    private Graph<TTNode, TTEdge> modelGraph;
    private final Solver solver;
    private Model model;
    private Map<InfrastructureNode, Double> outgoing;
    private final Map<InfrastructureNode, Map<TTEdge, Variable>> flow;
    private final Map<InfrastructureNode, Variable> stops;
    private final Map<InfrastructureNode, Map<TTEdge, Variable>> weightedWaitTime;


    private TravelTimeModel(Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph,
                            Graph<InfrastructureNode, WalkingEdge> walkingGraph, OD od, double waitTime,
                            SolverType solverType) {
        this.infrastructureGraph = infrastructureGraph;
        this.walkingGraph = walkingGraph;
        this.od = od;
        this.waitTime = waitTime;
        flow = new HashMap<>();
        stops = new HashMap<>();
        weightedWaitTime = new HashMap<>();
        solver = Solver.createSolver(solverType);
    }

    private void buildModelGraph() {
        modelGraph = new ArrayListGraph<>();
        int nodeId = 1;
        int edgeId = 1;
        Map<InfrastructureNode, List<TTNode>> newByOldNodes = new HashMap<>();
        for (InfrastructureNode node : infrastructureGraph.getNodes()) {
            newByOldNodes.put(node, new ArrayList<>());
            TTNode odNode = new TTNode(nodeId, TTNode.NodeType.OD, node);
            nodeId += 1;
            newByOldNodes.get(node).add(odNode);
            modelGraph.addNode(odNode);
            TTNode inNode = new TTNode(nodeId, TTNode.NodeType.IN, node);
            nodeId += 1;
            newByOldNodes.get(node).add(inNode);
            modelGraph.addNode(inNode);
            TTNode outNode = new TTNode(nodeId, TTNode.NodeType.OUT, node);
            nodeId += 1;
            newByOldNodes.get(node).add(outNode);
            modelGraph.addNode(outNode);
            TTEdge waitEdge = new TTEdge(edgeId, inNode, outNode, waitTime, TTEdge.EdgeType.WAIT);
            edgeId += 1;
            modelGraph.addEdge(waitEdge);
        }
        for (InfrastructureEdge edge : infrastructureGraph.getEdges()) {
            InfrastructureNode leftNode = edge.getLeftNode();
            InfrastructureNode rightNode = edge.getRightNode();
            TTNode left = newByOldNodes.get(leftNode).stream().filter(n -> n.getType() == TTNode.NodeType.OUT).findAny().orElseThrow(() -> new LinTimException("No outgoing node!"));
            TTNode right = newByOldNodes.get(rightNode).stream().filter(n -> n.getType() == TTNode.NodeType.IN).findAny().orElseThrow(() -> new LinTimException("No incoming node!"));
            TTEdge driveEdge = new TTEdge(edgeId, left, right, edge.getLowerBound(), TTEdge.EdgeType.DRIVE);
            edgeId += 1;
            modelGraph.addEdge(driveEdge);
            if (!infrastructureGraph.isDirected()) {
                left = newByOldNodes.get(rightNode).stream().filter(n -> n.getType() == TTNode.NodeType.OUT).findAny().orElseThrow(() -> new LinTimException("No outgoing node!"));
                right = newByOldNodes.get(leftNode).stream().filter(n -> n.getType() == TTNode.NodeType.IN).findAny().orElseThrow(() -> new LinTimException("No incoming node!"));
                driveEdge = new TTEdge(edgeId, left, right, edge.getLowerBound(), TTEdge.EdgeType.DRIVE);
                edgeId += 1;
                modelGraph.addEdge(driveEdge);
            }
        }
        for (WalkingEdge edge : walkingGraph.getEdges()) {
            InfrastructureNode leftNode = edge.getLeftNode();
            InfrastructureNode rightNode = edge.getRightNode();
            TTNode left = newByOldNodes.get(leftNode).stream().filter(n -> n.getType() == TTNode.NodeType.OD).findAny().orElseThrow(() -> new LinTimException("No od node!"));
            TTNode right = newByOldNodes.get(rightNode).stream().filter(n -> n.getType() == TTNode.NodeType.OUT).findAny().orElseThrow(() -> new LinTimException("No incoming node!"));
            TTEdge walkEdge = new TTEdge(edgeId, left, right, edge.getLength(), TTEdge.EdgeType.WALK);
            edgeId += 1;
            modelGraph.addEdge(walkEdge);
            left = newByOldNodes.get(leftNode).stream().filter(n -> n.getType() == TTNode.NodeType.IN).findAny().orElseThrow(() -> new LinTimException("No od node!"));
            right = newByOldNodes.get(rightNode).stream().filter(n -> n.getType() == TTNode.NodeType.OD).findAny().orElseThrow(() -> new LinTimException("No incoming node!"));
            walkEdge = new TTEdge(edgeId, left, right, edge.getLength(), TTEdge.EdgeType.WALK);
            edgeId += 1;
            modelGraph.addEdge(walkEdge);
            if (!infrastructureGraph.isDirected()) {
                left = newByOldNodes.get(rightNode).stream().filter(n -> n.getType() == TTNode.NodeType.OD).findAny().orElseThrow(() -> new LinTimException("No od node!"));
                right = newByOldNodes.get(leftNode).stream().filter(n -> n.getType() == TTNode.NodeType.OUT).findAny().orElseThrow(() -> new LinTimException("No incoming node!"));
                walkEdge = new TTEdge(edgeId, left, right, edge.getLength(), TTEdge.EdgeType.WALK);
                edgeId += 1;
                modelGraph.addEdge(walkEdge);
                left = newByOldNodes.get(rightNode).stream().filter(n -> n.getType() == TTNode.NodeType.IN).findAny().orElseThrow(() -> new LinTimException("No od node!"));
                right = newByOldNodes.get(leftNode).stream().filter(n -> n.getType() == TTNode.NodeType.OD).findAny().orElseThrow(() -> new LinTimException("No incoming node!"));
                walkEdge = new TTEdge(edgeId, left, right, edge.getLength(), TTEdge.EdgeType.WALK);
                edgeId += 1;
                modelGraph.addEdge(walkEdge);
            }
        }
    }


    private void buildModel(double costOfStop) {
        model = solver.createModel();
        logger.debug("Compute outgoing flow");
        computeOutgoingFlowPerODNode();
        logger.debug("Creating variables");
        createVariables(costOfStop);
        logger.debug("Creating constraints");
        createConstraints();
    }

    private void computeOutgoingFlowPerODNode() {
        outgoing = new HashMap<>();
        for (InfrastructureNode origin: infrastructureGraph.getNodes()) {
            double outgoingFlow = 0;
            for (InfrastructureNode destination: infrastructureGraph.getNodes()) {
                if (origin.equals(destination)) {
                    continue;
                }
                outgoingFlow += od.getValue(origin.getId(), destination.getId());
            }
            outgoing.put(origin, outgoingFlow);
        }
    }

    private void createVariables(double costOfStop) {
        LinearExpression expression = model.createExpression();
        Variable stopVariable;
        for (InfrastructureNode origin: infrastructureGraph.getNodes()) {
            if (outgoing.get(origin) > 0) {
                flow.put(origin, new HashMap<>());
                weightedWaitTime.put(origin, new HashMap<>());
                for (TTEdge edge : modelGraph.getEdges()) {
                    // Skip walking edges starting somewhere else as origin
                    if (edge.getType() == TTEdge.EdgeType.WALK && edge.getLeftNode().getType() == TTNode.NodeType.OD && !edge.getLeftNode().getOriginalNode().equals(origin)) {
                        continue;
                    }
                    flow.get(origin).put(edge, model.addVariable(0, Double.POSITIVE_INFINITY, Variable.VariableType.CONTINUOUS, edge.getLength(), "flow_" + origin.getId() + "_" + edge));
                    // Process wait edges
                    if (edge.getType() == TTEdge.EdgeType.WAIT) {
                        weightedWaitTime.get(origin).put(edge, model.addVariable(0, Double.POSITIVE_INFINITY, Variable.VariableType.CONTINUOUS, 1, "wait" + origin.getId() + "_" + edge));
                    }
                }

            }
            stopVariable = model.addVariable(0, 1, Variable.VariableType.BINARY, costOfStop, "stop_" + origin.getId());
            stops.put(origin, stopVariable);
            if (!origin.isStopPossible()) {
                expression.clear();
                expression.multiAdd(1, stopVariable);
                model.addConstraint(expression, Constraint.ConstraintSense.EQUAL, 0, "stop_forbidden_" + origin.getId());
            }
        }
    }

    private void createConstraints() {
        logger.debug("Create flow constraints");
        createFlowConstraints();
        logger.debug("Create walk constraints");
        createWalkConstraints();
        logger.debug("Create wait constraints");
        createWaitConstraints();
    }

    private void createFlowConstraints() {
        LinearExpression expression = model.createExpression();
        for (InfrastructureNode origin: infrastructureGraph.getNodes()) {
            if (outgoing.get(origin) == 0) {
                continue;
            }
            for (TTNode node : modelGraph.getNodes()) {
                expression.clear();
                for (TTEdge edge: modelGraph.getIncomingEdges(node)) {
                    if (!flow.get(origin).containsKey(edge)) {
                        continue;
                    }
                    expression.multiAdd(1, flow.get(origin).get(edge));
                }
                for (TTEdge edge: modelGraph.getOutgoingEdges(node)) {
                    if (!flow.get(origin).containsKey(edge)) {
                        continue;
                    }
                    expression.multiAdd(-1, flow.get(origin).get(edge));
                }
                double rhs = 0;
                if (node.getType() == TTNode.NodeType.OD) {
                    if (node.getOriginalNode().equals(origin)) {
                        rhs = - 1 * outgoing.get(origin);
                    }
                    else {
                        rhs = od.getValue(origin.getId(), node.getOriginalNode().getId());
                    }
                }
                model.addConstraint(expression, Constraint.ConstraintSense.EQUAL, rhs, "flow_conservation_" + origin.getId() + "_" + node);
            }
        }
    }

    private void createWalkConstraints() {
        LinearExpression expression = model.createExpression();
        for (InfrastructureNode origin: infrastructureGraph.getNodes()) {
            if (outgoing.get(origin) == 0) {
                continue;
            }
            for (TTEdge edge: modelGraph.getEdges()) {
                if (!flow.get(origin).containsKey(edge)) {
                    continue;
                }
                if (edge.getType() == TTEdge.EdgeType.WALK) {
                    InfrastructureNode stopNode = edge.getLeftNode().getType() == TTNode.NodeType.OD ? edge.getRightNode().getOriginalNode() : edge.getLeftNode().getOriginalNode();
                    expression.clear();
                    expression.multiAdd(1, flow.get(origin).get(edge));
                    expression.multiAdd(-1 * outgoing.get(origin), stops.get(stopNode));
                    model.addConstraint(expression, Constraint.ConstraintSense.LESS_EQUAL, 0, "origin_" + origin.getId() + "_can_walk_to_" + stopNode.getId() + "_when_used");
                }
            }
        }
    }

    private void createWaitConstraints() {
        LinearExpression expression = model.createExpression();
        for (InfrastructureNode origin: infrastructureGraph.getNodes()) {
            if (outgoing.get(origin) > 0) {
                for (TTEdge edge : modelGraph.getEdges()) {
                    if (edge.getType() != TTEdge.EdgeType.WAIT) {
                        continue;
                    }
                    expression.clear();
                    expression.multiAdd(1, weightedWaitTime.get(origin).get(edge));
                    expression.multiAdd(-edge.getLength(), flow.get(origin).get(edge));
                    expression.multiAdd(-outgoing.get(origin) * edge.getLength(), stops.get(edge.getLeftNode().getOriginalNode()));
                    model.addConstraint(expression, Constraint.ConstraintSense.GREATER_EQUAL, -outgoing.get(origin) * edge.getLength(), "weighted_wait_of_origin_" + origin.getId() + "_at_stop_" + edge.getLeftNode().getOriginalNode().getId());
                }
            }
        }
    }


    private void optimize(SolverParameters parameters) {
        parameters.setSolverParameters(model);
        if (parameters.writeLpFile()) {
            model.write("basis/SL-TT.lp");
        }
        model.solve();
        if (model.getStatus() == Model.Status.INFEASIBLE) {
            model.computeIIS("basis/sl-tt.ilp");
            throw new RuntimeException("Model is infeasible!");
        }
    }

    private Graph<Stop, Link> readBackSolution(double conversionFactorCoordinates) {
        Graph<Stop, Link> ptn = new ArrayListGraph<>();
        int nextStopId = 1;
        HashMap<Integer, Stop> stopByNodeId = new HashMap<>();
        // Add stops to the ptn that were chosen in the model
        List<InfrastructureNode> stopsToBuild = stops.entrySet().stream().filter(e -> Math.round(model.getValue(e.getValue())) > 0).map(Map.Entry::getKey).sorted(Comparator.comparingInt(InfrastructureNode::getId)).collect(Collectors.toList());
        for (InfrastructureNode node: stopsToBuild) {
            logger.debug("Building stop " + node.getId());
            Stop stop = new Stop(nextStopId, node.getName(), String.valueOf(node.getId()),
                node.getxCoordinate() / conversionFactorCoordinates, node.getyCoordinate() / conversionFactorCoordinates);
            ptn.addNode(stop);
            nextStopId += 1;
            stopByNodeId.put(node.getId(), stop);
        }
        // Check which edges are between realised stops
        int nextLinkId = 1;
        // We need to find all connections between stops. For this, compute the shortest paths between all stop pairs
        // in the original infrastructure graph and add a direct connection if there is no other realised stop on
        // this path
        Set<Integer> nodeIdsToBuild = stopsToBuild.stream().map(InfrastructureNode::getId).collect(Collectors.toSet());
        boolean directedGraph = infrastructureGraph.isDirected();
        for (Stop origin: ptn.getNodes()) {
            logger.debug("Check " + origin + " for links");
            InfrastructureNode originNode = infrastructureGraph.getNode(Integer.parseInt(origin.getLongName()));
            Dijkstra<InfrastructureNode, InfrastructureEdge, Graph<InfrastructureNode, InfrastructureEdge>> dijkstra = new Dijkstra<>(infrastructureGraph, originNode, e -> (double) e.getLowerBound());
            for (Stop destination: ptn.getNodes()) {
                boolean output = origin.getId() == 2 && destination.getId() == 30;
                if (output) {
                    logger.debug("To " + destination);
                }
                if (origin.equals(destination) || !directedGraph && origin.getId() > destination.getId()) {
                    if (output) {
                        logger.debug("Skip");
                    }
                    continue;
                }
                InfrastructureNode destinationNode = infrastructureGraph.getNode(Integer.parseInt(destination.getLongName()));
                dijkstra.computeShortestPath(destinationNode);
                Collection<Path<InfrastructureNode, InfrastructureEdge>> paths = dijkstra.getPaths(destinationNode);
                logger.debug("Found " + paths.size() + " paths");
                boolean inbetween = false;
                for (Path<InfrastructureNode, InfrastructureEdge> path: paths) {
                    inbetween = path.getNodes().stream().anyMatch(n -> !n.equals(originNode) && !n.equals(destinationNode) && nodeIdsToBuild.contains(n.getId()));
                    logger.debug("Path " + path);
                    if (inbetween) {
                        logger.debug("Found inbetween");
                        break;
                    }
                }
                if (inbetween) {
                    continue;
                }
                // Create a new link
                Path<InfrastructureNode, InfrastructureEdge> path = paths.stream().findAny().orElseThrow(() -> new LinTimException("Unconnected network between nodes " + origin.getShortName() + " and " + destination.getShortName()));
                int lowerBound = path.getEdges().stream().mapToInt(InfrastructureEdge::getLowerBound).sum();
                int upperBound = path.getEdges().stream().mapToInt(InfrastructureEdge::getUpperBound).sum();
                double length = path.getEdges().stream().mapToDouble(InfrastructureEdge::getLength).sum();
                logger.debug("Creating link between " + origin + " and " + destination);
                ptn.addEdge(new Link(nextLinkId, origin, destination, length, lowerBound, upperBound, directedGraph));
                nextLinkId += 1;
            }
        }
        return ptn;
    }

    public static Graph<Stop, Link> findSolution(Graph<InfrastructureNode, InfrastructureEdge> infrastructureGraph,
                                                 Graph<InfrastructureNode, WalkingEdge> walkingGraph, OD od,
                                                 Parameters parameters) {
        TravelTimeModel model = new TravelTimeModel(infrastructureGraph, walkingGraph, od, parameters.getWaitingTime(), parameters.getSolverType());
        logger.debug("Building model graph");
        model.buildModelGraph();
        logger.debug("Build graph has " + model.modelGraph.getNodes().size() + " nodes and " + model.modelGraph.getEdges().size() + " edges");
        logger.debug("Building model");
        model.buildModel(parameters.getCostOfStop());
        logger.debug("Start optimizing");
        model.optimize(parameters);
        return model.readBackSolution(parameters.getConversionCoordinates());
    }
}
