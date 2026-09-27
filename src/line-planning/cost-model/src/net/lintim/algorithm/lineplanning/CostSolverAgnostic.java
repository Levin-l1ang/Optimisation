package net.lintim.algorithm.lineplanning;

import net.lintim.model.*;
import net.lintim.solver.*;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

import java.util.HashMap;

/**
 * Solver agnostic implementation of the cost model. The solver to use is given in the constructor
 * {@link #CostSolverAgnostic(SolverType)}.
 */
public class CostSolverAgnostic extends LinePlanningCostSolver {

    private static final Logger logger = new Logger(CostSolverAgnostic.class);

    private final SolverType solverType;

    /**
     * Create a new solver class, dependent on the given solver type. An instance of the given solver will be used to
     * solve the models in {@link #solveLinePlanningCost(Graph, LinePool, SolverParameters)} .
     *
     * @param solverType the solver type to use
     */
    public CostSolverAgnostic(SolverType solverType) {
        this.solverType = solverType;
    }

    @Override
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, SolverParameters parameters, boolean computeIIS) {
        Solver solver = Solver.createSolver(solverType);

        Model model = solver.createModel();

        parameters.setSolverParameters(model);

        // Add variables
        logger.debug("Add variables");
        HashMap<Integer, Variable> frequencies = new HashMap<>();
        for (Line line : linePool.getLines()) {
            Variable frequency = model.addVariable(0, Double.POSITIVE_INFINITY, Variable.VariableType.INTEGER, line.getCost(), "f_" + line.getId());
            frequencies.put(line.getId(), frequency);
        }

        // Add constraints
        logger.debug("Add frequency constraints");
        LinearExpression sumFreqPerLine;
        for (Link link : ptn.getEdges()) {
            sumFreqPerLine = model.createExpression();
            for (Line line : linePool.getLines()) {
                if (line.getLinePath().getEdges().contains(link)) {
                    sumFreqPerLine.add(frequencies.get(line.getId()));
                }
            }
            model.addConstraint(sumFreqPerLine, Constraint.ConstraintSense.GREATER_EQUAL,
                link.getLowerFrequencyBound(), "lowerBound_" + link.getId());
            model.addConstraint(sumFreqPerLine, Constraint.ConstraintSense.LESS_EQUAL,
                link.getUpperFrequencyBound(), "upperBound" + link.getId());
        }

        // Transform model
        logger.debug("Transforming model to solver " + solverType.toString());
        model.setSense(Model.OptimizationSense.MINIMIZE);

        if (parameters.writeLpFile()) {
            model.write("line-planning/costModel.lp");
        }

        logger.debug("Start optimization");
        model.solve();
        logger.debug("End optimization");

        // Read back solution
        Model.Status status = model.getStatus();
        if (model.getIntAttribute(Model.IntAttribute.NUM_SOLUTIONS) > 0) {
            if (status == Model.Status.OPTIMAL) {
                logger.debug("Optimal solution found");
            } else {
                logger.debug("Feasible solution found");
            }
            for (Line line : linePool.getLines()) {
                line.setFrequency((int) Math.round(model.getValue(frequencies.get(line.getId()))));
            }
            return true;
        }
        logger.debug("No feasible solution found");
        if (status == Model.Status.INFEASIBLE) {
            logger.debug("The problem is infeasible!");
            if (computeIIS) {
                model.computeIIS("line-planning/cost-model");
            }
        }
        return false;
    }
}
