package net.lintim.util;

import net.lintim.exception.ConfigTypeMismatchException;

public class GeneticAlgorithmParameters extends Parameters{

    private final int solutionPoolSize;
    private final int numberOfRandomMutationsAtStart;
    private final int numberOfRandomMutationsBreeding;
    private final int maxMutationAmount;
    private final int breedingsPerIteration;
    private final double mipGap;
    private final int timeLimit;
    private final int threadLimit;
    private final boolean outputSolverMessages;
    private final boolean writeLpFile;
    private final boolean useDepot;
    private final int depotIndex;
    private final double costFactorLength;
    private final double costFactorTime;
    private final double vehicleCost;
    private final int numberOfTimetablesToRead;
    private final int seed;
    private final boolean onlyBestBreeding;
    private final SELECTION_TYPE selectionType;

    public GeneticAlgorithmParameters(Config config) {
        super(config);
        costFactorLength = config.getDoubleValue("vs_eval_cost_factor_empty_trips_length");
        costFactorTime = config.getDoubleValue("vs_eval_cost_factor_empty_trips_duration") / SECONDS_PER_HOUR;
        vehicleCost = config.getDoubleValue("vs_vehicle_costs");
        depotIndex = config.getIntegerValue("vs_depot_index");
        useDepot = depotIndex != -1;
        this.mipGap = config.getDoubleValue("rob_mip_gap");
        this.timeLimit = config.getIntegerValue("rob_timelimit");
        this.threadLimit = config.getIntegerValue("rob_threads");
        this.outputSolverMessages = config.getLogLevel("console_log_level") == LogLevel.DEBUG;
        this.writeLpFile = false;
        solutionPoolSize = config.getIntegerValue("rob_ga_solution_pool_size");
        breedingsPerIteration = config.getIntegerValue("rob_ga_breedings_per_iteration");
        numberOfRandomMutationsAtStart = config.getIntegerValue("rob_ga_number_mutations_at_start");
        numberOfRandomMutationsBreeding = config.getIntegerValue("rob_ga_number_mutations_at_breeding");
        maxMutationAmount = config.getIntegerValue("rob_ga_mutation_amount");
        numberOfTimetablesToRead = config.getIntegerValue("rob_ga_number_start_solutions");
        seed = config.getIntegerValue("rob_ga_seed");
        onlyBestBreeding = config.getBooleanValue("rob_ga_only_best_breeding");
        switch (config.getStringValue("rob_ga_selection").toUpperCase()) {
            case "QUALITY":
                selectionType = SELECTION_TYPE.QUALITY;
                break;
            case "PARETO":
                selectionType = SELECTION_TYPE.PARETO;
                break;
            default:
                throw new ConfigTypeMismatchException("rob_ga_selection", "QUALITY or PARETO", config.getStringValue("rob_genetic_selection"));
        }
    }

    public SELECTION_TYPE getSelectionType() {
        return selectionType;
    }

    public boolean onlyBestBreeding() {
        return onlyBestBreeding;
    }

    public int getSeed() {
        return seed;
    }

    public double getMipGap() {
        return mipGap;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public int getThreadLimit() {
        return threadLimit;
    }

    public boolean outputSolverMessages() {
        return outputSolverMessages;
    }

    public boolean writeLpFile() {
        return writeLpFile;
    }

    public boolean useDepot() {
        return useDepot;
    }

    public int getDepotIndex() {
        return depotIndex;
    }

    public double getCostFactorLength() {
        return costFactorLength;
    }

    public double getCostFactorTime() {
        return costFactorTime;
    }

    public double getVehicleCost() {
        return vehicleCost;
    }

    public int getSolutionPoolSize() {
        return solutionPoolSize;
    }

    public int getNumberOfRandomMutationsAtStart() {
        return numberOfRandomMutationsAtStart;
    }

    public int getNumberOfRandomMutationsBreeding() {
        return numberOfRandomMutationsBreeding;
    }

    public int getMaxMutationAmount() {
        return maxMutationAmount;
    }

    public int getBreedingsPerIteration() {
        return breedingsPerIteration;
    }

    public int getNumberOfTimetablesToRead() {
        return numberOfTimetablesToRead;
    }

    public static enum SELECTION_TYPE {
        QUALITY, PARETO
    }
}
