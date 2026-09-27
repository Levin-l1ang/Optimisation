package net.lintim.exception;

/**
 * Exception to throw if edge is incident to a node which does not exist.
 */
public class GraphIncidentNodeNotFoundException extends LinTimException {
    /**
     * Exception to throw if edge is incident to a node which does not exist.
     *
     * @param edgeId edge id
     * @param nodeId node id
     */
    public GraphIncidentNodeNotFoundException(int edgeId, int nodeId) {
        super("Error G3: Edge " + edgeId + " is incident to node " + nodeId + " but node " + nodeId + " does not " +
            "exist.");
    }
}
