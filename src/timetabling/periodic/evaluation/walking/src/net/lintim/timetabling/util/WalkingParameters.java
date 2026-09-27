package net.lintim.timetabling.util;

import net.lintim.util.Config;

public class WalkingParameters {
    private final int periodLength;
    private final int changePenalty;
    private final int minChangeTime;
    private final int maxChangeTime;
    private final double walkingUtility;
    private final double adaptionUtility;
    private final double changeUtility;
    private final boolean outputTravelTimes;
    private final String travelTimeOutputFileName;
    private final String travelTimeOutputHeader;

    public WalkingParameters(Config config) {
        periodLength = config.getIntegerValue("period_length");
        changePenalty = config.getIntegerValue("ean_change_penalty");
        minChangeTime = config.getIntegerValue("ean_default_minimal_change_time");
        maxChangeTime = config.getIntegerValue("ean_default_maximal_change_time");
        walkingUtility = config.getDoubleValue("gen_walking_utility");
        adaptionUtility = config.getDoubleValue("gen_adaption_utility");
        outputTravelTimes = config.getBooleanValue("tim_eval_walking_write_perceived_traveltime_file");
        changeUtility = config.getDoubleValue("gen_change_utility");
        if(outputTravelTimes) {
            travelTimeOutputFileName = config.getStringValue("filename_perceived_travel_time_walking");
            travelTimeOutputHeader = config.getStringValue("travel_time_walking_output_header");
        }
        else {
            travelTimeOutputFileName = "";
            travelTimeOutputHeader = "";
        }
    }

    public int getPeriodLength() {
        return periodLength;
    }

    public int getChangePenalty() {
        return changePenalty;
    }

    public double getWalkingUtility() {
        return walkingUtility;
    }

    public boolean isOutputTravelTimes() {
        return outputTravelTimes;
    }

    public String getTravelTimeOutputFileName() {
        return travelTimeOutputFileName;
    }

    public String getTravelTimeOutputHeader() {
        return travelTimeOutputHeader;
    }

    public double getAdaptionUtility() {
        return adaptionUtility;
    }

    public double getChangeUtility() {
        return changeUtility;
    }

    public int getMinChangeTime() {
        return minChangeTime;
    }

    public int getMaxChangeTime() {
        return maxChangeTime;
    }
}
