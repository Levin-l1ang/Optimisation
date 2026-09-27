package net.lintim.algorithm;

import net.lintim.exception.*;
import net.lintim.model.Edge;
import net.lintim.model.Graph;
import net.lintim.model.Node;
import net.lintim.model.Path;
import net.lintim.model.impl.LinkedListPath;
import net.lintim.util.Pair;
import net.lintim.util.Logger;

import java.util.*;
import java.util.function.Function;

/**
 * A straight forward implementation of the algorithm of Dijkstra, using {@link PriorityQueue}. The algorithm can be
 * initialized with some graph (directed or undirected), a node in the graph and a distance Function to compute length
 * of edges.
 * Shortest paths can be computed using {@link #computeShortestPath(Node)} or {@link #computeShortestPaths()}. The
 * distance to a node and the shortest path can only be queried after they are computed, by one of the two mentioned
 * methods. If the shortest path for a queried note was already calculated in an earlier query (maybe due to a side
 * effect for another query), there will be no new computation (this holds for {@link #computeShortestPaths()} as well).
 * <p>
 * Note that the used graph may not be altered after initializing an instance of this graph, since the results after
 * that are undefined (already computed shortest paths may change). Therefore the algorithm needs to be initialised
 * anew after each change!
 */
public class Dijkstra<N extends Node, E extends Edge<N>, G extends Graph<N, E>> {
    /**
     * The start node, for which the algorithm was initialized. All shortest path returned by the algorithm will
     * start here.
     */
    private N startNode;
    /**
     * The graph to compute the shortest paths on
     */
    private G graph;
    /**
     * The predecessors of the nodes. A predecessor of a node is the node directly before this node in a shortest
     * path. Needed to reconstruct the shortest paths
     */
    private HashMap<N, HashSet<N>> predecessors;
    /**
     * The distances of the shortest paths. Are stored after successfully computing a shortest path to a node
     */
    private HashMap<N, Double> distances;
    /**
     * Stores all nodes of which the shortest paths are already calculated
     */
    private HashSet<N> finishedNodes;
    /**
     * The function to calculate the length of an edge
     */
    private Function<E, Double> distanceFunction;
    /**
     * The already computed shortest paths
     */
    private HashMap<N, LinkedList<Path<N, E>>> alreadyComputedShortestPaths;

    private boolean warning_printed;

    private static Logger logger = new Logger(Dijkstra.class);

    /**
     * Initialize a new shortest path algorithm. Computing is done with {@link #computeShortestPath(Node)} or
     * {@link #computeShortestPaths()} and needs to be done before querying a shortest path or a distance with
     * {@link #getDistance(Node)} or {@link #getPath(Node)}.
     *
     * @param graph            the graph to compute the shortest paths on. Note that the used graph may not be
     *                         altered after
     *                         initializing an instance of this graph, since the results after that are undefined
     *                         (already
     *                         computed shortest paths may change). Therefore the algorithm needs to be initialised
     *                         anew after
     *                         each change!
     * @param startNode        the start node of the algorithm, i.e., the start of the shortest paths
     * @param distanceFunction the distance function to compute the length of an edge
     */
    public Dijkstra(G graph, N startNode, Function<E, Double> distanceFunction) {
        this.graph = graph;
        this.startNode = startNode;
        this.distanceFunction = distanceFunction;
        this.predecessors = new HashMap<>();
        this.distances = new HashMap<>();
        this.finishedNodes = new HashSet<>();
        for (N node : graph.getNodes()) {
            this.distances.put(node, Double.POSITIVE_INFINITY);
        }
        this.distances.put(startNode, 0.);
        this.finishedNodes.add(startNode);
        this.alreadyComputedShortestPaths = new HashMap<>();

        // Flag to determine whether a warning for negative edges leading to/coming from
        // nodes with zero out/in-degree
        this.warning_printed = false;

        for (E edge : graph.getEdges()) {
            double edgeLength = distanceFunction.apply(edge);
            if (edgeLength < 0) {
                if (graph.isDirected() && (graph.getIncomingEdges(edge.getLeftNode()).size()==0 || graph.getOutgoingEdges(edge.getRightNode()).size()==0)) {
                    if (!this.warning_printed) {
                        logger.debug("Graph contains an edge with negative length which either leads to a node with out-degree zero or comes from a node with in-degree zero. For this special case of negative edge lengths, Dijsktra's algorithm will still work correctly.");
                        this.warning_printed = true;
                    }
                }
                else {
                    throw new AlgorithmDijkstraNegativeEdgeLengthException(edge, edgeLength);
                }
            }
        }
    }

