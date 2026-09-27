package net.lintim.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 */
public class StatisticTest {
    @Test
    public void canAddValues() {
        Statistic statistic = new Statistic();
        statistic.put("test", "abc");
        statistic.put("test2", String.valueOf(2));
        statistic.put("test3", String.valueOf(true));
        assertEquals(3, statistic.getData().size());
        statistic.put("test", String.valueOf(5.3));
        assertEquals(3, statistic.getData().size());
    }

    @Test
    public void canReadValues() {
        Statistic statistic = new Statistic();
        statistic.put("test", "abc");
        statistic.put("test2", String.valueOf(2));
        statistic.put("test3", String.valueOf(true));
        statistic.put("test4", String.valueOf(5.3));
        assertEquals("abc", statistic.getStringValue("test"));
        assertEquals(2, (int) statistic.getIntegerValue("test2"));
        assertEquals(true, statistic.getBooleanValue("test3"));
        assertEquals(5.3, (double) statistic.getDoubleValue("test4"), 0.00001);
    }
}
