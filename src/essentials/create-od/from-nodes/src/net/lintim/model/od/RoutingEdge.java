package net.lintim.model.od;

import net.lintim.model.Edge;

import java.util.Objects;

public class RoutingEdge implements Edge<RoutingNode> {

    private int id;
    private RoutingNode leftNode;
    private RoutingNode rightNode;
    private double length;
    private EdgeType type;

    public RoutingEdge(int id, RoutingNode leftNode, RoutingNode rightNode, double length, EdgeType type) {
        this.id = id;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.length = length;
        this.type = type;
    }

    public double getLength() {
        return length;
    }

    public EdgeType getType() {
        return type;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public RoutingNode getLeftNode() {
        return leftNode;
    }

    @Override
    public RoutingNode getRightNode() {
        return rightNode;
    }

    @Override
    public boolean isDirected() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingEdge that = (RoutingEdge) o;
        return id == that.id &&
            Double.compare(that.length, length) == 0 &&
            Objects.equals(leftNode, that.leftNode) &&
            Objects.equals(rightNode, that.rightNode) &&
            Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, leftNode, rightNode, length, type);
    }

    @Override
    public String toString() {
        return "RoutingEdge{" +
            "id=" + id +
            ", leftNode=" + leftNode +
            ", rightNode=" + rightNode +
            ", length=" + length +
            ", type=" + type +
            '}';
    }

    public enum EdgeType {
        WAIT, WALK, DRIVE
    }
}
