package net.lintim.algorithm.lineplanning;

import com.gurobi.gurobi.*;
import net.lintim.exception.SolverGurobiException;
import net.lintim.model.*;
import net.lintim.solver.impl.GurobiHelper;
import net.lintim.util.Logger;
import net.lintim.util.lineplanning.Parameters;

import java.util.HashMap;

/**
 * A class to solve the cost model of line planning using Gurobi.
 */
public class ExtendedCostGurobi extends ExtendedCostSolver {

    @Override
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, Parameters parameters) {
        Logger logger = new Logger(ExtendedCostGurobi.class);

        try {
            GRBEnv env = new GRBEnv();
            GRBModel costModel = new GRBModel(env);
            costModel.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
            GurobiHelper.setGurobiSolverParameters(costModel, parameters);

            //Add variables
            logger.debug("Add variables and system frequency constraint");
            HashMap<Integer, GRBVar> frequencies = new HashMap<>();
            for (Line line : linePool.getLines()) {
                GRBVar frequency = costModel.addVar(0, GRB.INFINITY, line.getCost(), GRB.INTEGER, "f_" + line.getId());
                frequencies.put(line.getId(), frequency);
                GRBVar systemFrequencyDivisor = costModel.addVar(0, GRB.INFINITY, 0, GRB.INTEGER, "g_" +
                    line.getId());
                GRBLinExpr rhs = new GRBLinExpr();
                rhs.addTerm(parameters.getCommonFrequencyDivisor(), systemFrequencyDivisor);
                costModel.addConstr(frequency, GRB.EQUAL, rhs, "systemFrequency_" + line.getId());
            }
            logger.debug("Update model");
            costModel.update();

            //Add constraints
            logger.debug("Add frequency constraints");
            GRBLinExpr sumFreqPerLink;
            for (Link link : ptn.getEdges()) {
                sumFreqPerLink = new GRBLinExpr();
                for (Line line : linePool.getLines()) {
                    if (line.getLinePath().getEdges().contains(link)) {
                        sumFreqPerLink.addTerm(1, frequencies.get(line.getId()));
                    }
                }
                costModel.addConstr(sumFreqPerLink, GRB.GREATER_EQUAL, link.getLowerFrequencyBound(), "lowerBound_" +
                    link.getId());
                costModel.addConstr(sumFreqPerLink, GRB.LESS_EQUAL, link.getUpperFrequencyBound(), "upperBound_" +
                    link.getId());

            }
        if (parameters.writeLpFile()) {
            logger.debug("Writing lp file");  
            costModel.write("line-planning/cost-model-extended-" + parameters.getCommonFrequencyDivisor() + ".lp");
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
                for (Line line : linePool.getLines()) {
                    line.setFrequency((int) Math.round(frequencies.get(line.getId()).get(GRB.DoubleAttr.X)));
                }
                return true;
            }
            logger.debug("No feasible solution found");
            if (status == GRB.INFEASIBLE && parameters.getCommonFrequencyDivisor() == 1) {
                logger.debug("The problem is infeasible!");
                costModel.computeIIS();
                costModel.write("line-planning/extended-cost-model.ilp");
            }
            return false;
        } catch (GRBException e) {
            throw new SolverGurobiException(e.toString());
        }
    }
}
