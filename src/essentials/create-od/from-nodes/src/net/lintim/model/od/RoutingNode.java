package net.lintim.model.od;

import net.lintim.model.InfrastructureNode;
import net.lintim.model.Node;

import java.util.Objects;

public class RoutingNode implements Node {

    private int id;
    private final InfrastructureNode originalNode;
    private final NodeType type;

    public RoutingNode(int id, InfrastructureNode originalNode, NodeType type) {
        this.id = id;
        this.originalNode = originalNode;
        this.type = type;
    }

    public InfrastructureNode getOriginalNode() {
        return originalNode;
    }

    public NodeType getType() {
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

    public enum NodeType {
        IN, OUT, BOARD, ALIGHT
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingNode that = (RoutingNode) o;
        return id == that.id &&
            Objects.equals(originalNode, that.originalNode) &&
            type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, originalNode, type);
    }

    @Override
    public String toString() {
        return "RoutingNode{" +
            "id=" + id +
            ", originalNode=" + originalNode +
            ", type=" + type +
            '}';
    }
}
