package net.lintim.algorithm.lineplanning;

import net.lintim.exception.LinTimException;
import net.lintim.exception.SolverNotSupportedException;
import net.lintim.model.Graph;
import net.lintim.model.LinePool;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.util.LogLevel;
import net.lintim.util.SolverType;
import net.lintim.util.lineplanning.Parameters;

import java.util.logging.Logger;

/**
 * Abstraction class for the different solvers, i.e., {@link CostRestrictingFrequenciesGurobi} and
 * {@link CostRestrictingFrequenciesXpress}.
 */
public abstract class CostRestrictingFrequenciesSolver {

    private static Logger logger = Logger.getLogger(CostRestrictingFrequenciesSolver.class.getCanonicalName());

    /**
     * Solve the line planning problem for the given data. This is just an abstract method, use
     * {@link CostRestrictingFrequenciesGurobi#solveLinePlanningCost(Graph, LinePool, Parameters)} or
     * {@link CostRestrictingFrequenciesXpress#solveLinePlanningCost(Graph, LinePool, Parameters)} for the actual implementation.
     *
     * @param ptn        the underlying ptn to compute the line concept for
     * @param linePool   the linepool to choose the lines from
     * @param parameters the parameters for the model
     * @return whether an optimal/feasible solution could be found.
     */
    public abstract boolean solveLinePlanningCost(Graph<Stop, Link> ptn, LinePool linePool, Parameters parameters);

    public static CostRestrictingFrequenciesSolver getSolver(SolverType solverType) {
        String solverClassName = "";
        switch (solverType) {
            case GUROBI:
                logger.log(LogLevel.DEBUG, "Will use Gurobi for optimization");
                solverClassName = "net.lintim.algorithm.lineplanning.CostRestrictingFrequenciesGurobi";
                break;
            case XPRESS:
                logger.log(LogLevel.DEBUG, "Will use Xpress for optimization");
                solverClassName = "net.lintim.algorithm.lineplanning.CostRestrictingFrequenciesXpress";
                break;
            default:
                logger.log(LogLevel.DEBUG, "Will use " + solverClassName + " for optimization");
                solverClassName = "net.lintim.algorithm.lineplanning.CostRestrictingFrequenciesCore";
        }
        try {
            Class<?> solverClass = Class.forName(solverClassName);
            return (CostRestrictingFrequenciesSolver) solverClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new LinTimException(e.getMessage());
        }
    }
}
