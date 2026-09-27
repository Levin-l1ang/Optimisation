package net.lintim.exception;

/**
 * Exception to throw if the same node id is assigned multiple times.
 */
public class GraphNodeIdMultiplyAssignedException extends LinTimException {
    /**
     * Exception to throw if the same node id is assigned multiple times.
     *
     * @param nodeId node id
     */
    public GraphNodeIdMultiplyAssignedException(int nodeId) {
        super("Error G1: Node with id " + nodeId + " already exists.");
    }
}
