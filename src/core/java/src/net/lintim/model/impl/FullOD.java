package net.lintim.model.impl;

import net.lintim.model.OD;
import net.lintim.model.ODPair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implements the od interface using a two dimensional array. Therefore accessing the od pairs by index is cheap, but
 * getting nonempty od-pairs by {@link #getODPairs()} may be expensive.
 */
public class FullOD implements OD {
    /**
     * The data object to store the od pairs in. Remember to use index shifting for every raw access inside this class!
     */
    private final ODPair[][] matrix;

    /**
     * Create a new full od matrix. This matrix will store the od pairs in a two dimensional array. Therefore accessing
     * the od pairs by index is cheap, but getting nonempty od-pairs by {@link #getODPairs()} may be expensive.
     * @param size the size of the matrix
     */
    public  FullOD(int size){
        this.matrix = new ODPair[size][size];
    }
    @Override
    public double getValue(int origin, int destination) {
        throwForInvalidIndexPair(origin, destination);
        return matrix[origin-1][destination-1] != null ? matrix[origin-1][destination-1].getValue() : 0;
    }

    @Override
    public void setValue(int origin, int destination, double newValue) {
        throwForInvalidIndexPair(origin, destination);
        matrix[origin-1][destination-1] = new ODPair(origin, destination, newValue);
    }

    @Override
    public double computeNumberOfPassengers() {
        double sum = 0;
        for(int origin = 1; origin <= matrix.length; origin++){
            for(int destination = 1; destination <= matrix.length; destination++){
                sum += getValue(origin, destination);
            }
        }
        return sum;
    }

    @Override
    public List<ODPair> getODPairs() {
        return Arrays.stream(matrix) //Stream of arrays
            .flatMap(Arrays::stream) //Stream of od pairs
            .filter(Objects::nonNull) //May be null
            .filter(odPair -> odPair.getValue() > 0) //Choose only nonempty od pairs
            .collect(Collectors.toList());
    }

    @Override
    public Map<Integer, Collection<ODPair>> getODPairsByOriginID() {
        return IntStream.range(0, matrix.length).boxed().collect(
            Collectors.toMap(i -> i + 1, i -> Arrays.asList(matrix[i]))
        );
    }

    private void throwForInvalidIndexPair(int origin, int destination){
        if(matrix.length < origin || origin <= 0){
            throw new IndexOutOfBoundsException("Origin index " + origin + " is not in [1," + matrix.length + "] when" +
                " accessing od matrix.");
        }
        if(matrix.length < destination || destination <= 0){
            throw new IndexOutOfBoundsException("Destination index " + origin + " is not in [1," + matrix.length + "]" +
                "  when accessing od matrix.");
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Full OD:\n");
        for(ODPair odPair : getODPairs()){
            builder.append(odPair).append("\n");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FullOD fullOD = (FullOD) o;

        return Arrays.deepEquals(matrix, fullOD.matrix);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(matrix);
    }
}
