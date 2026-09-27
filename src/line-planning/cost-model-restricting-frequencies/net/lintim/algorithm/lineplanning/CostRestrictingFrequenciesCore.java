package net.lintim.algorithm.lineplanning;

import net.lintim.model.*;
import net.lintim.solver.*;
import net.lintim.util.Logger;
import net.lintim.util.lineplanning.Parameters;

import java.util.HashMap;

public class CostRestrictingFrequenciesCore extends CostRestrictingFrequenciesSolver {

    private static final Logger logger = new Logger(CostRestrictingFrequenciesCore.class);

    @Override
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, Parameters parameters) {
        Solver solver = Solver.createSolver(parameters.getSolverType());
        Model model = solver.createModel();
        parameters.setSolverParameters(model);

        logger.debug("Add variables");
        HashMap<Integer, HashMap<Integer, Variable>> frequencies = new HashMap<>();
        for (Line line : linePool.getLines()) {
            frequencies.put(line.getId(), new HashMap<>());
            LinearExpression lineFreqConstraint = model.createExpression();
            for (int freq = 0; freq <= parameters.getMaximalFrequency(); freq++) {
                Variable lineUsesFreq = model.addVariable(0, 1, Variable.VariableType.BINARY,
                    freq * line.getCost(), "f_" + line.getId() + "_" + freq);
                frequencies.get(line.getId()).put(freq, lineUsesFreq);
                lineFreqConstraint.add(lineUsesFreq);
            }
            model.addConstraint(lineFreqConstraint, Constraint.ConstraintSense.EQUAL, 1,
                "line_" + line.getId() + "_uses_one_freq");
        }
        if (parameters.getNumberOfPossibleFrequencies() != -1) {
            LinearExpression freqConstraint = model.createExpression();
            for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                Variable freqUsed = model.addVariable(0, 1, Variable.VariableType.BINARY, 0,
                    "freq_" + freq + "_used");
                freqConstraint.add(freqUsed);
                for (Line line: linePool.getLines()) {
                    model.addConstraint(freqUsed, Constraint.ConstraintSense.GREATER_EQUAL, frequencies.get(line.getId()).get(freq),
                        "line_" + line.getId() + "_uses_freq_" + freq);
                }
            }
            model.addConstraint(freqConstraint, Constraint.ConstraintSense.LESS_EQUAL, parameters.getNumberOfPossibleFrequencies(),
                "freq_bound");
        }
        logger.debug("Add frequency constraints");
        LinearExpression sumFreqPerLink;
        for (Link link: ptn.getEdges()) {
            sumFreqPerLink = model.createExpression();
            for (Line line: linePool.getLines()) {
                if (line.getLinePath().contains(link)) {
                    for (int freq = 1; freq <= parameters.getMaximalFrequency(); freq++) {
                        sumFreqPerLink.multiAdd(freq, frequencies.get(line.getId()).get(freq));
                    }
                }
            }
            model.addConstraint(sumFreqPerLink, Constraint.ConstraintSense.GREATER_EQUAL, link.getLowerFrequencyBound(),
                "lb_" + link.getId());
            model.addConstraint(sumFreqPerLink, Constraint.ConstraintSense.LESS_EQUAL, link.getUpperFrequencyBound(),
                "ub_" + link.getId());
        }
        if (parameters.writeLpFile()) {
            logger.debug("Writing model file");
            model.write("line-planning/cost-restrict.lp");
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
            logger.debug("Read back solution");
            for (Line line: linePool.getLines()) {
                for (int freq = 0; freq <= parameters.getMaximalFrequency(); freq++) {
                    if (Math.round(model.getValue(frequencies.get(line.getId()).get(freq))) > 0) {
                        line.setFrequency(freq);
                        break;
                    }
                }
            }
            return true;
        }
        logger.debug("No feasible solution found");
        if (status == Model.Status.INFEASIBLE) {
            logger.debug("Compute IIS");
            model.computeIIS("line-planning/cost-restrict.ilp");
        }
        return false;
    }
}
