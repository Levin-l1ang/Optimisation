package net.lintim.util.tools;

import net.lintim.util.Config;

public class WalkingPreprocessorParameters {
    private final double maxWalkingTime;
    private final double maxWalkingRatio;
    private final double maxWalkingAmount;
    private final boolean walkingIsDirected;

    public WalkingPreprocessorParameters (Config config) {
        maxWalkingTime = config.getDoubleValue("sl_max_walking_time");
        maxWalkingRatio = config.getDoubleValue("sl_max_walking_ratio");
        maxWalkingAmount = config.getDoubleValue("sl_max_walking_amount");
        walkingIsDirected = config.getBooleanValue("sl_walking_is_directed");
    }

    public double getMaxWalkingTime() {
        return maxWalkingTime;
    }

    public double getMaxWalkingRatio() {
        return maxWalkingRatio;
    }

    public double getMaxWalkingAmount() {
        return maxWalkingAmount;
    }

    public boolean walkingIsDirected() {
        return walkingIsDirected;
    }
}
