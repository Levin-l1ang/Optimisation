package net.lintim.model;

import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 */
public abstract class ODTest {

    private final double DELTA = 1e-15;

    protected abstract OD getOd(int size);

    @Test
    public void canComputeNumberOfODPairs() {
        OD od = getOd(5);
        assertEquals(0, od.computeNumberOfPassengers(), DELTA);
        od.setValue(1,1,1);
        assertEquals(1, od.computeNumberOfPassengers(), DELTA);
        od.setValue(5,1, 1);
        assertEquals(2, od.computeNumberOfPassengers(), DELTA);
        od.setValue(1,5, 1);
        assertEquals(3, od.computeNumberOfPassengers(), DELTA);
        od.setValue(1,5, 2);
        assertEquals(4, od.computeNumberOfPassengers(), DELTA);
    }

    @Test
    public void canGetOdPairs() {
        OD od = getOd(3);
        od.setValue(1, 1, 1);
        od.setValue(3, 1, 5);
        od.setValue(2, 1, 0.1);
        Collection<ODPair> odPairs = od.getODPairs();
        assertEquals(3, odPairs.size());
        assertTrue(odPairs.contains(new ODPair(2, 1, 0.1)));
        assertTrue(odPairs.contains(new ODPair(1, 1, 1)));
        assertTrue(odPairs.contains(new ODPair(3, 1, 5)));
        od.setValue(2, 1, 0);
        odPairs = od.getODPairs();
        assertEquals(2, odPairs.size());
        assertTrue(odPairs.contains(new ODPair(1, 1, 1)));
        assertTrue(odPairs.contains(new ODPair(3, 1, 5)));
    }
}
