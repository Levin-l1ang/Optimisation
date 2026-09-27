package net.lintim.exception;

import net.lintim.model.Node;

/**
 */
public class AlgorithmDijkstraNetworkNotConnectedException extends LinTimException {
    /**
     * Exception to throw, if a shortest path into an unconnected part of the network was queried
     * @param sourceNode the start node
     * @param notConnnectedTargetNode the target node, that is not connected with the sourceNode
     */
    public AlgorithmDijkstraNetworkNotConnectedException(Node sourceNode, Node notConnnectedTargetNode){
        super("Error A7: Node " + sourceNode + " is not connected to node " + notConnnectedTargetNode + ", but a " +
            "shortest path was queried");
    }
}
