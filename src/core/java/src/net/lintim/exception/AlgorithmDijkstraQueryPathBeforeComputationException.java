package net.lintim.exception;

import net.lintim.model.Node;

/**
 * Exception to throw, when the path to a node is queried in {@link net.lintim.algorithm.Dijkstra} before a
 * shortest path to this node was computed
 */
public class AlgorithmDijkstraQueryPathBeforeComputationException extends LinTimException{

    /**
     * Exception to throw, when the path to a node is queried in {@link net.lintim.algorithm.Dijkstra} before a
     * shortest path to this node was computed
     * @param node the node
     */
    public AlgorithmDijkstraQueryPathBeforeComputationException(Node node){
        super("Error A4: Path to " + node + " was queried before computation");
    }
}
