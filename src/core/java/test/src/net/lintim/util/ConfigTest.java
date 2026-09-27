package net.lintim.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 */
public class ConfigTest {

    @Test
    public void canAddValues() {
        Config config = new Config();
        config.put("test", "abc");
        config.put("test2", String.valueOf(2));
        config.put("test3", String.valueOf(true));
        assertEquals(3, config.getData().size());
        config.put("test", String.valueOf(5.3));
        assertEquals(3, config.getData().size());
    }

    @Test
    public void canReadValues() {
        Config config = new Config();
        config.put("test", "abc");
        config.put("test2", String.valueOf(2));
        config.put("test3", String.valueOf(true));
        config.put("test4", String.valueOf(5.3));
        config.put("test5", "FATAL");
        config.put("test6", "XPRESS");
        assertEquals("abc", config.getStringValue("test"));
        assertEquals(2, (int) config.getIntegerValue("test2"));
        assertEquals(true, config.getBooleanValue("test3"));
        assertEquals(5.3, (double) config.getDoubleValue("test4"), 0.00001);
        assertEquals(LogLevel.FATAL, config.getLogLevel("test5"));
        assertEquals(SolverType.XPRESS, config.getSolverType("test6"));
    }
}
