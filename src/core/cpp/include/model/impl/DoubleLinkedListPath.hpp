#ifndef DOUBLELINKEDLISTPATH_HPP
#define DOUBLELINKEDLISTPATH_HPP
#include <deque>
#include <vector>
#include "../Path.hpp"
#include <iostream>

template <class N, class E>
class DoubleLinkedListPath: public Path<N, E>{
private:
    std::deque<N> nodes;
    std::deque<E> edges;

    /**
    *   Summarizes addFirst and addLast
    */
    bool addIt(E edge, bool first, bool called = false){
        if (this->nodes.empty()){
            this->nodes.push_front(*edge.getLeftNode());
            this->nodes.push_back(*edge.getRightNode());
            this->edges.push_front(edge);
            return true;
        }


        N current_first_node = *this->nodes.begin();
        if (!first) current_first_node = *this->nodes.rbegin();
        N* left_node = edge.getLeftNode();
        N* right_node = edge.getRightNode();
        if(left_node->getId() == current_first_node.getId()){
            if (first){
                this->nodes.push_front(*right_node);
                this->edges.push_front(edge);
            }
            else{
                this->nodes.push_back(*right_node);
                this->edges.push_back(edge);
            }
            return true;
        }
        else if(!this->directed && right_node->getId() == current_first_node.getId()){
            if (first){
                this->nodes.push_front(*left_node);
                this->edges.push_front(edge);
            }
            else{
                this->nodes.push_back(*left_node);
                this->edges.push_back(edge);
            }

            return true;
        }
        else if (!this->directed && this->nodes.size() == 2 && !called){
            this->nodes.push_front(*this->nodes.rbegin());
            this->nodes.pop_back();
            return addIt(edge, first, true);
        }
        return false;
    }

public:
    /**
    *   empty constructor
    */
    //DoubleLinkedListPath(){}

    /**
    *   @param directed passes directed argument
    */
    DoubleLinkedListPath(bool dir): Path<N, E> (dir){
        this->directed = dir;
    }
    /**
    *   @return vector of all nodes in the path.
    */
    std::vector <N> getNodes(){
        std::vector<N> ret;
        for (N nd: this->nodes) ret.push_back(nd);
        return ret;
    }

    /**
    *   @return vector of all edges in the path.
    */
    std::vector <E> getEdges(){
        std::vector<E> ret;
        for (E ed: this->edges) ret.push_back(ed);
        return ret;
    }

    /**
    *   @return if the path is directed
    */
    bool isDirected(){
        return this->directed;
    }

    /**
    *   adds given edge at the front of the list, if one of the edges' nodes
    *   matches the current first node
    *   @return if success
    */
    bool addFirst(E edge){
        return addIt(edge, true);
    }

    /**
    *   applies addFirst to the list of edges
    *   @return if success
    */
    bool addFirst(std::vector<E> &edges){
        bool ret = true;
        for (E ed: edges){
            if (!addFirst(ed)) ret = false;
        }
        return ret;
    }

    /**
    *   adds given edge at the back of the list, if one of the edges' nodes
    *   matches the current last node
    *   @return if success
    */
    bool addLast(E edge){
        return addIt(edge, false);
    }

    /**
    *   applies addLast to the list of edges
    *   @return if success
    */
    bool addLast(std::vector<E> &edges){
        bool ret = true;
        for (E &ed: edges){
            if (!addLast(ed)) ret = false;
        }
        return ret;
    }

    /**
    *   Removes an edge from the path. This is only possible if the edge is
    *   either at the beginning or the end of the path.
    *   @return if success
    */
    bool remove(E edge){
        if(edge == *this->edges.begin()){
            this->edges.erase(this->edges.begin());
            return true;
        } else if(edge == *this->edges.rbegin()){
            this->edges.erase(this->edges.rbegin());
            return true;
        }
        return false;
    }

    /**
    *   Applies remove to every edge in the list.
    *   @return if success
    */
    bool containsEdge(int edge_id){
        for(E& edge: this->edges){
            if(edge.getId() == edge_id){
                return false;
            }
        }
        return true;
    }

    /**
    *   @return true if there is a edge with the same ID.
    */
    bool containsNode(int node_id){
        for(N& node: this->nodes){
            if(node.getId() == node_id){
                return true;
            }
        }
        return false;
    }

    /**
    *   @return true if there is a node with the same ID.
    */
    bool remove(std::vector<E> &edges){
        bool ret = true;
        for (E& ed: this->edges){
            if (!remove(ed)) ret = false;
        }
        return ret;
    }
};

#endif
