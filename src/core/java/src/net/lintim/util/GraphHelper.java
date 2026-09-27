package net.lintim.util;

import net.lintim.model.Edge;
import net.lintim.model.Graph;
import net.lintim.model.Node;

import java.util.Comparator;

/**
 * Class containing static graph helper methods
 */
public class GraphHelper {
    /**
     * Get the maximal node id currently present in the given graph
     * @param graph the graph to check
     * @param <N> the node type, needs to extend Node, see {@link net.lintim.model.Node}
     * @param <E> the edge type, needs to extend Edge<N>, see {@link net.lintim.model.Edge}
     * @return the maximal node id currently present in the graph
     */
    public static <N extends Node, E extends Edge<N>> int getMaxNodeId(Graph<N, E> graph){
        N maxNode = graph.getNodes().stream().max(Comparator.comparingInt(Node::getId)).orElse(null);
        return maxNode == null ? 0 : maxNode.getId();
    }

    /**
     * Get the maximal edge id currently present in the given graph
     * @param graph the graph to check
     * @param <N> the node type, needs to extend Node, see {@link net.lintim.model.Node}
     * @param <E> the edge type, needs to extend Edge<N>, see {@link net.lintim.model.Edge}
     * @return the maximal edge id currently present in the graph
     */
    public static <N extends Node, E extends Edge<N>> int getMaxEdgeId(Graph<N, E> graph){
        E maxEdge = graph.getEdges().stream().max(Comparator.comparingInt(Edge::getId)).orElse(null);
        return maxEdge == null ? 0 : maxEdge.getId();
    }
}
