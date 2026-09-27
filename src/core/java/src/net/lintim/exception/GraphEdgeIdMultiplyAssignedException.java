package net.lintim.exception;

/**
 * Exception to throw if the same edge id is assigned multiple times.
 */
public class GraphEdgeIdMultiplyAssignedException extends LinTimException {
    /**
     * Exception to throw if the same edge id is assigned multiple times.
     *
     * @param edgeId edge id
     */
    public GraphEdgeIdMultiplyAssignedException(int edgeId) {
        super("Error G2: Edge with id " + edgeId + " already exists.");
    }
}
