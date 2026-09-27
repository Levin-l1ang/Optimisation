package net.lintim.exception;

import net.lintim.util.SolverType;

/**
 * Exception to throw if a attribute is not implemented for a solver.
 */
public class SolverAttributeNotImplementedException extends LinTimException {

    /**
     * Exception to throw if a attribute is not implemented for a solver.
     * @param type the solver type
     * @param attributeName the attribute
     */
    public SolverAttributeNotImplementedException(SolverType type, String attributeName) {
        super("Error S5: Attribute " + attributeName + " is not implemented for " + type.toString() + " yet.");
    }
}
