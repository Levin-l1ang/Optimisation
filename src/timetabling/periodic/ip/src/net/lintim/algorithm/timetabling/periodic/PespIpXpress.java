package net.lintim.algorithm.timetabling.periodic;

import com.dashoptimization.*;
import net.lintim.model.*;
import net.lintim.solver.impl.XpressHelper;
import net.lintim.util.Logger;
import net.lintim.util.Statistic;
import net.lintim.util.timetabling.periodic.Parameters;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;

/**
 * Pesp solver implementation using Xpress.
 */
public class PespIpXpress extends PespSolver {
    @Override
    public boolean solveTimetablingPespModel(Graph<PeriodicEvent, PeriodicActivity> ean, Parameters parameters) {

        Logger logger = new Logger(PespIpXpress.class);

        logger.debug("Setting up the solver");
        XPRB bcl = new XPRB();
        XPRBprob model = bcl.newProb("pesp ip periodic timetabling");
        XpressHelper.setXpressSolverParameters(model, parameters);
        model.setSense(XPRB.MINIM);

        if (parameters.shouldUseOldSolution()) {
            logger.warn("Parameter useOldSolution only implemented for Gurobi.");
        }

        HashMap<Integer, XPRBvar> eventIdToVarMap = new HashMap<>();
        logger.debug("Add event variables");
        for (PeriodicEvent event : ean.getNodes()) {
            XPRBvar eventVar = model.newVar("pi_" + event.getId(), XPRB.UI, 0, parameters.getPeriodLength() - 1);
            eventIdToVarMap.put(event.getId(), eventVar);
        }

        logger.debug("Add variables and constraints for the activities");
        XPRBexpr activityTerm;
        XPRBexpr objective = new XPRBexpr();
        for (PeriodicActivity activity : ean.getEdges()) {
            //set objective to 0. The model objective will be overwritten by the build expression afterwards
            // anyway
            XPRBvar moduloVariable = model.newVar("z_" + activity.getId(), XPRB.UI, 0, XPRB.INFINITY);
            XPRBvar sourceEventVariable = eventIdToVarMap.get(activity.getLeftNode().getId());
            XPRBvar targetEventVariable = eventIdToVarMap.get(activity.getRightNode().getId());
            activityTerm = new XPRBexpr();
            activityTerm.addTerm(parameters.getPeriodLength(), moduloVariable);
            activityTerm.add(targetEventVariable);
            activityTerm.addTerm(-1, sourceEventVariable);
            //Add the constraints for this activity
            model.newCtr("l" + activity.getId(), activityTerm.gEql(activity.getLowerBound()));
            model.newCtr("u" + activity.getId(), activityTerm.lEql(activity.getUpperBound()));
            if (activity.getType() == ActivityType.CHANGE) {
                activityTerm.add(parameters.getChangePenalty());
            }
            //Add the objective terms
            objective.add(activityTerm.mul(activity.getNumberOfPassengers()));
        }

        model.setObj(objective);
        if (parameters.writeLpFile()) {
            logger.debug("Writing lp file");
            try {
                model.exportProb("timetabling/PeriodicTimetablingPespIp.lp");
            } catch (IOException e) {
                logger.debug("Could not write lp file: " + e.getMessage());
            }
        }
        logger.debug("Start optimization");
        model.mipOptimise();
        logger.debug("End optimization");

        if (model.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) > 0) {
            if (model.getMIPStat() == XPRB.MIP_OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }
            for (PeriodicEvent event : ean.getNodes()) {
                int time = (int) Math.round(eventIdToVarMap.get(event.getId()).getSol());
                event.setTime(time);
            }
            double bestObjective = model.getXPRSprob().getDblAttrib(XPRS.MIPBESTOBJVAL);
            double bestBound = model.getXPRSprob().getDblAttrib(XPRS.BESTBOUND);
            double gap = Math.abs(bestObjective - bestBound) / bestObjective;
            Statistic.putStatic("tim_pesp_gap", new BigDecimal(gap).setScale(2, RoundingMode.HALF_UP).doubleValue());
            return true;
        }

        logger.debug("No feasible solution found");
        if (model.getMIPStat() == XPRB.MIP_INFEAS) {
            logger.debug("Model is infeasible");
            model.getXPRSprob().firstIIS(1);
            model.getXPRSprob().writeIIS(0, "timetabling/PeriodicTimetablingPespIp.ilp", 0);
        }
        return false;
    }
}
