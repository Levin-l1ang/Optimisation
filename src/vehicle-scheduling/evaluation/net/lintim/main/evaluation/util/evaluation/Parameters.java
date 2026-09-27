package net.lintim.main.evaluation.util.evaluation;

import net.lintim.util.Config;

public class Parameters {

    private final double costFactorFullDuration;
    private final double costFactorEmptyDuration;
    private final double costFactorFullLength;
    private final double costFactorEmptyLength;
    private final double costPerVehicle;
    private final int timeUnitsPerMinute;
    private final int turnoverTime;
    private final String speedLevel;
    private final String depotFileName;
    private final double conversionFactorCoordinates;
    private boolean useDepot;
    private double speed;


    public Parameters(Config config) {
        //Convert cost factors from cost per hour to cost per second
        costFactorFullDuration = config.getDoubleValue("vs_eval_cost_factor_full_trips_duration")/3600;
        costFactorEmptyDuration = config.getDoubleValue("vs_eval_cost_factor_empty_trips_duration")/3600;
        costFactorFullLength = config.getDoubleValue("vs_eval_cost_factor_full_trips_length");
        costFactorEmptyLength = config.getDoubleValue("vs_eval_cost_factor_empty_trips_length");
        costPerVehicle = config.getDoubleValue("vs_vehicle_costs");
        timeUnitsPerMinute = config.getIntegerValue("time_units_per_minute");
        turnoverTime = config.getIntegerValue("vs_turn_over_time");
        useDepot = false; 
        speedLevel = config.getStringValue("vs_vehicle_speed_level");
        depotFileName = config.getStringValue("filename_depot_file");
        conversionFactorCoordinates = config.getDoubleValue("gen_conversion_coordinates");
        speed = config.getDoubleValue("gen_vehicle_speed");
    }

    public Double getVehicleSpeed () {
        return speed;
    }

    public String getDepotFileName() {
        return depotFileName;
    }

    public Double getConversionCoordinates() {
        return conversionFactorCoordinates;
    }
    
    public String getSpeedLevel() {
        return speedLevel;
    }
    
    public double getCostFactorFullDuration() {
        return costFactorFullDuration;
    }

    public double getCostFactorEmptyDuration() {
        return costFactorEmptyDuration;
    }

    public double getCostFactorFullLength() {
        return costFactorFullLength;
    }

    public double getCostFactorEmptyLength() {
        return costFactorEmptyLength;
    }

    public double getCostPerVehicle() {
        return costPerVehicle;
    }

    public int getTurnoverTime() {
        return turnoverTime;
    }

    public int getTimeUnitsPerMinute() {
        return timeUnitsPerMinute;
    }

    public void setUseDepot(boolean value) {
        this.useDepot = value;
    }

    public boolean useDepot() {
        return useDepot;
    }
}
