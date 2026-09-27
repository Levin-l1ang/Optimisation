package net.lintim.util.tools;

import net.lintim.exception.ConfigTypeMismatchException;
import net.lintim.util.Config;

/**
 * Parameter class for load generation in a PTN.
 */
public class LoadGenerationParameters {

    private final boolean useCg;
    private final boolean addAdditionalLoad;
    private final int numberShortestPaths;
    private final LoadGeneratorType loadGeneratorType;
    private final int changePenalty;
    private final int minChangeTime;
    private final int maxChangeTime;
    private final int minWaitTime;
    private final int maxWaitTime;
    private final double minChangeTimeFactor;
    private final String modelWeightDrive;
    private final String modelWeightWait;
    private final double costFactor;
    private final int maxIterations;
    private final double distributionFactor;
    private final int capacity;
    private final double lowerFrequencyFactor;
    private final double upperFrequencyFactor;
    private final boolean useFixUpperFrequency;
    private final int fixUpperFrequency;
    private static final double epsilon = 0.01;

    /**
     * Create a new parameter object from a provided LinTim config.
     * @param config the config
     */
    public LoadGenerationParameters(Config config) {
        useCg = config.getBooleanValue("load_generator_use_cg");
        addAdditionalLoad = config.getBooleanValue("load_generator_add_additional_load");
        numberShortestPaths = config.getIntegerValue("load_generator_number_of_shortest_paths");
        loadGeneratorType = parseLoadGeneratorType(config
            .getStringValue("load_generator_type"));
        changePenalty = config.getIntegerValue("ean_change_penalty");
        minChangeTime = config.getIntegerValue("ean_default_minimal_change_time");
        maxChangeTime = config.getIntegerValue("ean_default_maximal_change_time");
        minWaitTime = config.getIntegerValue("ean_default_minimal_waiting_time");
        maxWaitTime = config.getIntegerValue("ean_default_maximal_waiting_time");
        minChangeTimeFactor = config.getDoubleValue("load_generator_min_change_time_factor");
        modelWeightDrive = config.getStringValue("ean_model_weight_drive");
        modelWeightWait = config.getStringValue("ean_model_weight_wait");
        costFactor = config.getDoubleValue("load_generator_scaling_factor");
        maxIterations = config.getIntegerValue("load_generator_max_iteration");
        distributionFactor = config.getDoubleValue("load_generator_sp_distribution_factor");
        capacity = config.getIntegerValue("gen_passengers_per_vehicle");
        lowerFrequencyFactor = config.getDoubleValue("load_generator_lower_frequency_factor");
        useFixUpperFrequency = config.getBooleanValue("load_generator_fix_upper_frequency");
        if (useFixUpperFrequency) {
            fixUpperFrequency = config.getIntegerValue("load_generator_fixed_upper_frequency");
            upperFrequencyFactor = -1;
        }
        else {
            upperFrequencyFactor = config.getDoubleValue("load_generator_upper_frequency_factor");
            fixUpperFrequency = -1;
        }
    }

    public static LoadGeneratorType parseLoadGeneratorType(String type) {
        switch (type.toLowerCase()) {
            case "sp":
                return LoadGeneratorType.SHORTEST_PATH;
            case "reward":
                return LoadGeneratorType.REWARD;
            case "reduction":
                return LoadGeneratorType.REDUCTION;
            case "iterative":
                return LoadGeneratorType.ITERATIVE;
            default:
                throw new ConfigTypeMismatchException("ptn_load_generator_type", "PTNLoadGeneratorType", type);
        }
    }

    public boolean useCg() {
        return useCg;
    }

    public boolean addAdditionalLoad() {
        return addAdditionalLoad;
    }

    public int getNumberShortestPaths() {
        return numberShortestPaths;
    }

    public LoadGeneratorType getLoadGeneratorType() {
        return loadGeneratorType;
    }

    public int getChangePenalty() {
        return changePenalty;
    }

    public int getMinChangeTime() {
        return minChangeTime;
    }

    public int getMaxChangeTime() {
        return maxChangeTime;
    }

    public double getMinChangeTimeFactor() {
        return minChangeTimeFactor;
    }

    public String getModelWeightDrive() {
        return modelWeightDrive;
    }

    public String getModelWeightWait() {
        return modelWeightWait;
    }

    public double getCostFactor() {
        return costFactor;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public double getDistributionFactor() {
        return distributionFactor;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getLowerFrequencyFactor() {
        return lowerFrequencyFactor;
    }

    public double getUpperFrequencyFactor() {
        return upperFrequencyFactor;
    }

    public boolean useFixUpperFrequency() {
        return useFixUpperFrequency;
    }

    public int getFixUpperFrequency() {
        return fixUpperFrequency;
    }

    public static double getEpsilon() {
        return epsilon;
    }

    public int getMinWaitTime() {
        return minWaitTime;
    }

    public int getMaxWaitTime() {
        return maxWaitTime;
    }

    public enum LoadGeneratorType {
        SHORTEST_PATH, REWARD, REDUCTION, ITERATIVE
    }
}
