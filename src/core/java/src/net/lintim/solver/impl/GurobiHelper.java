package net.lintim.solver.impl;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBModel;
import net.lintim.solver.SolverParameters;
import net.lintim.util.Logger;

public class GurobiHelper {

    private static final Logger logger = new Logger(GurobiHelper.class);

    public static void setGurobiSolverParameters(GRBModel model, SolverParameters parameters) throws GRBException {

        if (parameters.getTimelimit() > 0) {
            logger.debug("Set Gurobi timelimit to " + parameters.getTimelimit());
            model.set(GRB.DoubleParam.TimeLimit, parameters.getTimelimit());
        }

        if (parameters.getMipGap() > 0) {
            logger.debug("Set Gurobi mip gap to " + parameters.getMipGap());
            model.set(GRB.DoubleParam.MIPGap, parameters.getMipGap());
        }

        if (parameters.getThreadLimit() > 0) {
            logger.debug("Set Gurobi thread limit to " + parameters.getThreadLimit());
            model.set(GRB.IntParam.Threads, parameters.getThreadLimit());
        }

        if (parameters.outputSolverMessages()) {
            model.set(GRB.IntParam.LogToConsole, 1);
            model.set(GRB.StringParam.LogFile, "gurobi.log");
        } else {
            model.set(GRB.IntParam.OutputFlag, 0);
        }
    }
}
