package net.lintim.solver;

import net.lintim.exception.LinTimException;
import net.lintim.exception.SolverNotImplementedException;
import net.lintim.util.Logger;
import net.lintim.util.SolverType;

/**
 * Abstract class representing a solver. For actual implementations, see {@link net.lintim.solver.impl}. To get an
 * instance of a solver, use {@link #createSolver(SolverType)}. Afterwards, you can create a model using
 * {@link #createModel()}.
 */
public abstract class Solver {
    private static final Logger logger = new Logger(Solver.class.getCanonicalName());

    /**
     * Create a new solver with the given type. If the solver is not implemented or can not be initialized on the
     * system, this method may throw!
     * @param type the solver type to initialize
     * @return the solver
     */
    public static Solver createSolver(SolverType type){
        String solverClassName = getSolverClassName(type);
        try {
            Class<?> solverClass = Class.forName(solverClassName);
            return (Solver) solverClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.debug("Tried to load class " + solverClassName);
            logger.debug("Error was: " + e);
            throw new LinTimException("Your solver " + type + " could not be loaded. Please make sure that" +
                " the corresponding jar files are in your classpath! For more information on how to find these, see " +
                "the documentation of your solver.");
        }
    }

    private static String getSolverClassName(SolverType type) {
        switch (type) {
            case GUROBI:
                logger.debug("Using Gurobi for optimization");
                return "net.lintim.solver.impl.GurobiSolver";
            case XPRESS:
                logger.debug("Using Xpress for optimization");
                return "net.lintim.solver.impl.XpressSolver";
            case CPLEX:
                logger.debug("Using Cplex for optimization");
                return "net.lintim.solver.impl.CplexSolver";
            case SCIP:
                logger.debug("Using SCIP for optimization");
                return "net.lintim.solver.impl.SCIPSolver";
            case GLPK:
                logger.debug("Using GLPK for optimization");
                return "net.lintim.solver.impl.GLPKSolver";
            default:
                throw new SolverNotImplementedException(type);
        }
    }

    /**
     * Create a model for the current solver. The type of the returned model is dependent on the used solver.
     * @return the newly created model.
     */
    public abstract Model createModel();

    /**
     * Parse all the possible solver types. Note that there may be solver that are not implemented in the java core
     * so initializing a corresponding solver may fail
     * @param solverName the solver name
     * @return the parsed solver type
     */
    public static SolverType parseSolverType(String solverName) {
        switch (solverName.toUpperCase()) {
            case "XPRESS":
                return SolverType.XPRESS;
            case "GUROBI":
                return SolverType.GUROBI;
            case "CPLEX":
                return SolverType.CPLEX;
            case "GLPK":
                return SolverType.GLPK;
            case "SCIP":
                return SolverType.SCIP;
            default:
                throw new LinTimException("Solver " + solverName + " is unknown");
        }
    }

    /**
     * Dispose the solver and all associated resources. This should free any resources used by the solver, e.g., the
     * solver license. Note that using the solver after that may result in errors. You should dispose all models
     * created with this solver first using {@link Model#dispose()}.
     */
    public abstract void dispose();


}

