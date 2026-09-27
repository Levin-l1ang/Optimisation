/**
 * The template for a graph structure. There are default implementations of this interface, namely
 * {@link net.lintim.model.impl.Graph}. More implementations may follow. Choose the appropriate implementation based on
 * your graph structure or implement your own.
 *
 */
#ifndef GRAPH_HPP
#define GRAPH_HPP

#include "Node.hpp"
#include "Edge.hpp"
#include <vector>
#include <map>
#include <type_traits>

template<class N, class E>
class Graph{
public:
    Graph(){
        static_assert(std::is_base_of<Node, N>::value, "N must derive from Node to initialize Graph");
        static_assert(std::is_base_of<Edge<N>, E>::value, "E must derive from Edge<N> to initialize Graph");
    }

    /**
     * Get the node with the specified id from the graph. If there is no node with the given id, null will be returned.
     *
     * @param id the id to search for
     * @return the node with the given id, or null if there is none
     */
    N* getNode(int id);

    /**
     * Get the edge with the specified id from the graph. If there is no edge with the given id, null will be returend.
     *
     * @param id the id to search for
     * @return the edge with the given id, or null if there is none
     */
    E* getEdge(int id);

    /**
     * Get a node from the graph on which the given function takes the given value
     *
     * @param map the function whose value must be matched
     * @param value the value the function must take on the node to be returned
     * @param <O> type of function image and value
     * @return one of the nodes on which the provided function yields the provided value, or null if none exists
     */
     template <typename O> N getNode(O (*map) (N), O);


    /**
     * Get an edge from the graph on which the given function takes the given value
     *
     * @param map the function whose value must be matched
     * @param value the value the function must take on the edge to be returned
     * @param <O> type of function image and value
     * @return one of the edges on which the provided function yields the provided value, or null if none exists
     */
    template <typename O> E getEdge(O (*map) (E), O);


    /**
     * Add the given edge to the graph. There can not be multiple edges with the same id in the same graph.
     *
     * @param edge the edge to add to the network
     * @return whether the edge could be added to the graph
     */
    bool addEdge(E edge);

    /**
     * Add the given node to the graph. There can not be multiple nodes with the same id in the same graph.
     *
     * @param node the edge to add to the network
     * @return whether the node could be added to the graph
     */
    bool addNode(N node);

    /**
     * Remove the given edge from the graph. After calling this method, there will not be an edge in this graph with
     * the same id
     *
     * @param edge the edge to remove
     * @return whether the edge could be removed from the graph
     */
    bool removeEdge(E &edge_to_delete);
    /**
     * Remove the given node from the graph. After calling this method, there will not be an node in this graph with
     * the same id
     *
     * @param node the node to remove
     * @return whether the node could be removed from the graph
     */
    bool removeNode(N &node_to_delete);

    /**
     * Order the nodes by the given comparator.
     * <p>
     * This will assign new ids to the nodes. After calling this method, the nodes will be numbered consecutively, with
     * the order imposed by the given comparator. If the comparator is null, no order is guaranteed.
     *
     * @param comparator the comparator to order the nodes by
     */

    void orderNodes(bool (comparator) (N, N));

    /**
     * Order the edges by the given comparator.
     * <p>
     * This will assign new ids to the edges. After calling this method, the edges will be numbered consecutively, with
     * the order imposed by the given comparator. If the comparator is null, no order is guaranteed.
     *
     * @param comparator the comparator to order the edges by
     */
    void orderEdges(bool (comparator)(E, E));

    /**
     * Get a collection of the outgoing edges for the given node. If the graph is undirected, all incident edges
     * to the given node will be included in the returned collection. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @param node the node to get the outgoing edges for
     * @return all outgoing edges for the given node
     */
    std::vector<E> getOutgoingEdges(N node);

    /**
     * Get a collection of the incoming edges for the given node. If the graph is undirected, all incident edges
     * to the given node will be included in the returned collection. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @param node the node to get the incoming edges for
     * @return all incoming edges for the given node
     */
    std::vector<E> getIncomingEdges(N node);

    /**
     * Get a collection of the incident edges for the given node. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @param node the node to get the incident edges for
     * @return all incident edges for the given node
     */
    std::vector<E> getIncidentEdges(N node);

    /**
    * Get a collection of all nodes in the graph. Note that the returned collection is not a
    * reference to the underlying graph structure, i.e., removing nodes from it will not remove the nodes from the
    * graph (for this, use {@link #removeNode(Node)}).
    *
    * @return all nodes in the graph
    */
    std::vector<N> getNodes(void);

    /**
     * Get a collection of all edges in the graph. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @return all edges in the graph
     */
    std::vector<E> getEdges(void);

    /**
     * Get the information, whether the graph is directed, i.e., if it contains directed or undirected edges.
     * Directed edges lead from left to right by convention, i.e. the left node is the source and the right node is
     * the target of the edge.
     *
     * @return whether the graph is directed
     */
    bool isDirected(void);
};

#endif
