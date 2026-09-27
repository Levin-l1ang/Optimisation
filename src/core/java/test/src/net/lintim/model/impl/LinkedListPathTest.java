package net.lintim.model.impl;

import net.lintim.model.PathTest;

/**
 */
public class LinkedListPathTest extends PathTest {
    @Override
    public void supplyPaths() {
        directedPath = new LinkedListPath<>(true);
        undirectedPath = new LinkedListPath<>(false);
    }
}
