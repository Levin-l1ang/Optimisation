package net.lintim.solver;


import net.lintim.exception.SolverParamNotImplementedException;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

public class SolverParameters {

    private static final Logger logger = new Logger(SolverParameters.class);

    private final int threadLimit;
    private final int timelimit;
    private final double mipGap;
    private final boolean writeLpFile;
    private final boolean outputSolverMessages;
    private final SolverType solverType;

    public SolverParameters(Config config, String prefix) {
        this.threadLimit = config.getIntegerValue(prefix + "threads");
        this.timelimit = config.getIntegerValue(prefix + "timelimit");
        this.mipGap = config.getDoubleValue(prefix + "mip_gap");
        this.writeLpFile = config.getBooleanValue(prefix + "write_lp_file");
        this.outputSolverMessages = config.getLogLevel("console_log_level") == LogLevel.DEBUG;
        this.solverType = config.getSolverType(prefix + "solver");
    }

    public int getTimelimit() {
        return timelimit;
    }

    public double getMipGap() {
        return mipGap;
    }

    public boolean writeLpFile() {
        return writeLpFile;
    }

    public boolean outputSolverMessages() {
        return outputSolverMessages;
    }

    public SolverType getSolverType() {
        return solverType;
    }

    public int getThreadLimit() {
        return threadLimit;
    }

    public void setSolverParameters(Model model) {
        if (timelimit > 0) {
            logger.debug("Set solver timelimit to " + timelimit);
            try {
                model.setIntParam(Model.IntParam.TIMELIMIT, timelimit);
            } catch (SolverParamNotImplementedException e) {
                logger.warn("Parameter timelimit not implemented for solver " + solverType + ", skip.");
            }
        }
        if (mipGap > 0) {
            logger.debug("Set solver mip gap to " + mipGap);
            try {
                model.setDoubleParam(Model.DoubleParam.MIP_GAP, mipGap);
            } catch (SolverParamNotImplementedException e) {
                logger.warn("Parameter mip gap not implemented for solver " + solverType + ", skip.");
            }
        }
        if (threadLimit > 0) {
            logger.debug("Set solver thread limit to " + threadLimit);
            try {
                model.setIntParam(Model.IntParam.THREAD_LIMIT, threadLimit);
            } catch (SolverParamNotImplementedException e) {
                logger.warn("Parameter thread limit not implemented for solver " + solverType + ", skip.");
            }
        }
        try {
            if (outputSolverMessages) {
                model.setIntParam(Model.IntParam.OUTPUT_LEVEL, LogLevel.DEBUG.intValue());
            }
            else {
                model.setIntParam(Model.IntParam.OUTPUT_LEVEL, 0);
            }
        } catch (SolverParamNotImplementedException e) {
            logger.warn("Parameter output level not implemented for solver " + solverType + ", skip.");
        }
    }
}
