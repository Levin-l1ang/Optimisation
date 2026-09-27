package net.lintim.util.vehiclescheduling;

import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;

public class Parameters extends SolverParameters {

    private final String modelType;
    private final double weightFactor;
    private final boolean solveLPRelax;
    private final boolean regardFrequencies;

    public Parameters(Config config) {
        super(config, "vs_");
        modelType = config.getStringValue("vs_line_based_method");
        weightFactor = config.getDoubleValue("vs_line_based_alpha");
        solveLPRelax = config.getBooleanValue("vs_line_based_solve_lp_relax");
        regardFrequencies = config.getBooleanValue("vs_line_based_regard_frequencies");
    }

    public String getModelType() {
        return modelType;
    }

    public double getWeightFactor() {
        return weightFactor;
    }

    public boolean shouldSolveLPRelax() {
        return solveLPRelax;
    }

    public boolean shouldRegardFrequencies() {
        return regardFrequencies;
    }
}
