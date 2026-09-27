package net.lintim.model.impl;

import net.lintim.model.OD;
import net.lintim.model.ODTest;

/**
 */
public class SparseODTest extends ODTest {

    @Override
    protected OD getOd(int size) {
        return new SparseOD(size);
    }
}
