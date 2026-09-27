package net.lintim.model.impl;

import net.lintim.model.OD;
import net.lintim.model.ODPair;
import net.lintim.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of a sparse od matrix. Only use this class if the od matrix is sparse, since big matrices will
 * be very inefficient. In this case, use {@link FullOD}!
 */
public class SparseOD implements OD{
    /**
     * The data object to store the od pairs.
     */
    private final ArrayList<ODPair> odPairs;
    /**
     * The map of indices, i.e. every index for an od pair in the odPair list can be accessed through a Pair of its
     * origin and destination id, where origin is the key and destination is the value.
     */
    private final HashMap<Pair<Integer, Integer>, Integer> indices;

    /**
     * Create a new sparse OD matrix. Only use this class if the od matrix is sparse, since big matrices will
     * be very inefficient. In this case, use {@link FullOD}!
     * @param initialSize the initial size of the matrix
     */
    public SparseOD(int initialSize){
        this.odPairs = new ArrayList<>(initialSize);
        this.indices = new HashMap<>(initialSize);
    }

    @Override
    public double getValue(int origin, int destination) {
        ODPair pair = getODPair(origin, destination);
        return  pair == null ? 0 : pair.getValue();
    }

    @Override
    public void setValue(int origin, int destination, double newValue) {
        ODPair pair = getODPair(origin, destination);
        if(pair != null){
            pair.setValue(newValue);
        }
        else {
            pair = new ODPair(origin, destination, newValue);
            int newIndex = odPairs.size();
            odPairs.add(pair);
            indices.put(new Pair<>(origin, destination), newIndex);
        }

    }

    /**
     * Get the od pair for the given origin destination pair or null, if there is none
     * @param origin the origin id
     * @param destination the destination id
     * @return the od pair or null, if there is none
     */
    private ODPair getODPair(int origin, int destination){
        Integer index = indices.get(new Pair<>(origin, destination));
        return index == null ? null : odPairs.get(index);
    }

    @Override
    public double computeNumberOfPassengers() {
        double numberOfPassengers = 0;
        for(ODPair pair : odPairs){
            numberOfPassengers += pair.getValue();
        }
        return numberOfPassengers;
    }

    @Override
    public List<ODPair> getODPairs() {
        return odPairs.stream().filter(odPair -> odPair.getValue() > 0).collect(Collectors.toList());
    }

    @Override
    public Map<Integer, Collection<ODPair>> getODPairsByOriginID() {
        Map<Integer, Collection<ODPair>> odPairsByOriginID = new HashMap<>();
        for (ODPair pair : odPairs) {
            int originId = pair.getOrigin();
            if (!odPairsByOriginID.containsKey(originId)) {
                odPairsByOriginID.put(originId, new ArrayList<>());
            }
            odPairsByOriginID.get(originId).add(pair);
        }
        return odPairsByOriginID;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Sparse OD:\n");
        for(ODPair odPair : getODPairs()){
            builder.append(odPair).append("\n");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SparseOD sparseOD = (SparseOD) o;

        if(odPairs != null) {
            // Only compare the od pairs with weight > 0
            List<ODPair> thisOdPairs = getODPairs();
            List<ODPair> otherODPairs = sparseOD.getODPairs();
            if(thisOdPairs.size() == otherODPairs.size()){
                // The lists are potentially of equal size. Convert to set and compare
                HashSet<ODPair> thisOdSet = new HashSet<>(thisOdPairs);
                HashSet<ODPair> otherOdSet = new HashSet<>(thisOdSet);
                return thisOdSet.equals(otherOdSet);
            }
            return false;
        }
        return sparseOD.odPairs == null;
    }

    @Override
    public int hashCode() {
        HashSet<ODPair> odPairSet = new HashSet<>(getODPairs());
        return odPairSet.hashCode();
    }
}
