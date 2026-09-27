package net.lintim.model;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Interface for an od matrix. Implementations can be found in {@link net.lintim.model.impl}.
 */
public interface OD extends Iterable<ODPair> {
    /**
     * Get the value for a specific origin, destination pair.
     * @param origin the id of the origin, i.e., the id of the node in the PTN, where the passengers start.
     * @param destination the id of the destination, i.e., the id of the node in the PTN, where the passengers want
     *                    to arrive at.
     * @return the value for the given od pair
     */
    double getValue(int origin, int destination);

    /**
     * Set the value for a specific origin, destination pair.
     * @param origin the id of the origin, i.e., the id of the node in the PTN, where the passengers start.
     * @param destination the id of the destination, i.e., the id of the node in the PTN, where the passengers want
     *                    to arrive at.
     * @param newValue the new value for the given od pair. The old value will be overwritten.
     */
    void setValue(int origin, int destination, double newValue);

    /**
     * Get the total number of passengers in the od matrix
     * @return the number of passengers
     */
    double computeNumberOfPassengers();

    /**
     * Get a collection of all od pairs, that have a non zero entry.
     * @return all non empty od pairs
     */
    Collection<ODPair> getODPairs();

    Map<Integer, Collection<ODPair>> getODPairsByOriginID();

    @Override
    default Iterator<ODPair> iterator() {
        return getODPairs().iterator();
    }
}
