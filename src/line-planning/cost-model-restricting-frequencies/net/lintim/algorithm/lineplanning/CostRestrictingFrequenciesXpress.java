package net.lintim.algorithm.lineplanning;

import com.dashoptimization.*;
import net.lintim.model.*;
import net.lintim.solver.impl.XpressHelper;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;
import net.lintim.util.lineplanning.Parameters;

import java.io.IOException;
import java.util.HashMap;

/**
 * A class to solve the cost model with restricted frequencies of line planning using Xpress.
 */
public class CostRestrictingFrequenciesXpress extends CostRestrictingFrequenciesSolver {
    @Override
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, Parameters parameters) {
        Logger logger = new Logger(CostRestrictingFrequenciesXpress.class);

        XPRB bcl = new XPRB();
        XPRBprob costModel = bcl.newProb("cost model line planning restricting frequencies");
        costModel.setSense(XPRB.MINIM);

        XpressHelper.setXpressSolverParameters(costModel, parameters);

        HashMap<Integer, HashMap<Integer, XPRBvar>> frequencies = new HashMap<>();

        //Add variables
        logger.debug("Add variables");
        for (Line line : linePool.getLines()) {
            frequencies.put(line.getId(), new HashMap<>());
            XPRBexpr lineFreqConstraint = new XPRBexpr();
            for (int freq = 0; freq <= parameters.getMaximalFrequency(); freq++) {
                XPRBvar lineUsesFreq = costModel.newVar("f_" + line.getId() + "_" + freq, XPRB.BV, 0, 1);
                frequencies.get(line.getId()).put(freq, lineUsesFreq);
                lineFreqConstraint.add(lineUsesFreq);
            }
            costModel.newCtr("line_" + line.getId() + "_only_uses_one_freq", lineFreqConstraint.eql(1));
        }
        if (parameters.getNumberOfPossibleFrequencies() != -1) {
            XPRBexpr freqConstraint = new XPRBexpr();
            for (int frequency = 1; frequency <= parameters.getMaximalFrequency(); frequency++) {
                XPRBvar freqUsed = costModel.newVar("freq_" + frequency + "_used", XPRB.BV, 0, 1);
                freqConstraint.add(freqUsed);
                for (Line line : linePool.getLines()) {
                    costModel.newCtr("line_" + line.getId() + "_uses_freq_" + frequency, freqUsed.gEql(frequencies.get(line

                        .getId()).get(frequency)));
                }
            }
            costModel.newCtr("freq_bound", freqConstraint.lEql(parameters.getNumberOfPossibleFrequencies()));
        }
        //Add constraints
        logger.debug("Add frequency constraints");
        XPRBexpr sumFreqPerLine;
        for (Link link : ptn.getEdges()) {
            sumFreqPerLine = new XPRBexpr();
            for (Line line : linePool.getLines()) {
                if (line.getLinePath().getEdges().contains(link)) {
                    for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                        sumFreqPerLine.addTerm(freq, frequencies.get(line.getId()).get(freq));
                    }
                }
            }
            costModel.newCtr("lowerBound_" + link.getId(), sumFreqPerLine.gEql(link.getLowerFrequencyBound()));
            costModel.newCtr("upperBound_" + link.getId(), sumFreqPerLine.lEql(link.getUpperFrequencyBound()));
        }

        logger.debug("Construct objective");
        XPRBexpr objective = new XPRBexpr();
        for (Line line : linePool.getLines()) {
            for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                objective.addTerm(line.getCost() * freq, frequencies.get(line.getId()).get(freq));
            }
        }

        logger.debug("Set objective");
        costModel.setObj(objective);

        if (parameters.writeLpFile()) {
            logger.debug("Writing model file");
            try {
                costModel.exportProb("line-planning/xpress_lc.lp");
            } catch (IOException e) {
                logger.warn("Could not write lp file, exception " + e);
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
                for (int freq = 0; freq <= parameters.getMaximalFrequency(); freq++) {
                    if (Math.round(frequencies.get(line.getId()).get(freq).getSol()) > 0) {
                        line.setFrequency(freq);
                    }
                }
            }
            return true;
        }
        if (status == XPRB.MIP_OPTIMAL) {
            logger.debug("Optimal solution found");
        } else if (status == XPRB.MIP_SOLUTION) {
            logger.debug("Feasible solution found");
        }

        logger.debug("No feasible solution found");
        if (status == XPRB.MIP_INFEAS) {
            logger.debug("The problem is infeasible!");
            costModel.getXPRSprob().firstIIS(1);
            costModel.getXPRSprob().writeIIS(0, "line-planning/cost-restrict.ilp", 0);
        }
        return false;
    }

}
