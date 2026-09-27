package net.lintim.util.lineplanning;

import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

public class Parameters extends SolverParameters {

    private final int numberOfPossibleFrequencies;
    private final double maximalFrequency;
    private final String weightDrive;
    private final int capacity;
    private final double budget;

    public Parameters(Config config) {
        super(config, "lc_");
        numberOfPossibleFrequencies = config.getIntegerValue("lc_number_of_possible_frequencies");
        maximalFrequency = config.getIntegerValue("lc_maximal_frequency");
        weightDrive = config.getStringValue("ean_model_weight_drive").toUpperCase();
        capacity = config.getIntegerValue("gen_passengers_per_vehicle");
        budget = config.getIntegerValue("lc_budget");
    }

    public int getNumberOfPossibleFrequencies() {
        return numberOfPossibleFrequencies;
    }

    public double getMaximalFrequency() {
        return maximalFrequency;
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
}
