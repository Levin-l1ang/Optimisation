package net.lintim.main;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;

/**
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    net.lintim.algorithm.DijkstraTest.class,
    net.lintim.io.IOTest.class,
    net.lintim.model.impl.ArrayListGraphTest.class,
    net.lintim.model.impl.LinkedListPathTest.class,
    net.lintim.model.impl.SimpleMapGraphTest.class,
    net.lintim.model.impl.FullODTest.class,
    net.lintim.model.impl.MapODTest.class,
    net.lintim.model.impl.SparseODTest.class,
    net.lintim.util.ConfigTest.class,
    net.lintim.util.StatisticTest.class,
    net.lintim.util.LinePlanningHelperTest.class
})
public class TestRunner {
    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(TestRunner.class);
        if (result.getFailures().size() > 0) {
            System.out.println("Failures:");
        }
        for (Failure failure : result.getFailures()) {
            System.out.println(failure);
        }
        System.out.println("Successfull: " + result.wasSuccessful() + ", ran " + result.getRunCount() + " tests.");
        if (!result.wasSuccessful()) {
            System.exit(1);
        }
    }
}
