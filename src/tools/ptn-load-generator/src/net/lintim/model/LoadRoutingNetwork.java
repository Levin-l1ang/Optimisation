package net.lintim.model;

import net.lintim.algorithm.Dijkstra;
import net.lintim.exception.ConfigTypeMismatchException;
import net.lintim.exception.LinTimException;
import net.lintim.main.tools.PTNLoadGeneratorMain;
import net.lintim.model.impl.ArrayListGraph;
import net.lintim.model.impl.LinkedListPath;
import net.lintim.util.Config;
import net.lintim.util.GraphHelper;
import net.lintim.util.Pair;
import net.lintim.util.tools.LoadGenerationParameters;
import net.lintim.util.Logger;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.interfaces.KShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.YenKShortestPath;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 */
public class LoadRoutingNetwork {

    private static final Logger logger = new Logger(LoadRoutingNetwork.class);

    private final OD od;
    private final Graph<Stop, Link> basePtn;
    private Graph<Stop, Link> directedPtn = null;
    private Graph<ChangeAndGoNode, ChangeAndGoEdge> cg = null;
    private HashMap<Pair<Integer, Integer>, Integer> cgNodeLookUpMap;
    private HashMap<Integer, Integer> ptnEdgeLookUpMap;
    private final SimpleDirectedWeightedGraph<ChangeAndGoNode, ChangeAndGoEdge> cgJgraphTGraph = new
        SimpleDirectedWeightedGraph<>(ChangeAndGoEdge.class);
    private final SimpleDirectedWeightedGraph<Stop, Link> ptnJgraphTGraph = new
        SimpleDirectedWeightedGraph<>(Link.class);
    private Function<Link, Double> ptnTravelTimeFunction;
    private Function<ChangeAndGoEdge, Double> cgTravelTimeFunction;
    private Function<Link, Double> ptnEdgeObjectiveFunction;
    private Function<ChangeAndGoEdge, Double> cgEdgeObjectiveFunction;
    private double waitTime;
    private HashMap<Pair<Integer, Integer>, HashMap<Integer, Path<ChangeAndGoNode, ChangeAndGoEdge>>> currentCgPaths;
    private HashMap<Pair<Integer, Integer>, HashMap<Integer, Path<Stop, Link>>> currentPtnPaths;
    private final LinePool linePool;
    private final double changeTime;
    private final Map<Integer, Map<Pair<Integer, Integer>, Double>> additionalLoad;
    private final LoadGenerationParameters parameters;

    /**
     * Create a new routing network
     * @param basePtn the baseline ptn
     * @param od the od matrix
     * @param linePool the linepool. If no change&go-network should be used, this may be null
     * @param additionalLoad the additional load. May be null if not used
     * @param parameters the parameters of the network
     */
    public LoadRoutingNetwork(Graph<Stop, Link> basePtn, OD od, LinePool linePool,
                              Map<Integer, Map<Pair<Integer, Integer>, Double>> additionalLoad,
                              LoadGenerationParameters parameters) {
        this.basePtn = basePtn;
        this.od = od;
        this.linePool = linePool;
        this.changeTime = Math.min(parameters.getMinChangeTimeFactor() * parameters.getMinChangeTime(),
            parameters.getMaxChangeTime());
        this.additionalLoad = additionalLoad;
        this.parameters = parameters;

        setTravelTimeFunction(parameters.getModelWeightDrive(), parameters.getModelWeightWait());
        setObjectiveFunction(parameters.getLoadGeneratorType(), parameters.getCostFactor());
        if (parameters.useCg()) {
            initializeCgUsage();
        }
        else {
            initializePtnUsage();
        }
    }

    /**
     * Compute a new shortest path for all passengers. All paths will be updated.
     */
    public void computeNewShortestPaths() {
        if (parameters.useCg()) {
            computeNewShortestPathsCg();
        } else {
            computeNewShortestPathsPtn();
        }
    }

