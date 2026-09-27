/**
 * Implementation of a Graph working with Adjacency Lists (via std::vector) per node
 *
 */
#ifndef ADJACENCYLISTGRAPH_HPP
#define ADJACENCYLISTGRAPH_HPP

#include "../Graph.hpp"
#include <algorithm>
#include <vector>
#include <map>
#include <functional>
#include <set>
#include <type_traits>
#include <iostream>

template<class N, class E>
class AdjacencyListGraph: public Graph<N, E>{

protected:
    std::vector<N*> nodes_ptr;
    std::vector<E*> edges_ptr;
    std::map <int, N> nodes;
    std::map <int, E> edges;
    /**
    *   maps the id of a node to the list of its outgoing edges
    */
    std::map<int, std::vector<int> > adjacency_list;
public:
    /**
     * Get the node with the specified id from the graph. If there is no node with the given id, null will be returned.
     *
     * @param id the id to search for
     * @return the node with the given id, or null if there is none
     */
    N* getNode(int id){
        if (id >= 0 && id < (int) nodes_ptr.size()) return nodes_ptr[id];
        else return nullptr;
    }

    /**
     * Get the edge with the specified id from the graph. If there is no edge with the given id, null will be returend.
     *
     * @param id the id to search for
     * @return the edge with the given id, or null if there is none
     */
    E* getEdge(int id){
        if (id >= 0 && id < (int) edges_ptr.size()) return edges_ptr[id];
        else return nullptr;
    }
    /**
     * Add the given node to the graph. There can not be multiple nodes with the same id in the same graph.
     *
     * @param node the edge to add to the network
     * @return whether the node could be added to the graph
     */
    bool addNode(N node){
        int id = node.getId();
        if (nodes.count(id)) return false;
        else{
            nodes.insert(std::make_pair(id,node));
            if (id >= (int) nodes_ptr.size()) nodes_ptr.resize(id + 1);
            nodes_ptr[id] = &nodes.at(id);
            return true;
        }
    }
    /**
     * Add the given edge to the graph. There can not be multiple edges with the same id in the same graph.
     *
     * @param edge the edge to add to the network
     * @return whether the edge could be added to the graph
     */
    bool addEdge(E edge){
        int id = edge.getId();
        if (this->edges.count(id)) return false;
        else{
            if (this->nodes.count(edge.getLeftNode()->getId())
                    && this->nodes.count(edge.getLeftNode()->getId())){
                this->edges.insert(std::make_pair(id,edge));
                adjacency_list[edge.getLeftNode()->getId()].push_back(edge.getId());
                if (!edge.isDirected()) adjacency_list[edge.getRightNode()->getId()].push_back(edge.getId());
                if (id >= (int) edges_ptr.size()) edges_ptr.resize(id + 1);
                edges_ptr[id] = &edges.at(id);
                return true;
            }
            else return false;
        }
    }

    /**
     * Remove the given edge from the graph. After calling this method, there will not be an edge in this graph with
     * the same id
     *
     * @param edge the edge to remove
     * @return whether the edge could be removed from the graph
     */
    bool removeEdge(E edge_to_delete){
        if (this->edges.erase(edge_to_delete.getId())){
            int from = edge_to_delete.getLeftNode()->getId();
            auto it2 = std::find(adjacency_list[from].begin(), adjacency_list[from].end(),
                edge_to_delete.getId());
            if (it2 != adjacency_list[from].end()){ 
                adjacency_list[from].erase(it2);
            }
            return true;
        }
        return false;
    }

    /**
     * Remove the given node from the graph. After calling this method, there will not be an node in this graph with
     * the same id
     *
     * @param node the node to remove
     * @return whether the node could be removed from the graph
     */
    bool removeNode(N node_to_delete){
        if (this->nodes.erase(node_to_delete.getId())){
            for (int i: adjacency_list[node_to_delete.getId()]){
                removeEdge(this->edges[i]);
            }
            adjacency_list.erase(node_to_delete.getId());
            return true;
        }
        return false;
    }

    /**
     * Get a collection of the incident edges for the given node. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @param node the node to get the incident edges for
     * @return all incident edges for the given node
     */
     std::vector<int> getIncidentEdges(N node){
         return adjacency_list[node.getId()];
     }

    /**
     * Get a collection of all nodes in the graph. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing nodes from it will not remove the nodes from the
     * graph (for this, use {@link #removeNode(Node)}).
     *
     * @return all nodes in the graph
     */
    std::vector<N> getNodes(){
    std::vector<N> ret;
    for (auto p: nodes) ret.push_back(p.second);
    return ret;
    }
    /**
     * Get a collection of all edges in the graph. Note that the returned collection is not a
     * reference to the underlying graph structure, i.e., removing edges from it will not remove the edges from the
     * graph (for this, use {@link #removeEdge(Edge)}).
     *
     * @return all edges in the graph
     */
    std::vector<E> getEdges(){
        std::vector<E> ret;
        for (auto p: edges) ret.push_back(p.second);
        return ret;
    }

    /**
     * Get the information, whether the graph is directed, i.e., if it contains directed or undirected edges.
     * Directed edges lead from left to right by convention, i.e. the left node is the source and the right node is
     * the target of the edge.
     *
     * @return whether the graph is directed
     */
    bool isDirected(){
        for(auto p: this->edges){
            if(p.second.isDirected()) return true;
        }
        return false;
    }

    int getNumberOfNodes(){
        return this->nodes.size();
    }
    int getNumberOfEdges(){
        return this->edges.size();
    }

    int getMaxEdgeId() {
        return this->edges.rbegin()->first;
    }

    int getMaxNodeId() {
        return this->nodes.rbegin()->first;
    }



    double getShortestPath(N &s, N &t, double (E::*value)(void)){
        std::map<int, bool> used;
        std::map<int, double> cur_value;
        cur_value[s.getId()] = 0;
        used[s.getId()] = true;
        std::set<std::pair<double, int> > heap;
        heap.insert(std::make_pair(0, s.getId()));
        while (!heap.empty()) {
            int cur = heap.begin()->second;
            if (cur == t.getId()) return heap.begin()->first;
            heap.erase(heap.begin());
            for (int edge_id: adjacency_list[cur]) {
                E* edge = getEdge(edge_id);
                int next = edge->getRightNode()->getId();
                auto c = std::bind(value, *edge);
                double cost = c();
                if (used[next] == 0 ||
                     (used[next] == 1 && cur_value[next] > cur_value[cur] + cost)) {
                    used[next] = 1;
                    heap.erase(std::make_pair(cur_value[next], next));
                    cur_value[next] = cur_value[cur] + cost;
                    heap.insert(std::make_pair(cur_value[next], next));
                }
            }
        }
        return 1e20;
    }


};

#endif
