package net.lintim.algorithm.lineplanning;

import net.lintim.exception.LinTimException;
import net.lintim.exception.SolverNotSupportedException;
import net.lintim.model.Graph;
import net.lintim.model.LinePool;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;
import net.lintim.util.lineplanning.Parameters;

/**
 * Abstraction class for the different solver classes, i.e., {@link ExtendedCostGurobi} and {@link ExtendedCostXpress}.
 */
public abstract class ExtendedCostSolver {

    private static final Logger logger = new Logger(ExtendedCostSolver.class);

    public static ExtendedCostSolver getExtendedCostSolver(SolverType solverType) {
        String solverClassName;
        switch (solverType) {
            case GUROBI:
                logger.debug("Will use Gurobi for optimization");
                solverClassName = "net.lintim.algorithm.lineplanning.ExtendedCostGurobi";
                break;
            case XPRESS:
                logger.debug("Will use Xpress for optimization");
                solverClassName = "net.lintim.algorithm.lineplanning.ExtendedCostXpress";
                break;
            default:
                logger.debug("Will use Core for optimization");
                solverClassName = "net.lintim.algorithm.lineplanning.ExtendedCostCore";
        }
        try {
            Class<?> solverClass = Class.forName(solverClassName);
            return (ExtendedCostSolver) solverClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new LinTimException(e.getMessage());
        }
    }

    /**
     * Solve the line planning problem for the given data. This is just an abstract method, see e.g.
     * {@link ExtendedCostGurobi#solveLinePlanningCost(Graph, LinePool, Parameters)} for the actual implementation.
     *
     * @param ptn        the underlying ptn to compute the line concept for
     * @param linePool   the linepool to choose the lines from
     * @param parameters the parameters for the model
     * @return whether an optimal/feasible solution could be found.
     */
    public abstract boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, Parameters parameters);
}
