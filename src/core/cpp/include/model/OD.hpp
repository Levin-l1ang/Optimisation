#ifndef OD_HPP
#define OD_HPP

/**
 * Interface for an od matrix. Implementations can be found in {@link net.lintim.model.impl}.
 */
#include <vector>
#include "ODPair.hpp"

class OD{
public:
    /**
     * Get the value for a specific origin, destination pair.
     * @param origin the id of the origin, i.e., the id of the node in the PTN, where the passengers start.
     * @param destination the id of the destination, i.e., the id of the node in the PTN, where the passengers want
     *                    to arrive at.
     * @return the value for the given od pair
     */
    virtual double getValue(int origin, int destination) = 0;

    /**
     * Set the value for a specific origin, destination pair.
     * @param origin the id of the origin, i.e., the id of the node in the PTN, where the passengers start.
     * @param destination the id of the destination, i.e., the id of the node in the PTN, where the passengers want
     *                    to arrive at.
     * @param newValue the new value for the given od pair. The old value will be overwritten.
     */
    virtual void setValue(int origin, int destination, double newValue) = 0;

    /**
     * Get the total number of passengers in the od matrix
     * @return the number of passengers
     */
    double computeNumberOfPassengers();

    /**
     * Get a collection of all od pairs, that have a non zero entry.
     * @return all non empty od pairs
     */
    virtual std::vector <ODPair> getODPairs() = 0;
};

#endif
