package net.lintim.model.impl;

import net.lintim.model.GraphTest;

/**
 * Unit tests for the array list graph implementations. Uses the abstract class {@link net.lintim.model.GraphTest}
 * and only implements {@link GraphTest#supplyGraph()}.
 */
public class SimpleMapGraphTest extends GraphTest{

    @Override
    public void supplyGraph() {
        this.graph = new SimpleMapGraph<>();
    }
}
