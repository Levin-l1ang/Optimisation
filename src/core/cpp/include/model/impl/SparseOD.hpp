#ifndef SPARSEOD_HPP
#define SPARSEOD_HPP

#include "../OD.hpp"
#include "../ODPair.hpp"
#include "FullOD.hpp"
#include <vector>
#include <map>
#include <algorithm>
#include "../../exception/exceptions.hpp"

/**
 * Implementation of a sparse od matrix. Only use this class if the od matrix is sparse, since big matrices will
 * be very inefficient. In this case, use {@link FullOD}!
 */
 class SparseOD : public OD{
    /**
     * The data object to store the od pairs.
     */
private:
    std::vector<ODPair> odPairs;
    /**
     * The map of indices, i.e. every index for an od pair in the odPair list can be accessed through a Pair of its
     * origin and destination id, where origin is the key and destination is the value.
     */
    std::map<std::pair<int, int>, int> indices;

    /**
     * Get the od pair for the given origin destination pair or null, if there is none
     * @param origin the origin id
     * @param destination the destination id
     * @return the od pair or null, if there is none
     */
    ODPair* getODPair(int origin, int destination){

        if (indices.count(std::make_pair(origin, destination))){
            return &odPairs[indices[{origin, destination}]];
        }
        else{
            return nullptr;
        }
    }
public:
    /**
     * Create a new sparse OD matrix. Only use this class if the od matrix is sparse, since big matrices will
     * be very inefficient. In this case, use {@link FullOD}!
     * @param initialSize the initial size of the matrix
     */
    SparseOD(){
    }

    double getValue(int origin, int destination) {
        ODPair* pair = getODPair(origin, destination);
        return  pair == NULL ? 0 : pair->getValue();
    }


    void setValue(int origin, int destination, double newValue) {
        ODPair* pair = getODPair(origin, destination);
        if(pair != nullptr){
            pair->setValue(newValue);
        }
        else {
            ODPair newpair = ODPair(origin, destination, newValue);
            int newIndex = odPairs.size();
            odPairs.push_back(newpair);
            indices[{origin, destination}] = newIndex;
        }

    }

     double computeNumberOfPassengers() {
        double numberOfPassengers = 0;
        for(ODPair &pair : odPairs){
            numberOfPassengers += pair.getValue();
        }
        return numberOfPassengers;
    }

    std::vector<ODPair> getODPairs() {
        return odPairs;
    }
    FullOD toFullOD(){
        int max_station = 0;
        for (ODPair &odp: odPairs){
            max_station = std::max(max_station, odp.getOrigin());
            max_station = std::max(max_station, odp.getDestination());
        }
        int size = max_station;
        FullOD ret(size);
        for (ODPair &odp: odPairs){
            ret.setValue(odp.getOrigin(),odp.getDestination(),odp.getValue());
        }
        return ret;
    }
};
#endif