    /**
     * Reroute all passengers to new shortest paths. Will use the rerouted reduction objective function, i.e., all
     * edges with existing load on the corresponding original ptn edge have the travel time as an edge length and all
     * other edges may not be used
     */
    public void computeNewShortestPathRerouteReduction() {
        // Reset the objective function to only use used ptn edges
        if (parameters.useCg()) {
            cgEdgeObjectiveFunction = cgEdge -> {
                int correspondingPtnLinkId = cgEdge.getCorrespondingPtnLinkId();
                if(correspondingPtnLinkId == ChangeAndGoEdge.CHANGE_LINK || basePtn.getEdge(correspondingPtnLinkId)
                    .getLoad() > 0){
                    return cgTravelTimeFunction.apply(cgEdge);
                }
                return Double.POSITIVE_INFINITY;
            };
        }
        else {
            ptnEdgeObjectiveFunction = link -> basePtn.getEdge(ptnEdgeLookUpMap.get(link.getId())).getLoad() > 0 ? ptnTravelTimeFunction
                .apply(link) : Double.POSITIVE_INFINITY;
        }
        computeNewShortestPaths();
    }

    /**
     * Compute a new shortest path for the given passenger. Will update only the path for this passenger
     *
     * @param origin      the origin of the passenger
     * @param destination the destination of the passenger
     * @param passenger   the number of the passenger. The number is used to identify a passenger over multiple runs,
     *                    to ensure that the old shortest path of this passenger is handled correctly
     */
    public void computeNewShortestPaths(Stop origin, Stop destination, int passenger) {
        if (parameters.useCg()) {
            computeNewShortestPathsCg(origin, destination, passenger);
        } else {
            computeNewShortestPathsPtn(origin, destination, passenger);
        }
    }

    private void setTravelTimeFunction(String eanModelWeightDrive, String eanModelWeightWait) {
        Function<Link, Double> driveTimeFunction;
        switch (eanModelWeightDrive) {
            case "AVERAGE_DRIVING_TIME":
                driveTimeFunction = link -> (link.getLowerBound() + link.getUpperBound()) / 2.0;
                break;
            case "MINIMAL_DRIVING_TIME":
                driveTimeFunction = link -> (double) link.getLowerBound();
                break;
            case "MAXIMAL_DRIVING_TIME":
                driveTimeFunction = link -> (double) link.getUpperBound();
                break;
            case "EDGE_LENGTH":
                driveTimeFunction = Link::getLength;
                break;
            default:
                throw new ConfigTypeMismatchException("ean_model_weight_drive", "String", Config.getStringValueStatic
                    ("ean_model_weight_drive"));
        }
        int minWaitingTime = parameters.getMinWaitTime();
        int maxWaitingTime = parameters.getMaxWaitTime();
        switch (eanModelWeightWait) {
            case "MINIMAL_WAITING_TIME":
                waitTime = minWaitingTime;
                break;
            case "AVERAGE_WAITING_TIME":
                waitTime = (minWaitingTime + maxWaitingTime) / 2.;
                break;
            case "MAXIMAL_WAITING_TIME":
                waitTime =  maxWaitingTime;
                break;
            case "ZERO_COST":
                waitTime = 0.;
                break;
            default:
                throw new ConfigTypeMismatchException("ean_model_weight_wait", "String", Config.getStringValueStatic
                    ("ean_model_weight_wait"));
        }
        ptnTravelTimeFunction = link -> driveTimeFunction.apply(link) + waitTime;
        if (parameters.useCg()) {
            cgTravelTimeFunction = ChangeAndGoEdge::getLength;
        }
    }

    private void setObjectiveFunction(LoadGenerationParameters.LoadGeneratorType objectiveType, double costFactor) {
        switch (objectiveType) {
            case SHORTEST_PATH:
                setShortestPathFunction();
                break;
            case REDUCTION:
                setReductionFunction(costFactor);
                break;
            case REWARD:
                setRewardFunction(costFactor);
                break;
            case ITERATIVE:
                // TODO: Implementation
                throw new LinTimException("Iterative load generation not implemented yet!");
            default:
                throw new ConfigTypeMismatchException("ptn_load_generator_type", "PTNLoadGeneratorType",
                    objectiveType.name());
        }
    }

    private void setReductionFunction(double costFactor) {
        Function<Link, Double> reductionCost = link -> link.getLength() / (link.getLoad() <= 1 ? 1 : link.getLoad());
        cgEdgeObjectiveFunction = cgEdge -> {
            double objective = cgTravelTimeFunction.apply(cgEdge);
            double costTerm = 0;
            if (cgEdge.getCorrespondingPtnLinkId() != ChangeAndGoEdge.CHANGE_LINK) {
                Link link = basePtn.getEdge(cgEdge.getCorrespondingPtnLinkId());
                costTerm = reductionCost.apply(link);
            }
            return objective + costFactor * costTerm;
        };
        ptnEdgeObjectiveFunction = link -> {
            double travelTime = ptnTravelTimeFunction.apply(link);
            return travelTime + costFactor * reductionCost.apply(link);
        };
    }

