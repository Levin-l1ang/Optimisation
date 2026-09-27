package net.lintim.util.lineplanning;

import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

public class Parameters extends SolverParameters {

    private final int numberOfPossibleFrequencies;
    private final int maximalFrequency;

    public Parameters(Config config) {
        super(config, "lc_");
        numberOfPossibleFrequencies = config.getIntegerValue("lc_number_of_possible_frequencies");
        maximalFrequency = config.getIntegerValue("lc_maximal_frequency");
    }

    public int getNumberOfPossibleFrequencies() {
        return numberOfPossibleFrequencies;
    }

    public int getMaximalFrequency() {
        return maximalFrequency;
    }
}
