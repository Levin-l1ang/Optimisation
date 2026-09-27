package net.lintim.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;

/**
 * The template for a graph structure. There are default implementations of this interface, namely
 * {@link net.lintim.model.impl.SimpleMapGraph} and {@link net.lintim.model.impl.SimpleMapGraph}. More implementations may follow.
 * Choose the appropriate implementation based on your graph structure or implement your own.
 */
public interface Graph<N extends Node, E extends Edge<N>> extends Iterable<N>{

    /**
     * Get the node with the specified id from the graph. If there is no node with the given id, null will be returned.
     *
     * @param id the id to search for
     * @return the node with the given id, or null if there is none
     */
    N getNode(int id);

    /**
     * Get the edge with the specified id from the graph. If there is no edge with the given id, null will be returned.
     *
     * @param id the id to search for
     * @return the edge with the given id, or null if there is none
     */
    E getEdge(int id);

    /**
     * Get a node from the graph on which the given function takes the given value
     *
     * @param map the function whose value must be matched
     * @param value the value the function must take on the node to be returned
     * @param <O> type of function image and value
     * @return one of the nodes on which the provided function yields the provided value, or null if none exists
     */
    <O> N getNode(Function<N, O> map, O value);

    /**
     * Get an edge from the graph on which the given function takes the given value
     *
     * @param map the function whose value must be matched
     * @param value the value the function must take on the edge to be returned
     * @param <O> type of function image and value
     * @return one of the edges on which the provided function yields the provided value, or null if none exists
     */
    <O> E getEdge(Function<E, O> map, O value);

    /**
     * Add the given edge to the graph. There can not be multiple edges with the same id in the same graph.
     *
     * @param edge the edge to add to the network
     * @return whether the edge could be added to the graph
     */
    boolean addEdge(E edge);

    /**
     * Add the given node to the graph. There can not be multiple nodes with the same id in the same graph.
     *
     * @param node the node to add to the network
     * @return whether the node could be added to the graph
     */
    boolean addNode(N node);

    /**
     * Remove the given edge from the graph. After calling this method, there will not be an edge in this graph with
     * the same id
     *
     * @param edge the edge to remove
     * @return whether the edge could be removed from the graph
     */
    boolean removeEdge(E edge);

    /**
     * Remove the given node from the graph. After calling this method, there will not be an node in this graph with
     * the same id
     *
     * @param node the node to remove
     * @return whether the node could be removed from the graph
     */
    boolean removeNode(N node);

    /**
     * Order the nodes by the given comparator.
     * <p>
     * This will assign new ids to the nodes. After calling this method, the nodes will be numbered consecutively, with
     * the order imposed by the given comparator. If the comparator is null, no order is guaranteed.
     *
     * @param comparator the comparator to order the nodes by
     */
    void orderNodes(Comparator<N> comparator);

    /**
     * Order the edges by the given comparator.
     * <p>
     * This will assign new ids to the edges. After calling this method, the edges will be numbered consecutively, with
     * the order imposed by the given comparator. If the comparator is null, no order is guaranteed.
     *
     * @param comparator the comparator to order the edges by
     */
    void orderEdges(Comparator<E> comparator);

    /**
     * Get a collection of the outgoing edges for the given node. If the graph is undirected, all incident edges
     * to the given node will be included in the returned collection. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @param node the node to get the outgoing edges for
     * @return all outgoing edges for the given node
     */
    Collection<E> getOutgoingEdges(N node);

    /**
     * Get a collection of the incoming edges for the given node. If the graph is undirected, all incident edges
     * to the given node will be included in the returned collection. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @param node the node to get the incoming edges for
     * @return all incoming edges for the given node
     */
    Collection<E> getIncomingEdges(N node);

    /**
     * Get a collection of the incident edges for the given node. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @param node the node to get the incident edges for
     * @return all incident edges for the given node
     */
    Collection<E> getIncidentEdges(N node);

    /**
     * Get a collection of all nodes in the graph. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing nodes from it will not remove the nodes from the
     * graph (for this, use {@link #removeNode(Node)}).
     *
     * @return all nodes in the graph
     */
    Collection<N> getNodes();

    /**
     * Get a collection of all edges in the graph. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @return all edges in the graph
     */
    Collection<E> getEdges();

    /**
     * Get the information, whether the graph is directed, i.e., if it contains directed or undirected edges.
     * Directed edges lead from left to right by convention, i.e. the left node is the source and the right node is
     * the target of the edge.
     *
     * @return whether the graph is directed
     */
    boolean isDirected();

    @Override
    default Iterator<N> iterator() {
        return getNodes().iterator();
    }

    /**
     * Get an iterator for the edges
     * @return an edge iterator
     */
    default Iterator<E> edgeIterator() {
        return getEdges().iterator();
    }

    /**
     * Get edge by its nodes. The optional will be empty, when the edge does not exist. For directed edges, the nodes
     * must be provided in the correct order.
     * @param leftNode the left node, i.e., the start, of the edge
     * @param rightNode the right node, i.e., the end, of the edge
     * @return the corresponding edge, when it exists
     */
    default Optional<E> getEdge(N leftNode, N rightNode) {
        return this.getOutgoingEdges(leftNode).stream()
            .filter(a -> a.getRightNode().equals(rightNode) || (!a.isDirected()&& a.getLeftNode().equals(rightNode)))
            .findAny();
    }
}
