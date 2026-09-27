package net.lintim.algorithm.lineplanning;

import com.dashoptimization.*;
import net.lintim.exception.LinTimException;
import net.lintim.model.*;
import net.lintim.solver.SolverParameters;
import net.lintim.solver.impl.XpressHelper;
import net.lintim.util.Logger;

import java.io.IOException;
import java.util.HashMap;

/**
 * A class to solve the cost model of line planning using Xpress.
 */
public class CostXpress extends LinePlanningCostSolver {

    private static final Logger logger = new Logger(CostXpress.class);

    @Override
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, SolverParameters parameters, boolean computeIIS) {

        XPRB bcl = new XPRB();
        XPRBprob costModel = bcl.newProb("cost model line planning");
        costModel.setSense(XPRB.MINIM);
        XpressHelper.setXpressSolverParameters(costModel, parameters);

        HashMap<Integer, XPRBvar> frequencies = new HashMap<>();

        //Add variables
        logger.debug("Add variables");
        for (Line line : linePool.getLines()) {
            XPRBvar frequency = costModel.newVar("f_" + line.getId(), XPRB.UI, 0, XPRB.INFINITY);
            frequencies.put(line.getId(), frequency);
        }

        //Add constraints
        logger.debug("Add frequency constraints");
        XPRBexpr sumFreqPerLine;
        for (Link link : ptn.getEdges()) {
            sumFreqPerLine = new XPRBexpr();
            for (Line line : linePool.getLines()) {
                if (line.getLinePath().getEdges().contains(link)) {
                    sumFreqPerLine.addTerm(1, frequencies.get(line.getId()));
                }
            }
            costModel.newCtr("lowerBound_" + link.getId(), sumFreqPerLine.gEql(link.getLowerFrequencyBound()));
            costModel.newCtr("upperBound_" + link.getId(), sumFreqPerLine.lEql(link.getUpperFrequencyBound()));
        }

        logger.debug("Construct objective");
        XPRBexpr objective = new XPRBexpr();
        for (Line line : linePool.getLines()) {
            objective.addTerm(line.getCost(), frequencies.get(line.getId()));
        }

        logger.debug("Set objective");
        costModel.setObj(objective);

        if (parameters.writeLpFile()) {
            try {
                costModel.exportProb("line-planning/cost-model.lp");
            } catch (IOException e) {
                throw new LinTimException(e.getMessage());
            }
        }

        logger.debug("Start optimization");
        costModel.mipOptimise();
        logger.debug("End optimization");

        int status = costModel.getMIPStat();

        if (costModel.getXPRSprob().getIntAttrib(XPRS.MIPSOLS) > 0) {
            if (status == XPRB.MIP_OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }
            for (Line line : linePool.getLines()) {
                line.setFrequency((int) Math.round(frequencies.get(line.getId()).getSol()));
            }
            return true;
        }
        logger.debug("No feasible solution found");
        if (status == XPRB.MIP_INFEAS) {
            logger.debug("The problem is infeasible!");
            if (computeIIS) {
                costModel.getXPRSprob().firstIIS(1);
                costModel.getXPRSprob().writeIIS(0, "line-planning/cost-model.ilp", 0);
            }
        }
        return false;
    }


}
