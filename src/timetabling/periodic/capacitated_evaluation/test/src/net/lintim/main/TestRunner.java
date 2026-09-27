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
    net.lintim.algorithm.timetabling.CapacitatedRoutingGurobiTest.class
})
public class TestRunner {
    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(TestRunner.class);
        if (!result.getFailures().isEmpty()) {
            System.out.println("Failures:");
        }
        for (Failure failure : result.getFailures()) {
            System.out.println(failure);
        }
        System.out.println("Successful: " + result.wasSuccessful() + ", ran " + result.getRunCount() + " tests.");
        if (!result.wasSuccessful()) {
            System.exit(1);
        }
    }
}
