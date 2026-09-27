package net.lintim.algorithm.lineplanning;

import net.lintim.exception.LinTimException;
import net.lintim.model.Graph;
import net.lintim.model.LinePool;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.solver.SolverParameters;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

/**
 * Generic class to solve the line planning cost model in its basic form.
 */
public abstract class LinePlanningCostSolver {
    private static final Logger logger = new Logger(LinePlanningCostSolver.class);

    /**
     * Solve the line planning problem for the given data. Will model the cost optimization problem for the given data
     * and solve it using the provided solver type.
     *
     * @param ptn        the underlying ptn to compute the line concept for
     * @param linePool   the linepool to choose the lines from
     * @param parameters the parameters for the solver
     * @return whether an optimal/feasible solution could be found.
     */
    public boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, SolverParameters parameters) {
        return solveLinePlanningCost(ptn, linePool, parameters, true);
    }

    /**
     * Solve the line planning problem for the given data. Will model the cost optimization problem for the given data
     * and solve it using the provided solver type.
     *
     * @param ptn        the underlying ptn to compute the line concept for
     * @param linePool   the linepool to choose the lines from
     * @param parameters the parameters for the solver
     * @param computeIIS whether compute an IIS when an infeasible solution is found
     * @return whether an optimal/feasible solution could be found.
     */
    public abstract boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, SolverParameters parameters, boolean computeIIS);

    public static LinePlanningCostSolver getLinePlanningCostSolver(SolverType solverType) {
        String solverClassName;
        Class<?> solverClass;
        try {
            switch (solverType) {
                case GUROBI:
                    logger.debug("Will use Gurobi for optimization");
                    solverClassName = "net.lintim.algorithm.lineplanning.CostGurobi";
                    solverClass = Class.forName(solverClassName);
                    return (LinePlanningCostSolver) solverClass.getDeclaredConstructor().newInstance();
                case XPRESS:
                    logger.debug("Will use Xpress for optimization");
                    solverClassName = "net.lintim.algorithm.lineplanning.CostXpress";
                    solverClass = Class.forName(solverClassName);
                    return (LinePlanningCostSolver) solverClass.getDeclaredConstructor().newInstance();
                default:
                    logger.debug("Will use solver agnostic version for optimization");
                    solverClassName = "net.lintim.algorithm.lineplanning.CostSolverAgnostic";
                    solverClass = Class.forName(solverClassName);
                    return (LinePlanningCostSolver) solverClass.getDeclaredConstructor(SolverType.class).newInstance
                        (solverType);
            }
        } catch (Exception e) {
            logger.info("Could not load solver " + solverType + ", can you use it on your system?");
            throw new LinTimException(e.getMessage());
        }
    }
}
