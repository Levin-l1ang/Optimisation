package net.lintim.util.timetabling.periodic;

import net.lintim.solver.SolverParameters;
import net.lintim.util.Config;
import net.lintim.util.SolverType;

public class Parameters extends SolverParameters {

    private final boolean useOldSolution;
    private final int periodLength;
    private final int mipFocus;
    private final double changePenalty;
    private final int solutionLimit;
    private final double bestBoundStop;

    public Parameters(Config config) {
        super(config, "tim_");
        useOldSolution = config.getBooleanValue("tim_use_old_solution");
        periodLength = config.getIntegerValue("period_length");
        changePenalty = config.getDoubleValue("ean_change_penalty");
        if (getSolverType() == SolverType.GUROBI) {
            mipFocus = config.getIntegerValue("tim_pesp_ip_mip_focus");
            solutionLimit = config.getIntegerValue("tim_pesp_ip_solution_limit");
            bestBoundStop = config.getDoubleValue("tim_pesp_ip_best_bound_stop");
        } else {
            mipFocus = -1;
            solutionLimit = -1;
            bestBoundStop = -1;
        }
    }

    public boolean shouldUseOldSolution() {
        return useOldSolution;
    }

    public int getPeriodLength() {
        return periodLength;
    }

    public int getMipFocus() {
        return mipFocus;
    }

    public double getChangePenalty() {
        return changePenalty;
    }

    public int getSolutionLimit() {
        return solutionLimit;
    }

    public double getBestBoundStop() {
        return bestBoundStop;
    }
}
