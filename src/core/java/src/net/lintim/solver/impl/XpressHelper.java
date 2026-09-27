package net.lintim.solver.impl;

import com.dashoptimization.XPRBprob;
import com.dashoptimization.XPRS;
import net.lintim.solver.SolverParameters;
import net.lintim.util.Logger;

public class XpressHelper {
    private static final Logger logger = new Logger(XpressHelper.class);

    public static void setXpressSolverParameters(XPRBprob model, SolverParameters parameters) {
        if (parameters.getTimelimit() > 0) {
            logger.debug("Set Xpress timelimit to " + -1 * parameters.getTimelimit());
            model.getXPRSprob().setIntControl(XPRS.MAXTIME, -1 * parameters.getTimelimit());
        }
        if (parameters.getMipGap() > 0) {
            logger.debug("Set Xpress mip gap to " + parameters.getMipGap());
            model.getXPRSprob().setDblControl(XPRS.MIPRELSTOP, parameters.getMipGap());
        }
        if (parameters.getThreadLimit() > 0) {
            logger.debug("Set Xpress thread limit to " + parameters.getThreadLimit());
            model.getXPRSprob().setIntControl(XPRS.THREADS, parameters.getThreadLimit());
        }
        if (parameters.outputSolverMessages()) {
            model.setMsgLevel(4);
        }
        else {
            model.setMsgLevel(0);
        }
    }

}
