#ifndef EVALUATION_FUNCTION_HPP
#define EVALUATION_FUNCTION_HPP

#include "LineClusters.hpp"
#include "LineCluster.hpp"
#include "mergeRequest.hpp"
#include "superEdge.hpp"
#include <iostream>
#include <set>

namespace evaluation_function{

    int time_period = -1;
    int modulus(int i){
        i = i % time_period;
        while (i < 0) i+= time_period;
        return abs(i) % time_period;
    }

    void setTimePeriod(int t){
        time_period = t;
    }
    long long sum_it_up(superEdge &s, int time_shift){
        int ret = 0;
        for (std::pair<int, int> p: s.getValues()){
            ret += (long long) (modulus(p.first + time_shift) * p.second);
        }
        return ret;
    }

    mergeRequest objective1(LineCluster &l1, LineCluster &l2){
        if (time_period == -1) std::cout << "Set time period beforehand!" << std::endl;
        superEdge s1 = l1.getSuperEdge(l2.getId());
        superEdge s2 = l2.getSuperEdge(l1.getId());
        mergeRequest m;
        m.setLeftId(l1.getId());
        m.setRightId(l2.getId());

        int travellers = 0;
        std::set<int> potential_ts;
        for (std::pair<int, int> p: s1.getValues()){
            travellers += p.second;
            potential_ts.insert(modulus(-p.first));
        }

        for (std::pair<int, int> p: s2.getValues()){
            travellers += p.second;
            potential_ts.insert(modulus(p.first));
        }


        m.setNbrTravellers(travellers);

        long long minval = sum_it_up(s1, 0) + sum_it_up(s2, 0);
        int min_t = 0;
        if (travellers != 0){
            for (int t: potential_ts){
                long long val = sum_it_up(s1, t) + sum_it_up(s2, -t);
                if (val < minval){
                    minval = val;
                    min_t = t;
                }
            }
        }
        m.setStartDifference(min_t);
        m.setMinVal(minval);
        m.setObjVal(minval);
        return m;
    }
    mergeRequest objective_variance(LineCluster &l1, LineCluster &l2){
        if (time_period == -1) std::cout << "Set time period beforehand!" << std::endl;
        superEdge s1 = l1.getSuperEdge(l2.getId());
        superEdge s2 = l2.getSuperEdge(l1.getId());
        mergeRequest m;
        m.setLeftId(l1.getId());
        m.setRightId(l2.getId());

        int travellers = 0;
        for (std::pair<int, int> p: s1.getValues()){
            travellers += p.second;
        }
        for (std::pair<int, int> p: s2.getValues()){
            travellers += p.second;
        }
        m.setNbrTravellers(travellers);

        long long summe = 0;
        long long minval = sum_it_up(s1, 0) + sum_it_up(s2, 0);
        int min_t = 0;
        summe += minval;
        if (travellers != 0){
            for (int t = 1; t < time_period; t++){
                long long val = sum_it_up(s1, t) + sum_it_up(s2, -t);
                summe += val;
                if (val < minval){
                    minval = val;
                    min_t = t;
                }
            }
        }
        m.setStartDifference(min_t);
        m.setMinVal(minval);
        m.setObjVal(summe/time_period);
        return m;
    }

     bool get_coefficients(LineCluster &l1, LineCluster &l2, std::vector<long long > &ans){
        if (time_period == -1) std::cout << "Set time period beforehand!" << std::endl;
        superEdge s1 = l1.getSuperEdge(l2.getId());
        superEdge s2 = l2.getSuperEdge(l1.getId());
        if (s1.getValues().size() + s2.getValues().size() == 0) return false;
        for (int t = 0; t < time_period; t++){
            long long val = sum_it_up(s1, t) + sum_it_up(s2, -t);
            ans.push_back(val);
        }
        return true;
    }

    std::set<int> get_preferred(LineCluster &l1, LineCluster &l2){
        superEdge s1 = l1.getSuperEdge(l2.getId());
        superEdge s2 = l2.getSuperEdge(l1.getId());
        std::set<int> ret;
        for (std::pair<int, int> p: s1.getValues()){
            ret.insert(modulus(-p.first));
        }
        for (std::pair<int, int> p: s2.getValues()){
            ret.insert(modulus(p.first));
        }
        return ret;
    }


};


#endif
