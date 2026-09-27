package net.lintim.util.vehiclescheduling;

import net.lintim.util.Config;

public class Parameters {

    private final int turnoverTime;
    private final boolean eanContainsFrequencies;
    private final int maximalBufferTime;

    public Parameters(Config config) {
        int timeUnitsPerMinute = config.getIntegerValue("time_units_per_minute");
        turnoverTime = config.getIntegerValue("vs_turn_over_time") * timeUnitsPerMinute;
        String eanConstructionTargetModelFrequency = config.getStringValue
            ("ean_construction_target_model_frequency");
        eanContainsFrequencies = eanConstructionTargetModelFrequency.equals("FREQUENCY_AS_MULTIPLICITY");
        maximalBufferTime = config.getIntegerValue("vs_maximum_buffer_time") * timeUnitsPerMinute;
    }

    public int getTurnoverTime() {
        return turnoverTime;
    }

    public boolean eanContainsFrequencies() {
        return eanContainsFrequencies;
    }

    public int getMaximalBufferTime() {
        return maximalBufferTime;
    }
}
