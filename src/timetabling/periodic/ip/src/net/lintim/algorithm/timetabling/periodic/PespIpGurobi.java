package net.lintim.algorithm.timetabling.periodic;

import com.gurobi.gurobi.*;
import net.lintim.exception.SolverGurobiException;
import net.lintim.model.*;
import net.lintim.solver.impl.GurobiHelper;
import net.lintim.util.Logger;
import net.lintim.util.Statistic;
import net.lintim.util.timetabling.periodic.Parameters;

import java.util.HashMap;

import java.util.Locale;

/**
 * Pesp solver implementation using Gurobi.
 */
public class PespIpGurobi extends PespSolver {
    @Override
    public boolean solveTimetablingPespModel(Graph<PeriodicEvent, PeriodicActivity> ean, Parameters parameters) {
        Logger logger = new Logger(PespIpGurobi.class);
        try {
            logger.debug("Setting up the solver");
            GRBEnv env = new GRBEnv();
            GRBModel model = new GRBModel(env);
            model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
            GurobiHelper.setGurobiSolverParameters(model, parameters);

            if (parameters.getSolutionLimit() > 0) {
                model.set(GRB.IntParam.SolutionLimit, parameters.getSolutionLimit());
            }
            if (parameters.getBestBoundStop() > 0) {
                model.set(GRB.DoubleParam.BestBdStop, parameters.getBestBoundStop());
            }
            if (parameters.getMipFocus() > 0) {
                model.set(GRB.IntParam.MIPFocus, parameters.getMipFocus());
            }

            logger.debug("Add event variables");
            HashMap<Integer, GRBVar> eventIdToVarMap = new HashMap<>();
            for (PeriodicEvent event : ean.getNodes()) {
                GRBVar eventVar = model.addVar(0, parameters.getPeriodLength() - 1, 0, GRB.INTEGER, "pi_" + event.getId());
                eventIdToVarMap.put(event.getId(), eventVar);

            }
            logger.debug("Add variables and constraints for the activities");
            GRBLinExpr objective = new GRBLinExpr();
            GRBLinExpr activityTerm;
            for (PeriodicActivity activity : ean.getEdges()) {
                //set objective to 0. The model objective will be overwritten by the build lin expression afterwards
                // anyway
                GRBVar moduloVariable = model.addVar(0, GRB.INFINITY, 0, GRB.INTEGER, "z_" + activity.getId());
                GRBVar sourceEventVariable = eventIdToVarMap.get(activity.getLeftNode().getId());
                GRBVar targetEventVariable = eventIdToVarMap.get(activity.getRightNode().getId());
                activityTerm = new GRBLinExpr();
                activityTerm.addTerm(parameters.getPeriodLength(), moduloVariable);
                activityTerm.addTerm(1, targetEventVariable);
                activityTerm.addTerm(-1, sourceEventVariable);
                //Add the constraints for this activity
                model.addConstr(activity.getLowerBound(), GRB.LESS_EQUAL, activityTerm, "l_" + activity.getId());
                model.addConstr(activity.getUpperBound(), GRB.GREATER_EQUAL, activityTerm, "u_" + activity.getId());
                if (activity.getType() == ActivityType.CHANGE) {
                    activityTerm.addConstant(parameters.getChangePenalty());
                }
                //Add the objective terms
                if (activity.getNumberOfPassengers() != 0) {
                    objective.multAdd(activity.getNumberOfPassengers(), activityTerm);
                }
            }
            model.setObjective(objective);

            // add start solution
            if (parameters.shouldUseOldSolution()) {
                logger.debug("Setting start solution.");
                for (PeriodicEvent event : ean.getNodes()) {
                    int startTime = event.getTime();
                    eventIdToVarMap.get(event.getId()).set(GRB.DoubleAttr.Start, startTime);
                }
            }

            if (parameters.writeLpFile()) {
                logger.debug("Writing lp file");
                model.write("timetabling/PeriodicTimetablingPespIp.lp");
            }
            logger.debug("Start optimization");
            model.optimize();
            logger.debug("End optimization");

            double runtime = model.get(GRB.DoubleAttr.Runtime);
            logger.debug("Pure optimization runtime (in sec): " + String.format(Locale.US, "%.4f", runtime));

            int status = model.get(GRB.IntAttr.Status);
            int solCount = model.get(GRB.IntAttr.SolCount);

            if (solCount > 0) {
                if (status == GRB.OPTIMAL) {
                    logger.debug("Optimal solution found");
                } else {
                    logger.debug("Feasible solution found");
                }
                for (PeriodicEvent event : ean.getNodes()) {
                    int time = (int) Math.round(eventIdToVarMap.get(event.getId()).get(GRB.DoubleAttr.X));
                    event.setTime(time);
                }
                Statistic.putStatic("tim_pesp_gap", model.get(GRB.DoubleAttr.MIPGap));
                return true;
            }
            logger.debug("No feasible solution found");
            if (status == GRB.INFEASIBLE) {
                logger.debug("Compute iis");
                model.computeIIS();
                model.write("timetabling/PeriodicTimetablingPespIp.ilp");
            }
            return false;
        } catch (GRBException e) {
            throw new SolverGurobiException(e.toString());
        }
    }
}