    private void setRewardFunction(double rewardFactor) {
        cgEdgeObjectiveFunction = cgEdge -> {
            double length = cgTravelTimeFunction.apply(cgEdge) *
                (1 - rewardFactor * (cgEdge.getLoad() % parameters.getCapacity()) / parameters.getCapacity());
            return length > 0 ? length : 0;
        };
        ptnEdgeObjectiveFunction = link -> {
            double length = ptnTravelTimeFunction.apply(link) *
                (1 - rewardFactor * (link.getLoad() % parameters.getCapacity()) / parameters.getCapacity());
            return length > 0 ? length : 0;
        };
    }

    private void setShortestPathFunction() {
        cgEdgeObjectiveFunction = cgTravelTimeFunction;
        ptnEdgeObjectiveFunction = ptnTravelTimeFunction;

    }

    private void initialiseJGraphT() {
        if (parameters.useCg()) {
            for(ChangeAndGoNode node : cg.getNodes()) {
                cgJgraphTGraph.addVertex(node);
            }
            for(ChangeAndGoEdge edge : cg.getEdges()){
                cgJgraphTGraph.addEdge(edge.getLeftNode(), edge.getRightNode(), edge);
                cgJgraphTGraph.setEdgeWeight(edge, cgEdgeObjectiveFunction.apply(edge));
            }
        }
        else {
            for(Stop node : directedPtn.getNodes()) {
                ptnJgraphTGraph.addVertex(node);
            }
            for(Link edge : directedPtn.getEdges()){
                ptnJgraphTGraph.addEdge(edge.getLeftNode(), edge.getRightNode(), edge);
                ptnJgraphTGraph.setEdgeWeight(edge, ptnEdgeObjectiveFunction.apply(edge));
            }
        }
    }

    private void updateJGraphT() {
        if (parameters.useCg()) {
            for(ChangeAndGoEdge edge : cg.getEdges()){
                cgJgraphTGraph.setEdgeWeight(edge, cgEdgeObjectiveFunction.apply(edge));
            }
        }
        else {
            for(Link link : directedPtn.getEdges()){
                ptnJgraphTGraph.setEdgeWeight(link, ptnEdgeObjectiveFunction.apply(link));
            }
        }
    }

    private void initializeCgUsage() {
        buildCg();
        // Initialise the path map
        currentCgPaths = new HashMap<>();
        for(ODPair pair : od.getODPairs()) {
            currentCgPaths.put(new Pair<>(pair.getOrigin(), pair.getDestination()), new HashMap<>());
        }
    }

