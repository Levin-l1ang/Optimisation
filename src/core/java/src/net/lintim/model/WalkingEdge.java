package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;
import java.util.Objects;

/**
 * ==!! This Class is deprecated !! Should no longer be used !!==
 *
 * Class representing walking edges, i.e., connections between different {@link InfrastructureNode} that are suitable
 * for a passenger to walk between.
 */
public class WalkingEdge implements Edge<InfrastructureNode> {

    private int edgeId;
    private final InfrastructureNode leftNode;
    private final InfrastructureNode rightNode;
    private double length;
    private boolean directed;

    public WalkingEdge(int edgeId, InfrastructureNode leftNode, InfrastructureNode rightNode, double length,
                       boolean directed) {
        this.edgeId = edgeId;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.length = length;
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

    /**
     *
     * @return walking time of the edge in seconds
     */
    public double getLength() {
        return length;
    }

    /**
     *
     * @param length walking time in seconds
     */
    public void setLength(double length) {
        this.length = length;
    }

    public void setDirected(boolean directed) {
        this.directed = directed;
    }

    public String[] toCsvStrings() {
        return new String[] {
            String.valueOf(edgeId),
            String.valueOf(leftNode.getId()),
            String.valueOf(rightNode.getId()),
            CsvWriter.shortenDecimalValueForOutput(length)
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WalkingEdge that = (WalkingEdge) o;
        return edgeId == that.edgeId &&
            Double.compare(that.length, length) == 0 &&
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
        return "WalkingEdge " + Arrays.toString(toCsvStrings());
    }
}
