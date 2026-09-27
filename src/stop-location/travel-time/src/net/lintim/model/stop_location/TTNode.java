package net.lintim.model.stop_location;

import net.lintim.model.InfrastructureNode;
import net.lintim.model.Node;

import java.util.Objects;

public class TTNode implements Node {

    private int nodeId;
    private NodeType type;
    private InfrastructureNode originalNode;

    public TTNode(int nodeId, NodeType type, InfrastructureNode originalNode) {
        this.nodeId = nodeId;
        this.type = type;
        this.originalNode = originalNode;
    }

    @Override
    public int getId() {
        return nodeId;
    }

    @Override
    public void setId(int id) {
        this.nodeId = id;
    }

    public NodeType getType() {
        return type;
    }

    public InfrastructureNode getOriginalNode() {
        return originalNode;
    }

    public enum NodeType {
        IN, OUT, OD
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TTNode ttNode = (TTNode) o;
        return nodeId == ttNode.nodeId &&
            type == ttNode.type &&
            Objects.equals(originalNode, ttNode.originalNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, type, originalNode);
    }

    @Override
    public String toString() {
        return "(" + originalNode.getId() + "," + type + ")";
    }
}