    private void buildCg() {
        cg = new ArrayListGraph<>();
        cgNodeLookUpMap = new HashMap<>();
        int nextId = 1;
        //First add the nodes
        for (Stop stop : basePtn.getNodes()) {
            ChangeAndGoNode start = new ChangeAndGoNode(nextId, stop.getId(), ChangeAndGoNode.START);
            cg.addNode(start);
            cgNodeLookUpMap.put(new Pair<>(stop.getId(), ChangeAndGoNode.START), nextId);
            nextId += 1;
        }
        for (Line line : linePool.getLines()) {
            for (Stop stop : line.getLinePath().getNodes()) {
                ChangeAndGoNode node = new ChangeAndGoNode(nextId, stop.getId(), line.getId());
                cg.addNode(node);
                cgNodeLookUpMap.put(new Pair<>(stop.getId(), line.getId()), nextId);
                nextId += 1;
            }
        }
        //Now add the edges
        nextId = 1;
        //First, connect the lines
        for (Line line : linePool.getLines()) {
            for (Link link : line.getLinePath().getEdges()) {
                ChangeAndGoNode leftNode = cg.getNode(cgNodeLookUpMap.get(new Pair<>(link.getLeftNode().getId(), line
                    .getId())));
                ChangeAndGoNode rightNode = cg.getNode(cgNodeLookUpMap.get(new Pair<>(link.getRightNode().getId(), line
                    .getId())));
                ChangeAndGoEdge changeAndGoEdge = new ChangeAndGoEdge(nextId++, leftNode, rightNode,
                    ptnTravelTimeFunction.apply(link), link.getId());
                cg.addEdge(changeAndGoEdge);
                //For undirected networks, add the backwards direction of the line
                if (!basePtn.isDirected()) {
                    ChangeAndGoEdge reverseEdge = new ChangeAndGoEdge(nextId++, rightNode, leftNode,
                        ptnTravelTimeFunction.apply(link), link.getId());
                    cg.addEdge(reverseEdge);
                }
            }
        }
        //Now, create the change edges
        // We connect every line node with the corresponding stop node. Therefore changing a line is equivalent to
        // alighting one vehicle and boarding another. The complete change penalty will be added on the alighting
        // edge. On this edge, we additionally subtract the waiting penalty.
        if (parameters.getChangePenalty() + changeTime <= waitTime) {
            throw new LinTimException("Cannot handle the situation where the change penalty + min change time is not " +
                "bigger than the wait time!");
        }
        for (Stop stop : basePtn.getNodes()) {
            //Get a list of all nodes corresponding to this stop
            List<ChangeAndGoNode> stopNodes = cg.getNodes().stream()
                .filter(changeAndGoNode -> changeAndGoNode.getStopId() == stop.getId())
                .collect(Collectors.toList());
            // Find the base node
            ChangeAndGoNode baseNode = stopNodes.stream().filter(node -> node.getLineId() == ChangeAndGoNode.START)
                .findAny().orElseThrow(()-> new LinTimException("Could not find start node for stop " + stop.getId()));
            for (ChangeAndGoNode sourceNode : stopNodes) {
                if (sourceNode.equals(baseNode)) {
                    continue;
                }
                ChangeAndGoEdge boardingEdge = new ChangeAndGoEdge(nextId++, baseNode, sourceNode, 0,
                    ChangeAndGoEdge.CHANGE_LINK);
                cg.addEdge(boardingEdge);
                ChangeAndGoEdge alightingEdge = new ChangeAndGoEdge(nextId++, sourceNode, baseNode,
                    parameters.getChangePenalty() + changeTime - waitTime, ChangeAndGoEdge.CHANGE_LINK);
                cg.addEdge(alightingEdge);
            }
        }
    }

    private void initializePtnUsage() {
        buildDirectedPtn();
        // Need to initialize path map
        currentPtnPaths = new HashMap<>();
        for(ODPair pair : od.getODPairs()) {
            currentPtnPaths.put(new Pair<>(pair.getOrigin(), pair.getDestination()), new HashMap<>());
        }
    }

    private void buildDirectedPtn() {
        ptnEdgeLookUpMap = new HashMap<>();
        directedPtn = new ArrayListGraph<>();
        for (Stop stop: basePtn.getNodes()) {
            directedPtn.addNode(new Stop(stop.getId(), stop.getShortName(), stop.getLongName(), stop.getxCoordinate()
                , stop.getyCoordinate()));
        }
        for (Link link: basePtn.getEdges()) {
            directedPtn.addEdge(new Link(link.getId(), link.getLeftNode(), link.getRightNode(), link.getLength(),
                link.getLowerBound(), link.getUpperBound(), true));
            ptnEdgeLookUpMap.put(link.getId(), link.getId());
        }
        if (!basePtn.isDirected()) {
            int nextLinkId = GraphHelper.getMaxEdgeId(directedPtn) + 1;
            for (Link link: basePtn.getEdges()) {
                directedPtn.addEdge(new Link(nextLinkId, link.getRightNode(), link.getLeftNode(), link.getLength(),
                    link.getLowerBound(), link.getUpperBound(), true));
                ptnEdgeLookUpMap.put(nextLinkId, link.getId());
                nextLinkId += 1;
            }
        }
    }

    private boolean jgraphTIsInitialized() {
        return parameters.useCg() ? cgJgraphTGraph.vertexSet().size() > 0 : ptnJgraphTGraph.vertexSet().size() > 0;
    }


    private void computeNewShortestPathsCg() {
        HashMap<Pair<ChangeAndGoNode, ChangeAndGoNode>, HashMap<Path<ChangeAndGoNode, ChangeAndGoEdge>, Double>>
            shortestPaths;
        Collection<ChangeAndGoNode> origins = cg.getNodes().stream().filter(node -> node.getLineId() ==
            ChangeAndGoNode.START).collect(Collectors.toList());
        Collection<ChangeAndGoNode> destinations = cg.getNodes().stream().filter(node -> node.getLineId() ==
            ChangeAndGoNode.START).collect(Collectors.toList());
        if (parameters.getNumberShortestPaths() > 1) {
            shortestPaths = computeNewKShortestPaths(cgJgraphTGraph, origins, destinations);
        } else {
            shortestPaths = computeNewShortestPaths(cg, cgEdgeObjectiveFunction, origins, destinations);
        }
        resetLoadOnCg(shortestPaths);
        distributeLoad();
    }

