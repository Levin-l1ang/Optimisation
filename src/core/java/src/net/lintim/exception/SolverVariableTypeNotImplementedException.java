package net.lintim.exception;

import net.lintim.solver.Variable;
import net.lintim.util.SolverType;

/**
 * Exception to throw if a parameter is not implemented for a solver.
 */
public class SolverVariableTypeNotImplementedException extends LinTimException {

    /**
     * Exception to throw if a parameter is not implemented for a solver.
     * @param solverType the solver
     * @param variableType the variable type
     */
    public SolverVariableTypeNotImplementedException(SolverType solverType, Variable.VariableType variableType) {
        super("Error S?: The variable type " + variableType + " is not implemented for " + solverType + " yet.");
    }
}
