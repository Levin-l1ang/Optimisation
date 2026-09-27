package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;
import java.util.Objects;

/**
 * ==!! This Class is deprecated !! Should no longer be used !!==
 *
 * Representation of an infrastructure edge, e.g., a street or a track.
 */
public class InfrastructureEdge implements Edge<InfrastructureNode> {

    private int edgeId;
    private final InfrastructureNode leftNode;
    private final InfrastructureNode rightNode;
    private double length;
    private int lowerBound;
    private int upperBound;
    private boolean directed;

    public InfrastructureEdge(int edgeId, InfrastructureNode leftNode, InfrastructureNode rightNode, double length, int lowerBound, int upperBound, boolean directed) {
        this.edgeId = edgeId;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.length = length;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.directed = directed;
    }

    @Override
    public int getId() {
        return edgeId;
    }

    @Override
    public void setId(int id) {
        this.edgeId = id;
    }

    @Override
    public InfrastructureNode getLeftNode() {
        return leftNode;
    }

    @Override
    public InfrastructureNode getRightNode() {
        return rightNode;
    }

    @Override
    public boolean isDirected() {
        return directed;
    }

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(int lowerBound) {
        this.lowerBound = lowerBound;
    }

    public int getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(int upperBound) {
        this.upperBound = upperBound;
    }

    public void setDirected(boolean directed) {
        this.directed = directed;
    }

    public String[] toCsvStrings() {
        return toCsvStrings(1);
    }

    public String[] toCsvStrings(double conversionFactorLength) {
        return new String[] {
            String.valueOf(edgeId),
            String.valueOf(leftNode.getId()),
            String.valueOf(rightNode.getId()),
            CsvWriter.shortenDecimalValueForOutput(length / conversionFactorLength),
            String.valueOf(lowerBound),
            String.valueOf(upperBound)
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InfrastructureEdge that = (InfrastructureEdge) o;
        return edgeId == that.edgeId &&
            Double.compare(that.length, length) == 0 &&
            lowerBound == that.lowerBound &&
            upperBound == that.upperBound &&
            directed == that.directed &&
            Objects.equals(leftNode, that.leftNode) &&
            Objects.equals(rightNode, that.rightNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeId, leftNode, rightNode);
    }

    @Override
    public String toString() {
        return "InfrastructureEdge " + Arrays.toString(toCsvStrings());
    }
}
