package net.lintim.algorithm.lineplanning;

import com.gurobi.gurobi.*;
import net.lintim.exception.SolverGurobiException;
import net.lintim.model.*;
import net.lintim.solver.impl.GurobiHelper;
import net.lintim.util.Logger;
import net.lintim.util.lineplanning.Parameters;

import java.util.HashMap;

/**
 * A class to solve the cost model with restricted frequencies of line planning using Gurobi.
 */
public class CostRestrictingFrequenciesGurobi extends CostRestrictingFrequenciesSolver {

    @Override
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, Parameters parameters) {
        Logger logger = new Logger(CostRestrictingFrequenciesGurobi.class);
        GRBModel costModel;
        HashMap<Integer, HashMap<Integer, GRBVar>> frequencies = new HashMap<>();
        try {
            GRBEnv env = new GRBEnv();
            costModel = new GRBModel(env);
            costModel.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
            GurobiHelper.setGurobiSolverParameters(costModel, parameters);

            //Add variables
            logger.debug("Add variables");
            for (Line line : linePool.getLines()) {
                frequencies.put(line.getId(), new HashMap<>());
                GRBLinExpr lineFreqConstraint = new GRBLinExpr();
                for (int freq = 0; freq <= parameters.getMaximalFrequency(); freq++) {
                    GRBVar lineUsesFreq = costModel.addVar(0, 1, freq * line.getCost(), GRB.BINARY,
                        "f_" + line.getId() + "_" + freq);
                    frequencies.get(line.getId()).put(freq, lineUsesFreq);
                    lineFreqConstraint.addTerm(1, lineUsesFreq);
                }
                costModel.addConstr(lineFreqConstraint, GRB.EQUAL, 1, "line_" + line.getId() + "_uses_one_freq");
            }
            logger.debug("Update model");
            costModel.update();
            if (parameters.getNumberOfPossibleFrequencies() != -1) {
                GRBLinExpr freqConstraint = new GRBLinExpr();
                for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                    GRBVar freqUsed = costModel.addVar(0, 1, 0, GRB.BINARY, "freq_" + freq + "_used");
                    freqConstraint.addTerm(1, freqUsed);
                    for (Line line : linePool.getLines()) {
                        costModel.addConstr(freqUsed, GRB.GREATER_EQUAL, frequencies.get(line.getId()).get(freq),
                            "line_" + line.getId() + "_uses_freq_" + freq);
                    }
                }
                costModel.addConstr(freqConstraint, GRB.LESS_EQUAL, parameters.getNumberOfPossibleFrequencies(), "freq_bound");
            }
            //Add constraints
            logger.debug("Add frequency constraints");
            GRBLinExpr sumFreqPerLine;
            for (Link link : ptn.getEdges()) {
                sumFreqPerLine = new GRBLinExpr();
                for (Line line : linePool.getLines()) {
                    if (line.getLinePath().getEdges().contains(link)) {
                        for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                            sumFreqPerLine.addTerm(freq, frequencies.get(line.getId()).get(freq));
                        }
                    }
                }
                costModel.addConstr(sumFreqPerLine, GRB.GREATER_EQUAL, link.getLowerFrequencyBound(), "lowerBound_" +
                    link.getId());
                costModel.addConstr(sumFreqPerLine, GRB.LESS_EQUAL, link.getUpperFrequencyBound(), "upperBound_" +
                    link.getId());

            }
            if (parameters.writeLpFile()) {
                logger.debug("Writing model file");
                costModel.write("line-planning/cost-restrict.lp");
            }
            logger.debug("Start optimization");
            costModel.optimize();
            logger.debug("End optimization");

            int status = costModel.get(GRB.IntAttr.Status);
            if (costModel.get(GRB.IntAttr.SolCount) > 0) {
                if (status == GRB.OPTIMAL) {
                    logger.debug("Optimal solution found");
                } else {
                    logger.debug("Feasible solution found");
                }
                logger.debug("Read back solution");
                for (Line line : linePool.getLines()) {
                    for (int freq = 0; freq <= parameters.getMaximalFrequency(); freq++) {
                        if (Math.round(frequencies.get(line.getId()).get(freq).get(GRB.DoubleAttr.X)) > 0) {
                            line.setFrequency(freq);
                            break;
                        }
                    }
                }
                return true;
            }
            logger.debug("No solution found");
            if (status == GRB.INFEASIBLE) {
                logger.debug("The problem is infeasible! Computing IIS");
                costModel.computeIIS();
                costModel.write("line-planning/cost-restrict.ilp");
            }
            return false;


        } catch (GRBException e) {
            throw new SolverGurobiException(e.toString());
        }
    }

}
