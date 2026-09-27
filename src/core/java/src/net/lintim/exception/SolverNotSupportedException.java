package net.lintim.exception;

/**
 * Exception to throw if an algorithms is called with a solver which is not supported.
 */
public class SolverNotSupportedException extends LinTimException {
    /**
     * Exception to throw if an algorithms is called with a solver which is not supported.
     *
     * @param solverName    name of the solver
     * @param algorithmName name of the algorithm
     */
    public SolverNotSupportedException(String solverName, String algorithmName) {
        super("Error S1: Solver " + solverName + " not supported for algorithm " + algorithmName + ".");
    }
}
