package net.lintim.exception;

import net.lintim.model.Edge;

/**
 * Exception to throw, if there is an edge with negative length in a graph used for the Dijkstra algorithm and this
 * edge is found during execution of the algorithm. Dijkstra cannot work reliable with negative edge lengths.
 */
public class AlgorithmDijkstraNegativeEdgeLengthException extends LinTimException{

    /**
     * Exception to throw, if there is an edge with negative length in a graph used for the Dijkstra algorithm and this
     * edge is found during execution of the algorithm. Dijkstra cannot work reliable with negative edge lengths.
     * @param edge the edge with negative length
     * @param length the negative length
     */
    public AlgorithmDijkstraNegativeEdgeLengthException(Edge edge, double length){
        super("Error A6: Edge " + edge + " has negative length " + length);
    }
}