    private void computeNewShortestPathsPtn() {
        HashMap<Pair<Stop, Stop>, HashMap<Path<Stop, Link>, Double>> shortestPaths;
        if (parameters.getNumberShortestPaths() > 1) {
            shortestPaths = computeNewKShortestPaths(ptnJgraphTGraph, directedPtn.getNodes(), directedPtn.getNodes());
        } else {
            shortestPaths = computeNewShortestPaths(directedPtn, ptnEdgeObjectiveFunction, directedPtn.getNodes(), directedPtn.getNodes());
        }
        resetLoadOnPtn(shortestPaths);
        distributeLoad();
    }


    private static void resetLoadOnPtn(Graph<Stop, Link> graph) {
        for (Link link : graph.getEdges()) {
            link.setLoad(0);
        }
    }

    private static void resetLoadOnCgn(Graph<ChangeAndGoNode, ChangeAndGoEdge> cg) {
        for (ChangeAndGoEdge edge : cg.getEdges()) {
            edge.setLoad(0);
        }
    }

    private void computeNewShortestPathsCg(Stop origin, Stop destination, int passenger) {
        Path<ChangeAndGoNode, ChangeAndGoEdge> shortestPath;
        ChangeAndGoNode originCg = cg.getNode(cgNodeLookUpMap.get(new Pair<>(origin.getId(), ChangeAndGoNode
            .START)));
        ChangeAndGoNode destinationCg = cg.getNode(cgNodeLookUpMap.get(new Pair<>(destination.getId(), ChangeAndGoNode
            .START)));
        shortestPath = computeNewShortestPaths(originCg, destinationCg, cg, cgEdgeObjectiveFunction);
        // Remove the old path, if present
        Path<ChangeAndGoNode, ChangeAndGoEdge> oldPath = currentCgPaths.get(new Pair<>(origin.getId(),
            destination.getId())).get(passenger);
        if (oldPath != null) {
            for (ChangeAndGoEdge edge : oldPath.getEdges()) {
                edge.setLoad(edge.getLoad() - 1);
            }
        }
        // Update for new path
        for (ChangeAndGoEdge edge : shortestPath.getEdges()) {
            edge.setLoad(edge.getLoad() + 1);
        }
        currentCgPaths.get(new Pair<>(origin.getId(), destination.getId())).put(passenger, shortestPath);
    }

    private void computeNewShortestPathsPtn(Stop origin, Stop destination, int passenger) {
        Path<Stop, Link> shortestPath = computeNewShortestPaths(origin, destination, directedPtn, ptnEdgeObjectiveFunction);
        Path<Stop, Link> oldPath = currentPtnPaths.get(new Pair<>(origin.getId(),
            destination.getId())).get(passenger);
        // Remove the old path, if present
        if (oldPath != null) {
            for (Link edge : oldPath.getEdges()) {
                edge.setLoad(edge.getLoad() - 1);
            }
        }
        // Update for new path
        for (Link edge : shortestPath.getEdges()) {
            edge.setLoad(edge.getLoad() + 1);
        }
        // Store new path
        currentPtnPaths.get(new Pair<>(origin.getId(), destination.getId())).put(passenger, shortestPath);
    }

    private <N extends Node, E extends Edge<N>> HashMap<Pair<N, N>, HashMap<Path<N, E>, Double>>
    computeNewKShortestPaths(SimpleDirectedWeightedGraph<N, E> spGraph, Collection<N> origins,
                             Collection<N> destinations) {
        if (!jgraphTIsInitialized()) {
            initialiseJGraphT();
        } else {
            updateJGraphT();
        }
        HashMap<Pair<N, N>, HashMap<Path<N, E>, Double>> shortestPaths = new HashMap<>();
        KShortestPathAlgorithm<N, E> spAlgo = new YenKShortestPath<>(spGraph);
        for (N origin : origins) {
            for (N destination : destinations) {
                if (origin.equals(destination)) {
                    continue;
                }
                List<GraphPath<N, E>> paths = spAlgo.getPaths(origin, destination, parameters.getNumberShortestPaths());
                shortestPaths.put(new Pair<>(origin, destination), distributePassengers(paths,
                    parameters.getDistributionFactor()));
            }
        }
        return shortestPaths;
    }

