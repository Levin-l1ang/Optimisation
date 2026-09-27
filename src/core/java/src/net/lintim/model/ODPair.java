package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;

/**
 * Class implementing a od pair, i.e., a triple of origin, destination and value.
 */
public class ODPair {
    protected int origin;
    protected int destination;
    protected double value;

    /**
     * Create a new od pair with the given attributes.
     * @param origin the origin of the od pair. This is the id of the node in the PTN, where the passengers start.
     * @param destination the destination of the od pair. This is the id of the node in the PTN, where the
     *                    passengers want to arrive at.
     * @param value the value of the od pair, i.e., how many passengers want to travel from origin to destination in
     *              the planning period.
     */
    public ODPair(int origin, int destination, double value){
        this.origin = origin;
        this.destination = destination;
        this.value = value;
    }

    /**
     * Get the origin of the od pair. This is the id of the node in the PTN, where the passengers start.
     * @return the id of the origin
     */
    public int getOrigin() {
        return origin;
    }

    /**
     * Get the destination of the od pair. This is the  id of the node in the PTN, where the passengers want to
     * arrive at.
     * @return the id of the destination
     */
    public int getDestination() {
        return destination;
    }

    /**
     * Get the value of the od pair, i.e., how many passengers want to travel from origin to destination in the
     * planning period.
     * @return the value of the od pair
     */
    public double getValue() {
        return value;
    }

    /**
     * Set the value of the od pair, i.e., how many passengers want to travel from origin to destination in the
     * planning period. The old value will be overwritten.
     * @param value the new value of the od pair
     */
    public void setValue(double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "OD " + Arrays.toString(toCsvStrings());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ODPair odPair = (ODPair) o;

        if (getOrigin() != odPair.getOrigin()) return false;
        if (getDestination() != odPair.getDestination()) return false;
        return Double.compare(odPair.getValue(), getValue()) == 0;
    }

    @Override
    public int hashCode() {
        int result = getOrigin();
        result = 31 * result + getDestination();
        return result;
    }

    /**
     * Return a string array, representing the od pair for a LinTim csv file
     * @return the csv representation of this od pair
     */
    public String[] toCsvStrings(){
        return new String[]{
            String.valueOf(origin),
            String.valueOf(destination),
            CsvWriter.shortenDecimalValueForOutput(value)
        };
    }
}
