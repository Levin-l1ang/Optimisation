package net.lintim.algorithm.timetabling.periodic;

import net.lintim.model.ActivityType;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;
import net.lintim.solver.*;
import net.lintim.util.Logger;
import net.lintim.util.timetabling.periodic.Parameters;

import java.util.HashMap;
import java.util.Map;

public class PespIpCore extends PespSolver{

    private static final Logger logger = new Logger(PespSolver.class);

    @Override
    public boolean solveTimetablingPespModel(Graph<PeriodicEvent, PeriodicActivity> ean, Parameters parameters) {
        logger.debug("Setting up solver");
        Solver solver = Solver.createSolver(parameters.getSolverType());
        Model model = solver.createModel();
        parameters.setSolverParameters(model);

        // First, we need to iterate all activities, collecting the event weights. Unfortunately, setObjective is not
        // supported in SCIP, so we need to know the weights before

        Map<Integer, Double> eventWeights = new HashMap<>();
        double changePenalty = 0;
        for (PeriodicActivity activity: ean.getEdges()) {
            eventWeights.put(activity.getLeftNode().getId(),
                eventWeights.computeIfAbsent(activity.getLeftNode().getId(), k -> 0.) - activity.getNumberOfPassengers());
            eventWeights.put(activity.getRightNode().getId(),
                eventWeights.computeIfAbsent(activity.getRightNode().getId(), k -> 0.) + activity.getNumberOfPassengers());
            if (activity.getType() == ActivityType.CHANGE) {
                changePenalty += parameters.getChangePenalty() * activity.getNumberOfPassengers();
            }
        }
        logger.debug("Change penalty: " + changePenalty);

        logger.debug("Add event variables");
        Map<Integer, Variable> eventIdToVarMap = new HashMap<>();
        for (PeriodicEvent event: ean.getNodes()) {
            Variable var = model.addVariable(0, parameters.getPeriodLength() - 1,
                Variable.VariableType.INTEGER, eventWeights.get(event.getId()), "pi_" + event.getId());
            eventIdToVarMap.put(event.getId(), var);
        }
        logger.debug("Add variables and constraints for the activities");
        LinearExpression obj = model.createExpression();
        LinearExpression activityTerm;
        for (PeriodicActivity activity: ean.getEdges()) {
            Variable moduloVariable = model.addVariable(0, Integer.MAX_VALUE, Variable.VariableType.INTEGER,
                activity.getNumberOfPassengers() * parameters.getPeriodLength(), "z_" + activity.getId());
            Variable sourceVar = eventIdToVarMap.get(activity.getLeftNode().getId());
            Variable targetVar = eventIdToVarMap.get(activity.getRightNode().getId());
            activityTerm = model.createExpression();
            activityTerm.multiAdd(parameters.getPeriodLength(), moduloVariable);
            activityTerm.add(targetVar);
            activityTerm.multiAdd(-1, sourceVar);
            model.addConstraint(activity.getLowerBound(), Constraint.ConstraintSense.LESS_EQUAL, activityTerm, "l_" + activity.getId());
            model.addConstraint(activity.getUpperBound(), Constraint.ConstraintSense.GREATER_EQUAL, activityTerm, "u_" + activity.getId());
        }

        if (parameters.shouldUseOldSolution()) {
            logger.debug("Setting start solution");
            for (PeriodicEvent event: ean.getNodes()) {
                model.setStartValue(eventIdToVarMap.get(event.getId()), event.getTime());
            }
        }

        if (parameters.writeLpFile()) {
            logger.debug("Writing lp file");
            model.write("timetabling/PeriodicTimetablingPespCore.lp");
        }

        logger.debug("Start optimization");
        model.solve();
        logger.debug("Finish optimization");

        Model.Status status = model.getStatus();
        int solCount = model.getIntAttribute(Model.IntAttribute.NUM_SOLUTIONS);

        if (solCount > 0) {
            if (status == Model.Status.OPTIMAL) {
                logger.debug("Optimal solution found");
            }
            else {
                logger.debug("Feasible solution found");
            }
            for (PeriodicEvent event: ean.getNodes()) {
                int time = (int) Math.round(model.getValue(eventIdToVarMap.get(event.getId())));
                event.setTime(time);
            }
            return true;
        }
        logger.debug("No feasible solution found");
        if (status == Model.Status.INFEASIBLE) {
            logger.debug("Compute IIS");
            model.computeIIS("timetabling/PeriodicTimetablingPespCore.ilp");
        }
        return false;
    }
}