    private static <N extends Node, E extends Edge<N>> HashMap<Pair<N, N>, HashMap<Path<N, E>, Double>>
    computeNewShortestPaths (Graph<N, E> graph, Function<E, Double> lengthFunction, Collection<N> origins,
                             Collection<N> destinations) {
        HashMap<Pair<N, N>, HashMap<Path<N, E>, Double>> shortestPaths = new HashMap<>();
        int skippedPassengers = 0;
        for (N origin : origins) {
            Dijkstra<N, E, Graph<N, E>> dijkstra = new Dijkstra<>(graph, origin, lengthFunction);
            dijkstra.computeShortestPaths();
            for (N destination : destinations) {
                if (origin.equals(destination)) {
                    continue;
                }
                if (dijkstra.getPath(destination) == null) {
                    logger.debug("No path found from " + origin.getId() + " to " + destination.getId() + ". Skipping passengers on this OD pair.");
                    skippedPassengers++;
                }
                else {
                    shortestPaths.put(new Pair<>(origin, destination), new HashMap<>());
                    shortestPaths.get(new Pair<>(origin, destination)).put(dijkstra.getPath(destination), 1.);
                }
            }
        }
        if (skippedPassengers > 0) {
            logger.warn("Skipped " + skippedPassengers + " OD pairs due to missing paths.");
        }
        return shortestPaths;
    }

    private static <N extends Node, E extends Edge<N>> Path<N, E> computeNewShortestPaths(N origin, N
        destination, Graph<N, E> graph, Function<E, Double> lengthFunction) {
        Dijkstra<N, E, Graph<N, E>> dijkstra = new Dijkstra<>(graph, origin, lengthFunction);
        dijkstra.computeShortestPath(destination);
        return dijkstra.getPath(destination);
    }

    private static <N extends Node, E extends Edge<N>> HashMap<Path<N, E>, Double> distributePassengers
        (List<GraphPath<N, E>> paths, double beta) {
        HashMap<Path<N, E>, Double> pathDistribution = new HashMap<>();
        HashMap<GraphPath<N, E>, Double> values = new HashMap<>();
        for (GraphPath<N, E> path : paths) {
            double newValue = Math.exp(beta * path.getWeight());
            values.put(path, newValue);
        }
        double divisor = values.values().stream().mapToDouble(Double::doubleValue).sum();
        for (GraphPath<N, E> path : paths) {
            Path<N, E> sp = new LinkedListPath<>(true);
            sp.addLast(path.getEdgeList());
            double value = values.get(path) / divisor;
            pathDistribution.put(sp, value);
        }
        return pathDistribution;
    }

    public void distributeLoad(){
        if (parameters.useCg()) {
            distributeLoadToPtnFromCg();
        }
        else {
            distributeLoadToBasePtn();
        }
    }

    private void resetLoadOnPtn(HashMap<Pair<Stop, Stop>, HashMap<Path<Stop, Link>, Double>> shortestPaths) {
        resetLoadOnPtn(directedPtn);
        for (Map.Entry<Pair<Stop, Stop>, HashMap<Path<Stop, Link>, Double>> shortestPathsOfPassenger : shortestPaths
            .entrySet()) {
            Pair<Stop, Stop> odPair = shortestPathsOfPassenger.getKey();
            double odValue = od.getValue(odPair.getFirstElement().getId(), odPair.getSecondElement().getId());
            if (odValue == 0) {
                continue;
            }
            for (Map.Entry<Path<Stop, Link>, Double> spMapEntry : shortestPathsOfPassenger.getValue().entrySet()) {
                for (Link link : spMapEntry.getKey().getEdges()) {
                    link.setLoad(link.getLoad() + odValue * spMapEntry.getValue());
                }
            }
        }
    }

