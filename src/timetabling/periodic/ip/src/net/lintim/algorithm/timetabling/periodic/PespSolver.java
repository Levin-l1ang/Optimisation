package net.lintim.algorithm.timetabling.periodic;

import net.lintim.exception.LinTimException;
import net.lintim.exception.SolverNotSupportedException;
import net.lintim.model.Graph;
import net.lintim.model.PeriodicActivity;
import net.lintim.model.PeriodicEvent;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;
import net.lintim.util.timetabling.periodic.Parameters;

/**
 * Generic solver for a pesp ip.
 */
public abstract class PespSolver {
    private static final Logger logger = new Logger(PespSolver.class);

    /**
     * Solve the periodic timetabling problem for the given data.
     *
     * @param ean        the ean
     * @param parameters the parameters for the solver
     * @return whether a feasible solution was found
     */
    public abstract boolean solveTimetablingPespModel(Graph<PeriodicEvent, PeriodicActivity> ean, Parameters parameters);

    /**
     * Get a pesp solver of the given type
     *
     * @param solverType the type of the desired solver
     * @return the solver
     */
    public static PespSolver getSolver(SolverType solverType) {
        try {
            String solverClassName;
            switch (solverType) {
                case GUROBI:
                    logger.debug("Will use Gurobi for optimization");
                    solverClassName = "net.lintim.algorithm.timetabling.periodic.PespIpGurobi";
                    break;
                case XPRESS:
                    logger.debug("Will use Xpress for optimization");
                    solverClassName = "net.lintim.algorithm.timetabling.periodic.PespIpXpress";
                    break;
                default:
                    logger.debug("Will use core library for optimization");
                    solverClassName = "net.lintim.algorithm.timetabling.periodic.PespIpCore";
            }
            Class<?> solverClass = Class.forName(solverClassName);
            return (PespSolver) solverClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new LinTimException("Unable to initialize solver: " + e.getMessage());
        }
    }
}
