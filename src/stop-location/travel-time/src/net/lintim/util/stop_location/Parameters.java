package net.lintim.util.stop_location;

import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

public class Parameters extends SolverParameters {

    private final double waitingTime;
    private final double costOfStop;
    private final double conversionCoordinates;


    public Parameters(Config config) {
        super(config, "sl_");
        int minimalWaitingTime = config.getIntegerValue("ean_default_minimal_waiting_time");
        int maximalWaitingTime = config.getIntegerValue("ean_default_maximal_waiting_time");
        int timeUnitsPerMinute = config.getIntegerValue("time_units_per_minute");
        waitingTime = (maximalWaitingTime + minimalWaitingTime) / 2.0 * timeUnitsPerMinute;
        costOfStop = config.getIntegerValue("sl_cost_of_stop");
        conversionCoordinates = config.getDoubleValue("gen_conversion_coordinates");
    }

    public double getWaitingTime() {
        return waitingTime;
    }

    public double getCostOfStop() {
        return costOfStop;
    }

    public double getConversionCoordinates() {
        return conversionCoordinates;
    }
}
