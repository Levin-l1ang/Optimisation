package net.lintim.timetabling.model;

import net.lintim.exception.LinTimException;
import net.lintim.model.Edge;
import net.lintim.model.PeriodicActivity;

import java.util.Objects;

public class RoutingEdge implements Edge<RoutingNode> {

    private int id;
    private final RoutingNode leftNode;
    private final RoutingNode rightNode;
    private final double length;
    private final EdgeType type;
    private final PeriodicActivity activity;
    private double weight;

    public RoutingEdge(int id, RoutingNode leftNode, RoutingNode rightNode, double length, EdgeType type,
                       PeriodicActivity activity) {
        this.id = id;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.length = length;
        this.type = type;
        this.activity = activity;
        this.weight = 0;
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

    public double getLength() {
        return length;
    }

    public double getLength(double walkingUtility, double waitingUtility, int changePenalty, double changeUtility) {
        switch (type) {
            case WALK:
                return walkingUtility * getLength();
            case DRIVE:
            case WAIT:
                return getLength();
            case CHANGE:
                return getLength() * changeUtility + changePenalty;
            case WAIT_AT_START:
                return waitingUtility * getLength();
            default:
                throw new LinTimException("Unknwon edge type");
        }
    }

    public EdgeType getType() {
        return type;
    }

    public PeriodicActivity getActivity() {
        return activity;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public void addWeight(double weight) {
        this.weight += weight;
    }

    public enum EdgeType {
        WAIT, WALK, DRIVE, WAIT_AT_START, CHANGE
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingEdge edge = (RoutingEdge) o;
        return id == edge.id &&
            Double.compare(edge.length, length) == 0 &&
            Objects.equals(leftNode, edge.leftNode) &&
            Objects.equals(rightNode, edge.rightNode) &&
            type == edge.type &&
            Objects.equals(activity, edge.activity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, leftNode, rightNode, length, type, activity);
    }

    @Override
    public String toString() {
        return "RoutingEdge{" +
            "id=" + id +
            ", leftNode=" + leftNode +
            ", rightNode=" + rightNode +
            ", length=" + length +
            ", type=" + type +
            ", activity=" + activity +
            ", weight=" + weight +
            '}';
    }
}
