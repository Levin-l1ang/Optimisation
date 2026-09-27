#ifndef LINECLUSTERS_HPP
#define LINECLUSTERS_HPP

#include "evaluation_function.hpp"
#include "LineCluster.hpp"
#include "mergeRequest.hpp"
#include <vector>
#include <iostream>
#include <map>
#include <set>
#include <functional>


class LineClusters{
private:
    std::vector<LineCluster> Clusters;
public:

    int getNumberOfClusters(){
        return (int) Clusters.size();
    }

    LineCluster* getLineCluster(int id){
        for (LineCluster &l: Clusters){
            if (l.getId() == id) return &l;
        }
        std::cout << "Could not find line cluster with id " << id << std::endl;
        return nullptr;
    }

    std::vector<LineCluster> getLineClusters(){
        return Clusters;
    }
    void addLineCluster(LineCluster l_to_add){
        for (LineCluster &l: Clusters){
            if (l.getId() == l_to_add.getId()){
                std::cout << "line cluster with id " << l_to_add.getId() << " already exists!" << std::endl;
                return;
            }
        }
        Clusters.push_back(l_to_add);
    }
    void removeLineCluster(int id){
        for (auto it = Clusters.begin(); it != Clusters.end(); it++){
            if (it->getId() == id){
                Clusters.erase(it);
                return;
            }
        }
        std::cout << "Could not find a cluster to remove with id " << id << std::endl;
    }
    void mergeClusters(int l, int r, int diff){
        // convert xvalues and weights
        LineCluster* l1 = getLineCluster(l);
        LineCluster* l2 = getLineCluster(r);

        // transform all stops from l2 to stops from l1
        for (superEdge &s: l2->getSuperEdges()){
            if (s.getTo() == l) continue;
            l1->addSuperEdgeValues(s.getValues(), s.getTo(), -diff);
            l2->removeSuperEdge(s.getTo());
        }
        // transform all stops to l2 to stops to l1
        for (LineCluster &L: Clusters){
            if (L.getId() == l || L.getId() == r) continue;
            superEdge s = L.getSuperEdge(l2->getId());
            L.addSuperEdgeValues(s.getValues(), l1->getId(), diff);
            L.removeSuperEdge(l2->getId());
        }
        removeLineCluster(l2->getId());
    }

    std::vector<mergeRequest> evaluate_clusters(long long obj_val){
        mergeRequest maxi;
        maxi.setObjVal(-1);
        maxi.setNbrTravellers(100000);
        for (LineCluster l: Clusters){
            for (LineCluster l2: Clusters){
                if (l.getId() >= l2.getId()) continue;
                mergeRequest m = evaluation_function::objective_variance(l, l2);
                if (m > maxi){
                    maxi = m;
                }
            }
        }
        return {maxi};
    }
    // work in progress ;-)
    std::vector<mergeRequest> evaluate_by_matching(long long obj_val);
    std::vector<mergeRequest> evaluate_by_ip(long long obj_val, int time_period, std::map<int, int> &start_times);
};


#endif
