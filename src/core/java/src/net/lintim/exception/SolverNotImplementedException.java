package net.lintim.exception;

import net.lintim.util.SolverType;

/**
 * Exception to throw when a user tries to use a solver that is not in the core library.
 */
public class SolverNotImplementedException extends LinTimException{

    /**
     * Create a new exception. This will be thrown, if a solver should be used, that is not implemented.
     * @param type the solver that should be used but is not implemented
     */
    public SolverNotImplementedException(SolverType type) {
        super("Error S4: The solver " + type.toString() + " is not yet implemented in the core solver library.");
    }
}
