package net.lintim.exception;

/**
 * Exception as a wrapper around a GRBException from Gurobi
 */
public class SolverGurobiException extends LinTimException {

    /**
     * Create a new SolverGurobiException from the exception text of the original GRBException. Call exception.toString()
     * to get the exception text of the GRBException
     * @param s the message of the exception
     */
    public SolverGurobiException(String s){
        super("Error S2: Gurobi returned the following error: " + s);
    }
}
