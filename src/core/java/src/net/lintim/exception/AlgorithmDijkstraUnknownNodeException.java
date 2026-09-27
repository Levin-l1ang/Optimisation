package net.lintim.exception;

import net.lintim.model.Node;

/**
 * Exception to throw when any method in {@link net.lintim.algorithm.Dijkstra} is called with an unknown node, i.e.,
 * a node that was not in the graph, when the used instance of the class was initialized.
 */
public class AlgorithmDijkstraUnknownNodeException extends LinTimException {
    /**
     * Exception to throw when any method in {@link net.lintim.algorithm.Dijkstra} is called with an unknown node, i.e.,
     * a node that was not in the graph, when the used instance of the class was initialized.
     * @param unknownNode the unknown node
     */
    public AlgorithmDijkstraUnknownNodeException(Node unknownNode){
        super("Error A5: Usage of unknown node " + unknownNode);
    }
}