    /**
     * Get the distance from the initialized start node to the given end node. The shortest path to the given end
     * node needs to be computed first, either by {@link #computeShortestPath(Node)} or {@link #computeShortestPaths()}.
     *
     * @param endNode the end node of the shortest path
     * @return the distance between the start and the end node or {@link Double#POSITIVE_INFINITY} if there is no path
     */
    public double getDistance(N endNode) throws AlgorithmDijkstraQueryDistanceBeforeComputationException,
                                                AlgorithmDijkstraUnknownNodeException {
        Double distance = distances.get(endNode);
        if (distance == null) {
            throw new AlgorithmDijkstraUnknownNodeException(endNode);
        }
        if (!finishedNodes.contains(endNode)) {
            throw new AlgorithmDijkstraQueryDistanceBeforeComputationException(endNode);
        }
        return distance;
    }

    /**
     * Get the shortest path from the initialized start node to the given end node. The shortest path to the given
     * end node needs to be computed first, either by {@link #computeShortestPath(Node)} or
     * {@link #computeShortestPaths()}.
     *
     * @param endNode the end node of the shortest path
     * @return the shortest path between the start and the end node or null, if start and end node coincide or there
     * is no path between the two nodes
     */
    public Path<N, E> getPath(N endNode) throws AlgorithmDijkstraQueryPathBeforeComputationException,
                                                AlgorithmDijkstraUnknownNodeException {
        if (!finishedNodes.contains(endNode)) {
            throw new AlgorithmDijkstraQueryPathBeforeComputationException(endNode);
        }
        if (this.startNode == endNode || this.distances.get(endNode).equals(Double.POSITIVE_INFINITY)) {
            return null;
        }
        if(alreadyComputedShortestPaths.get(endNode) != null){
            return alreadyComputedShortestPaths.get(endNode).stream().findAny().orElse(null);
        }
        Path<N, E> path = new LinkedListPath<>(graph.isDirected());
        N currentNode = endNode;
        boolean foundEdge;
        do {
            N nextNode = predecessors.get(currentNode).stream().findAny().orElse(null);
            //Search for the edge between current and next node
            foundEdge = false;
            for (E edge : graph.getIncomingEdges(currentNode)) {
                if ((edge.getLeftNode().equals(currentNode) && edge.getRightNode().equals(nextNode)) ||
                    (edge.getRightNode().equals(currentNode) && edge.getLeftNode().equals(nextNode))) {
                    path.addFirst(edge);
                    currentNode = nextNode;
                    foundEdge = true;
                    break;
                }
            }
            if (!foundEdge) {
                //This should never happen in a valid graph and with a well working algorithm.
                throw new AlgorithmDijkstraUnknownNodeException(endNode);
            }
        } while (!currentNode.equals(startNode));
        //Copy path to store for buffering
        Path<N, E> bufferPath = new LinkedListPath<>(graph.isDirected());
        for (E edge: path.getEdges()) {
            bufferPath.addLast(edge);
        }
        alreadyComputedShortestPaths.put(endNode, new LinkedList<>());
        alreadyComputedShortestPaths.get(endNode).add(bufferPath);
        return path;
    }

    /**
     * Get all shortest path from the initialized start node to the given end node. All paths with the length of the
     * shortest path will be returned. The shortest paths to the given end node needs to be computed first, either by
     * {@link #computeShortestPath(Node)} or {@link #computeShortestPaths()}.
     * @param endNode the end node of the shortest paths
     * @return the
     * @throws AlgorithmDijkstraQueryDistanceBeforeComputationException if the path was queried before it was computed
     * @throws AlgorithmDijkstraUnknownNodeException if the node is not known, i.e., it was not in the graph when the
     * Dijkstra class was constructed
     */
    public Collection<Path<N, E>> getPaths(N endNode) throws
        AlgorithmDijkstraQueryDistanceBeforeComputationException, AlgorithmDijkstraUnknownNodeException {
        if (!finishedNodes.contains(endNode)) {
            throw new AlgorithmDijkstraQueryPathBeforeComputationException(endNode);
        }
        if (this.startNode == endNode || this.distances.get(endNode).equals(Double.POSITIVE_INFINITY)) {
            return null;
        }
        if(alreadyComputedShortestPaths.get(endNode) != null){
            return alreadyComputedShortestPaths.get(endNode);
        }
        HashSet<Path<N, E>> paths = new HashSet<>();
        for(N nextNode : predecessors.get(endNode)){
            E nextEdge = null;
            for (E edge : graph.getIncomingEdges(endNode)) {
                if ((edge.getLeftNode().equals(endNode) && edge.getRightNode().equals(nextNode)) ||
                    (edge.getRightNode().equals(endNode) && edge.getLeftNode().equals(nextNode))) {
                    nextEdge = edge;
                    break;
                }
            }
            if (nextEdge == null) {
                //This should never happen in a valid graph and with a well working algorithm.
                throw new AlgorithmDijkstraUnknownNodeException(endNode);
            }
            //Find all shortest paths to nextNode
            if(nextNode == this.startNode){
                LinkedListPath<N, E> sp = new LinkedListPath<>(graph.isDirected());
                sp.addFirst(nextEdge);
                paths.add(sp);
                continue;
            }
            Collection<Path<N, E>> shortestPartPaths = getPaths(nextNode);
            if(shortestPartPaths == null){
                //This is only the case if the distance to nextNode from startNode was infinity, therefore the graph
                //is not connected.
                return null;
            }
            for(Path<N, E> shortestPartPath : shortestPartPaths){
                Path<N, E> shortestPath = new LinkedListPath<>(graph.isDirected());
                shortestPath.addLast(shortestPartPath.getEdges());
                shortestPath.addLast(nextEdge);
                paths.add(shortestPath);
            }
        }
        //Copy all paths to store for buffering
        LinkedList<Path<N, E>> bufferPaths = new LinkedList<>();
        for(Path<N, E> sp : paths){
            List<E> edges = sp.getEdges();
            Path<N, E> bufferPath = new LinkedListPath<>(graph.isDirected());
            for(E edge : edges){
                bufferPath.addLast(edge);
            }
            bufferPaths.add(bufferPath);
        }
        alreadyComputedShortestPaths.put(endNode, bufferPaths);

        return paths;
    }

