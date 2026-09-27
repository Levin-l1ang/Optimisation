package net.lintim.util.lineplanning;

import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;
import net.lintim.util.Logger;

/**
 * Parameter class for the direct line planning model
 */
public class DirectParameters extends SolverParameters {
    private static final Logger logger = new Logger(DirectParameters.class.getCanonicalName());

    private int commonFrequencyDivisor;
    private final int periodLength;
    private final int capacity;
    private final double budget;
    private final double directFactor;
    private final double costFactor;
    private final String weightDrive;
    private final boolean respectFixedLines;
    private final String fixedLinesFileName;


    /**
     * Create a new parameter object for the direct line planning problem. Will read all necessary data from the
     * provided config. For information on the different config parameters, check the LinTim documentation.
     *
     * @param config the config to read
     */
    public DirectParameters(Config config) {
        super(config, "lc_");
        commonFrequencyDivisor = config.getIntegerValue("lc_common_frequency_divisor");
        periodLength = config.getIntegerValue("period_length");
        capacity = config.getIntegerValue("gen_passengers_per_vehicle");
        budget = config.getDoubleValue("lc_budget");
        boolean respectCosts = config.getBooleanValue("lc_direct_optimize_costs");
        if (respectCosts) {
            double factor = config.getDoubleValue("lc_mult_relation");
            if (factor < 0 || factor > 1) {
                logger.warn("Config key lc_mult_relation should in [0,1] but this is not the case!");
            }
            directFactor = 1 - factor;
        } else {
            directFactor = 1;
        }
        respectFixedLines = config.getBooleanValue("lc_respect_fixed_lines");
        if (respectFixedLines) {
            fixedLinesFileName = config.getStringValue("filename_lc_fixed_lines");
        }
        else {
            fixedLinesFileName = "";
        }
        costFactor = 1 - directFactor;
        weightDrive = config.getStringValue("ean_model_weight_drive").toUpperCase();
    }

    public boolean respectFixedLines() {
        return respectFixedLines;
    }

    public String getFixedLinesFileName() {
        return fixedLinesFileName;
    }

    public int getCommonFrequencyDivisor() {
        return commonFrequencyDivisor;
    }

    public int getPeriodLength() {
        return periodLength;
    }

    public double getDirectFactor() {
        return directFactor;
    }

    public double getCostFactor() {
        return costFactor;
    }

    public String getWeightDrive() {
        return weightDrive;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getBudget() {
        return budget;
    }

    public void setCommonFrequencyDivisor(int commonFrequencyDivisor) {
        this.commonFrequencyDivisor = commonFrequencyDivisor;
    }
}
