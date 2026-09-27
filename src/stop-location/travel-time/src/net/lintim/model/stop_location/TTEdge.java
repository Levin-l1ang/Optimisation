package net.lintim.model.stop_location;

import net.lintim.model.Edge;

import java.util.Objects;

public class TTEdge implements Edge<TTNode> {

    private int edgeId;
    private TTNode leftNode;
    private TTNode rightNode;
    private double length;
    private EdgeType type;

    public TTEdge(int edgeId, TTNode leftNode, TTNode rightNode, double length, EdgeType type) {
        this.edgeId = edgeId;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.length = length;
        this.type = type;
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
    public TTNode getLeftNode() {
        return leftNode;
    }

    @Override
    public TTNode getRightNode() {
        return rightNode;
    }

    @Override
    public boolean isDirected() {
        return true;
    }

    public double getLength() {
        return length;
    }

    public EdgeType getType() {
        return type;
    }

    public enum EdgeType {
        DRIVE, WAIT, WALK
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TTEdge ttEdge = (TTEdge) o;
        return edgeId == ttEdge.edgeId &&
            Double.compare(ttEdge.length, length) == 0 &&
            Objects.equals(leftNode, ttEdge.leftNode) &&
            Objects.equals(rightNode, ttEdge.rightNode) &&
            type == ttEdge.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeId, leftNode, rightNode, length, type);
    }

    @Override
    public String toString() {
        return "(" + type + "," + leftNode + "," + rightNode + ")";
    }
}
