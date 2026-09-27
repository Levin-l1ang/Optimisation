package net.lintim.timetabling.model;

import net.lintim.model.Node;

import java.util.Objects;

public class RoutingNode implements Node {

    private int id;
    private final int correspondingId;
    private final NodeType type;
    private final int startTime;

    public RoutingNode(int id, int correspondingId, int startTime, NodeType type) {
        this.id = id;
        this.correspondingId = correspondingId;
        this.type = type;
        this.startTime = startTime;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    public int getCorrespondingId() {
        return correspondingId;
    }

    public NodeType getType() {
        return type;
    }

    public int getStartTime() {
        return startTime;
    }

    public enum NodeType {
        DEPARTURE, ARRIVAL, START, END
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingNode that = (RoutingNode) o;
        return id == that.id &&
            correspondingId == that.correspondingId &&
            startTime == that.startTime &&
            type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, correspondingId, type, startTime);
    }

    @Override
    public String toString() {
        return "RoutingNode{" +
            "id=" + id +
            ", correspondingId=" + correspondingId +
            ", type=" + type +
            ", startTime=" + startTime +
            '}';
    }
}