    /**
     * Compute a shortest path from the initialized start node to the given end node
     *
     * @param endNode the node to compute the shortest path to
     * @return the distance between start and end node or {@link Double#POSITIVE_INFINITY} if there is no path
     */
    public double computeShortestPath(N endNode) throws AlgorithmDijkstraNegativeEdgeLengthException {
        if (finishedNodes.contains(endNode)) {
            return distances.get(endNode);
        }
        //Initialize queue
        PriorityQueue<Pair<N, Double>> priorityQueue = new PriorityQueue<>(Comparator.comparingDouble
            (Pair::getSecondElement));
        HashMap<N, Pair<N, Double>> shortestPathNodes = new HashMap<>();
        for (Map.Entry<N, Double> entry : distances.entrySet()) {
            Pair<N, Double> newNode = new Pair<>(entry.getKey(), entry.getValue());
            priorityQueue.add(newNode);
            shortestPathNodes.put(entry.getKey(), newNode);
        }
        Pair<N, Double> nextNode;
        while (null != (nextNode = priorityQueue.poll())) {
            if(nextNode.getSecondElement() == Double.POSITIVE_INFINITY) {
                // The element with the lowest distance has distance infinity. Therefore, we have an unconnected
                // network and abort
                for (N node : graph.getNodes()) {
                    if (!finishedNodes.contains(node)) {
                        finishedNodes.add(node);
                        distances.put(node, Double.POSITIVE_INFINITY);
                    }
                }
                return Double.POSITIVE_INFINITY;
            }
            this.distances.put(nextNode.getFirstElement(), nextNode.getSecondElement());
            finishedNodes.add(nextNode.getFirstElement());
            if (nextNode.getFirstElement().equals(endNode)) {
                //Found end node!
                return nextNode.getSecondElement();
            }
            //Check all outgoing edges
            for (E edge : graph.getOutgoingEdges(nextNode.getFirstElement())) {
                //Check if we found a shorter path
                N adjacentNode = edge.getLeftNode().equals(nextNode.getFirstElement()) ? edge.getRightNode() : edge
                    .getLeftNode();
                //The found node may already have a computed shortest path (from a previous computation). In this
                // case we may continue
                if(finishedNodes.contains(adjacentNode)){
                    continue;
                }
                Pair<N, Double> adjacentShortestPathNode = shortestPathNodes.get(adjacentNode);
                double edgeLength = distanceFunction.apply(edge);

                double newDistance = nextNode.getSecondElement() + edgeLength;
                if(newDistance == adjacentShortestPathNode.getSecondElement() && newDistance != Double.POSITIVE_INFINITY){
                    this.predecessors.get(adjacentNode).add(nextNode.getFirstElement());
                }
                else if (newDistance < adjacentShortestPathNode.getSecondElement()) {
                    //Update the shortest path node and set predecessor
                    HashSet<N> predecessorsForNode = new HashSet<>();
                    predecessorsForNode.add(nextNode.getFirstElement());
                    this.predecessors.put(adjacentNode, predecessorsForNode);
                    priorityQueue.remove(adjacentShortestPathNode);
                    Pair<N, Double> newPair = new Pair<>(adjacentNode, newDistance);
                    shortestPathNodes.put(adjacentNode, newPair);
                    priorityQueue.add(newPair);
                }
            }
        }
        //If we arrive here, there is no shortest path
        finishedNodes.add(endNode);
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Compute a shortest path between the initialized start node and every other node in the graph. Will reuse
     * already computed shortest paths.
     */
    public void computeShortestPaths() {
        graph.getNodes().stream().filter(n -> !finishedNodes.contains(n)).forEach(this::computeShortestPath);
    }

}
