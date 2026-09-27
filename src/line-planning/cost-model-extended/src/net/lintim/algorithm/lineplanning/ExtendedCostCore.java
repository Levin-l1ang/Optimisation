package net.lintim.algorithm.lineplanning;

import net.lintim.model.*;
import net.lintim.solver.*;
import net.lintim.util.Logger;
import net.lintim.util.lineplanning.Parameters;

import java.util.HashMap;

public class ExtendedCostCore extends ExtendedCostSolver{

    private static Logger logger = new Logger(ExtendedCostCore.class);

    @Override
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, Parameters parameters) {
        Solver solver = Solver.createSolver(parameters.getSolverType());
        Model model = solver.createModel();
        parameters.setSolverParameters(model);

        logger.debug("Add variables and system frequency constraints");
        HashMap<Integer, Variable> frequencies = new HashMap<>();
        for (Line line: linePool.getLines()) {
            Variable frequency = model.addVariable(0, Integer.MAX_VALUE, Variable.VariableType.INTEGER,
                line.getCost(), "f_" + line.getId());
            frequencies.put(line.getId(), frequency);
            Variable systemFrequencyDivisor = model.addVariable(0, Integer.MAX_VALUE, Variable.VariableType.INTEGER,
                0, "g_" + line.getId());
            LinearExpression rhs = model.createExpression();
            rhs.multiAdd(parameters.getCommonFrequencyDivisor(), systemFrequencyDivisor);
            model.addConstraint(frequency, Constraint.ConstraintSense.EQUAL, rhs, "system_frequency_" + line.getId());
        }

        logger.debug("Add frequency constraints");
        LinearExpression sumFreqPerLink;
        for (Link link: ptn.getEdges()) {
            sumFreqPerLink = model.createExpression();
            for (Line line: linePool.getLines()) {
                if (line.getLinePath().contains(link)) {
                    sumFreqPerLink.add(frequencies.get(line.getId()));
                }
            }
            model.addConstraint(sumFreqPerLink, Constraint.ConstraintSense.GREATER_EQUAL, link.getLowerFrequencyBound(),
                "lb_" + link.getId());
            model.addConstraint(sumFreqPerLink, Constraint.ConstraintSense.LESS_EQUAL, link.getUpperFrequencyBound(),
                "ub_" + link.getId());
        }

        if (parameters.writeLpFile()) {
            logger.debug("Writing lp file");
            model.write("line-planning/cost-model-extended-" + parameters.getCommonFrequencyDivisor() + ".lp");
        }

        logger.debug("Start optimization");
        model.solve();
        logger.debug("End optimization");
        Model.Status status = model.getStatus();
        if (model.getIntAttribute(Model.IntAttribute.NUM_SOLUTIONS) > 0) {
            if (status == Model.Status.OPTIMAL) {
                logger.debug("Optimal solution found");
            }
            else {
                logger.debug("Feasible solution found");
            }
            for (Line line: linePool.getLines()) {
                line.setFrequency((int) Math.round(model.getValue(frequencies.get(line.getId()))));
            }
            return true;
        }
        logger.debug("No feasible solution found");
        if (status == Model.Status.INFEASIBLE && parameters.getCommonFrequencyDivisor() == 1) {
            logger.debug("The problem is infeasible");
            model.computeIIS("line-planning/cost-model-extended.ilp");
        }
        return false;
    }
}
