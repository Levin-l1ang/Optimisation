package net.lintim.util.vehiclescheduling;

import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

import static net.lintim.util.vehiclescheduling.Constants.MINUTES_PER_HOUR;
import static net.lintim.util.vehiclescheduling.Constants.SECONDS_PER_MINUTE;

public class Parameters extends SolverParameters {

    private final double factorLength;
    private final double factorTime;
    private final double vehicleCost;
    private final int timeUnitsPerMinute;
    private final int turnoverTime;
    private final String depotFileName;
    private final double conversionFactorCoordinates;
    private final String speedLevel;
    private final double vehicleSpeed;

    /**
     * Create a new parameter class that reads all necessary info from the config
     *
     * @param config the config to read from
     */
    public Parameters(Config config) {
        super(config, "vs_");
        factorLength = config.getDoubleValue("vs_eval_cost_factor_empty_trips_length");
        // the empty trip duration costs are given per hour, we need them to be per seconds
        factorTime = config.getDoubleValue("vs_eval_cost_factor_empty_trips_duration")/3600;
        vehicleCost = config.getDoubleValue("vs_vehicle_costs");
        timeUnitsPerMinute = config.getIntegerValue("time_units_per_minute");
        depotFileName = config.getStringValue("filename_depot_file");
        conversionFactorCoordinates = config.getDoubleValue("gen_conversion_coordinates");
        turnoverTime = config.getIntegerValue("vs_turn_over_time");
        speedLevel = config.getStringValue("vs_vehicle_speed_level");
        // the vehicle speed is given in length unit/hours we need them to be in length unit/seconds
        vehicleSpeed = config.getDoubleValue("gen_vehicle_speed")/3600;

    }

    public String getDepotFileName() {
        return depotFileName;
    }

    public double getConversionCoordinates() {
        return conversionFactorCoordinates;
    }

    public String getSpeedLevel() {
        return speedLevel;
    }

    public double getSpeed() {
        return vehicleSpeed;
    }

    public double getFactorLength() {
        return factorLength;
    }

    public double getFactorTime() {
        return factorTime;
    }

    public double getVehicleCost() {
        return vehicleCost;
    }

    public int getTurnoverTime() {
        return turnoverTime;
    }

    public int getTimeUnitsPerMinute() {
        return timeUnitsPerMinute;
    }
}
