package net.lintim.exception;

import net.lintim.util.SolverType;

/**
 * Exception to throw if a parameter is not implemented for a solver.
 */
public class SolverParamNotImplementedException extends LinTimException{

    /**
     * Exception to throw if a parameter is not implemented for a solver.
     * @param type the solver
     * @param param the parameter
     */
    public SolverParamNotImplementedException(SolverType type, String param) {
        super("Error S6: The parameter " + param + " is not implemented for " + type.toString() + " yet.");
    }
}
