package net.lintim.util;

public class MLHelper {
    public static boolean timeIsInRoutingInterval(Parameters parameters, int time) {
        return time >= parameters.getRoutingStartTime() && time <= parameters.getRoutingEndTime();
    }
}
