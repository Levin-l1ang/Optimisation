package net.lintim.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 */
public class LinePlanningHelperTest {

    @Test
    public void testPossibleSystemFrequencies() {
        Set<Integer> result = new HashSet<>();
        result.add(1);
        assertEquals("1 has no system frequencies", result, LinePlanningHelper.determinePossibleSystemFrequencies(1, 1));
        result.clear();
        result.add(2);
        assertEquals("Can give back a specific frequency", result, LinePlanningHelper.determinePossibleSystemFrequencies
            (2, 60));
        result.addAll(Arrays.asList(3, 5, 7, 11, 13));
        assertEquals("Can find all system frequencies", result, LinePlanningHelper
            .determinePossibleSystemFrequencies(-1, 60));
    }
}
