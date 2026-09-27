#ifndef LINECLUSTER_HPP
#define LINECLUSTER_HPP
#include <map>
#include <vector>
#include "RpttsLine.hpp"
#include "superEdge.hpp"
class LineCluster{
private:
    int identity;
    std::vector<RpttsLine> lines;
    std::vector<superEdge> superEdges;

public:
    LineCluster(){}

    int getId(){
        return identity;
    }
    void setId(int i){
        identity = i;
    }

    std::vector<RpttsLine> getLines(){
        return this->lines;
    }

    bool addLine(RpttsLine l){
        for (RpttsLine l2: lines){
            if (l2 == l) return true;
        }
        lines.push_back(l);
        return false;
    }
    std::vector<superEdge> getSuperEdges(){
        return superEdges;
    }
    superEdge getSuperEdge(int id){
        for (superEdge &se: superEdges){
            if (se.getTo() == id){
                return se;
            }
        }
        return superEdge(identity, id);
    }

    void removeSuperEdge(int i){
        for (superEdge &se: superEdges){
            if (se.getTo() == i){
                se.removeAllValues();
                return;
            }
        }
    }

    void addSuperEdgeValues(std::vector<std::pair<int, int> > vals, int id, int time_shift){
        for (superEdge &se: superEdges){
            if (se.getTo() == id){
                for (std::pair<int, int> p: vals){
                    se.addPair(p.first, p.second, time_shift);
                }
                return;
            }
        }
        superEdge se = superEdge(identity, id);
        for (std::pair<int, int> p: vals){
            se.addPair(p.first, p.second, time_shift);
        }
        superEdges.push_back(se);

    }
    void addSuperEdgeValue(int xval, int weight, int to){
        addSuperEdgeValues({{xval, weight}}, to, 0);
    }

    friend bool operator ==(LineCluster& a, LineCluster& b){
        if (a.getId() == b.getId()) return true;
        else return false;
    }
    friend bool operator != (LineCluster a, LineCluster b){
        return !(a==b);
    }

};


#endif
