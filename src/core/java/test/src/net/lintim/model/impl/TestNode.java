package net.lintim.model.impl;

import net.lintim.model.Node;

import java.util.Objects;

/**
 */
public class TestNode implements Node {

    private int id;

    public TestNode(int id) {
        this.id = id;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestNode testNode = (TestNode) o;
        return id == testNode.id;
    }

    @Override
    public int hashCode() {

        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TestNode{" +
            "id=" + id +
            '}';
    }
}