    private void distributeLoadToBasePtn() {
        if (basePtn.isDirected()) {
            // Just use the loads of the routing ptn
            basePtn.getEdges().forEach(link -> link.setLoad(directedPtn.getEdge(link.getId()).getLoad()));
            // Add additional load, if present
            if (additionalLoad != null) {
                for (Map.Entry<Integer, Map<Pair<Integer, Integer>, Double>> loadEntry: additionalLoad.entrySet()) {
                    Link link = basePtn.getEdge(loadEntry.getKey());
                    link.setLoad(link.getLoad() + loadEntry.getValue().values().stream().findAny().orElseThrow(()
                        -> new RuntimeException("Invalid formatted additional load for link"+ link.getId())));
                }
            }
        } else {
            for (Link undirectedLink : basePtn.getEdges()) {
                Function<Link, Boolean> isForwardLink = link -> link.getLeftNode().equals(undirectedLink.getLeftNode
                    ()) && link.getRightNode().equals(undirectedLink.getRightNode());
                Function<Link, Boolean> isBackwardLink = link -> link.getRightNode().equals(undirectedLink.getLeftNode
                    ()) && link.getLeftNode().equals(undirectedLink.getRightNode());
                Link forwardLink = directedPtn.getEdge(isForwardLink, true);
                Link backwardLink = directedPtn.getEdge(isBackwardLink, true);
                double forwardLoad = forwardLink.getLoad();
                double backwardLoad = backwardLink.getLoad();
                if (additionalLoad != null && additionalLoad.containsKey(undirectedLink.getId())) {
                    forwardLoad += additionalLoad.get(undirectedLink.getId())
                        .getOrDefault(new Pair<>(undirectedLink.getLeftNode().getId(),
                            undirectedLink.getRightNode().getId()), 0.);
                    backwardLoad += additionalLoad.get(undirectedLink.getId())
                        .getOrDefault(new Pair<>(undirectedLink.getRightNode().getId(),
                            undirectedLink.getLeftNode().getId()), 0.);
                }
                undirectedLink.setLoad(Math.max(forwardLoad, backwardLoad));
            }
        }
    }

    private void resetLoadOnCg(HashMap<Pair<ChangeAndGoNode, ChangeAndGoNode>, HashMap<Path<ChangeAndGoNode,
        ChangeAndGoEdge>, Double>> shortestPaths) {
        resetLoadOnCgn(cg);
        for (Map.Entry<Pair<ChangeAndGoNode, ChangeAndGoNode>, HashMap<Path<ChangeAndGoNode, ChangeAndGoEdge>,
            Double>> shortestPathOfPassenger : shortestPaths.entrySet()) {
            Pair<ChangeAndGoNode, ChangeAndGoNode> odPair = shortestPathOfPassenger.getKey();
            double odValue = od.getValue(odPair.getFirstElement().getStopId(), odPair.getSecondElement().getStopId());
            if (odValue == 0) {
                continue;
            }
            for (Map.Entry<Path<ChangeAndGoNode, ChangeAndGoEdge>, Double> spMapEntry : shortestPathOfPassenger
                .getValue().entrySet()) {
                for (ChangeAndGoEdge edge : spMapEntry.getKey().getEdges()) {
                    edge.setLoad(edge.getLoad() + odValue * spMapEntry.getValue());
                }
            }
        }

    }

    private void distributeLoadToPtnFromCg() {
        for (Link link : basePtn.getEdges()) {
            List<ChangeAndGoEdge> correspondingCgEdges = cg.getEdges().stream().filter(edge -> edge
                .getCorrespondingPtnLinkId() == link.getId()).collect(Collectors.toList());
            double forwardWeight = correspondingCgEdges.stream().filter(edge -> edge.getLeftNode().getStopId() ==
                link.getLeftNode().getId()).mapToDouble(ChangeAndGoEdge::getLoad).sum();
            if (additionalLoad != null && additionalLoad.containsKey(link.getId())) {
                forwardWeight += additionalLoad.get(link.getId())
                    .getOrDefault(new Pair<>(link.getLeftNode().getId(), link.getRightNode().getId()), 0.);
            }
            if (basePtn.isDirected()) {
                link.setLoad(forwardWeight);
            } else {
                double backwardWeight = correspondingCgEdges.stream().filter(edge -> edge.getLeftNode().getStopId()
                    == link.getRightNode().getId()).mapToDouble(ChangeAndGoEdge::getLoad).sum();
                if (additionalLoad != null && additionalLoad.containsKey(link.getId())) {
                    backwardWeight += additionalLoad.get(link.getId())
                        .getOrDefault(new Pair<>(link.getRightNode().getId(), link.getLeftNode().getId()), 0.);
                }
                link.setLoad(Math.max(forwardWeight, backwardWeight));
            }
        }
    }

}
