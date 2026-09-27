package net.lintim.model.impl;

import net.lintim.model.Edge;

import java.util.Objects;

/**
 */
public class TestEdge implements Edge<TestNode> {

    private int id;
    private TestNode leftNode;
    private TestNode rightNode;
    private boolean directed;

    public TestEdge(int id, TestNode leftNode, TestNode rightNode) {
        this(id, leftNode, rightNode, true);
    }

    public TestEdge(int id, TestNode leftNode, TestNode rightNode, boolean directed) {
        this.id = id;
        this.leftNode = leftNode;
        this.rightNode = rightNode;
        this.directed = directed;
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
    public TestNode getLeftNode() {
        return leftNode;
    }

    @Override
    public TestNode getRightNode() {
        return rightNode;
    }

    @Override
    public boolean isDirected() {
        return directed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestEdge testEdge = (TestEdge) o;
        return id == testEdge.id &&
            Objects.equals(leftNode, testEdge.leftNode) &&
            Objects.equals(rightNode, testEdge.rightNode);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, leftNode, rightNode);
    }

    @Override
    public String toString() {
        return "TestEdge{" +
            "id=" + id +
            ", leftNode=" + leftNode +
            ", rightNode=" + rightNode +
            '}';
    }
}
