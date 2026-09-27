/**
 * The template for an edge object for a graph structure. Used in the weighted and unweighted case.
 */
#ifndef EDGE_HPP
#define EDGE_HPP

#include "Node.hpp"
#include <type_traits>

template<class N>
class Edge{
public:
    Edge(){
        static_assert(std::is_base_of<Node, N>::value, "N must derive from Node to initialize Graph");
    }

    virtual int getId() = 0;

    virtual void setId(int id) = 0;

    virtual N* getLeftNode() = 0;

    virtual N* getRightNode() = 0;

    virtual bool isDirected() = 0;

    friend bool operator==(Edge& a, Edge& b){
        if(a.getId() == b.getId()) return true;
        else return false;
    }

    friend bool operator!=(Edge& a, Edge& b){
        if(a.getId() == b.getId()) return false;
        else return true;
    }
};

#endif
