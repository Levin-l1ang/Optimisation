package net.lintim.algorithm.lineplanning;

import com.gurobi.gurobi.*;
import net.lintim.exception.SolverGurobiException;
import net.lintim.model.*;
import net.lintim.solver.SolverParameters;
import net.lintim.solver.impl.GurobiHelper;
import net.lintim.util.Logger;

import java.util.HashMap;

/**
 * A class to solve the cost model of line planning using Gurobi.
 */
public class CostGurobi extends LinePlanningCostSolver {
    private static final Logger logger = new Logger(CostGurobi.class);



    @Override
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, SolverParameters parameters, boolean computeIIS) {

        try {
            GRBEnv env = new GRBEnv();
            GRBModel costModel = new GRBModel(env);
            costModel.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
            GurobiHelper.setGurobiSolverParameters(costModel, parameters);

            //Add variables
            logger.debug("Add variables");
            HashMap<Integer, GRBVar> frequencies = new HashMap<>();
            for (Line line : linePool.getLines()) {
                GRBVar frequency = costModel.addVar(0, GRB.INFINITY, line.getCost(), GRB.INTEGER, "f_" + line.getId());
                frequencies.put(line.getId(), frequency);
            }

            //Add constraints
            logger.debug("Add frequency constraints");
            GRBLinExpr sumFreqPerLine;
            for (Link link : ptn.getEdges()) {
                sumFreqPerLine = new GRBLinExpr();
                for (Line line : linePool.getLines()) {
                    if (line.getLinePath().getEdges().contains(link)) {
                        sumFreqPerLine.addTerm(1, frequencies.get(line.getId()));
                    }
                }
                costModel.addConstr(sumFreqPerLine, GRB.GREATER_EQUAL, link.getLowerFrequencyBound(), "lowerBound_" +
                    link.getId());
                costModel.addConstr(sumFreqPerLine, GRB.LESS_EQUAL, link.getUpperFrequencyBound(), "upperBound_" +
                    link.getId());

            }

            if (parameters.writeLpFile()) {
                costModel.write("line-planning/cost-model.lp");
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
                costModel.dispose();
                env.dispose();
                return true;
            }
            logger.debug("No feasible solution found");
            if (status == GRB.INFEASIBLE) {
                logger.debug("The problem is infeasible!");
                if (computeIIS) {
                    costModel.computeIIS();
                    costModel.write("line-planning/cost-model.ilp");
                }
            }
            costModel.dispose();
            env.dispose();
            return false;
        } catch (GRBException e) {
            throw new SolverGurobiException(e.toString());
        }
    }

}
