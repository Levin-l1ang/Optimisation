package net.lintim.exception;

import net.lintim.model.Node;

/**
 * Exception to throw, when the distance to a node is queried in {@link net.lintim.algorithm.Dijkstra} before a
 * shortest path to this node was computed
 */
public class AlgorithmDijkstraQueryDistanceBeforeComputationException extends LinTimException{

    /**
     * Exception to throw, when the distance to a node is queried in {@link net.lintim.algorithm.Dijkstra} before a
     * shortest path to this node was computed
     * @param node the node
     */
    public AlgorithmDijkstraQueryDistanceBeforeComputationException(Node node){
        super("Error A3: Distance to " + node + " was queried before computation");
    }
}
