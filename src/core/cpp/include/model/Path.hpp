/**
 * Directed Path Class in a Graph
 */
#ifndef PATH_HPP
#define PATH_HPP

#include "Node.hpp"
#include "Edge.hpp"
#include <vector>

template<class N, class E>
class Path{
protected:
    bool directed;
public:
    Path(bool directed){
        this->directed = directed;
        static_assert(std::is_base_of<Node, N>::value, "N must derive from Node to initialize Graph");
        static_assert(std::is_base_of<Edge<N>, E>::value, "E must derive from Edge<N> to initialize Graph");
    }
    /**
    *   @return vector of all nodes in the path.
    */
    std::vector <N> getNodes();
    /**
    *   @return vector of all edges in the path.
    */
    std::vector <E> getEdges();

    /**
    *   @return if the path is directed
    */
    bool isDirected();

    /**
    *   adds given edge at the front of the list, if one of the edges' nodes
    *   matches the current first node
    *   @return 0 if success
    */
    bool addFirst(E edge);
    /**
    *   applies addFirst to the list of edges
    *   @return 0 if success
    */
    bool addFirst(std::vector<E> edges);
    /**
    *   adds given edge at the back of the list, if one of the edges' nodes
    *   matches the current last node
    *   @return 0 if success
    */
    bool addLast(E edge);
    /**
    *   applies addLast to the list of edges
    *   @return 0 if success
    */
    bool addLast(std::vector<E> edges);

    /**
    *   Removes an edge from the path. This is only possible if the edge is
    *   either at the beginning or the end of the path.
    *   @return 0 if success
    */
    bool remove(E edge);
    /**
    *   Applies remove to every edge in the list.
    *   @return false if success
    */
    bool remove(std::vector<E> edges);
    /**
    *   @return true if there is a edge with the same ID.
    */
    bool containsEdge(int edge_id);
    /**
    *   @return true if there is a node with the same ID.
    */
    bool containsNode(int node_id);


};


#endif
