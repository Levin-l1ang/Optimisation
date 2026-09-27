package net.lintim.exception;

/**
 * Exception as a wrapper around a IloException from Gurobi
 */
public class SolverCplexException extends LinTimException {

    /**
     * Create a new SolverCplexException from the exception text of the original IloException. Call exception.toString()
     * to get the exception text of the IloException
     * @param exceptionText the message of the exception
     */
    public SolverCplexException(String exceptionText) {
        super("Error S3: Cplex returned the following error: " + exceptionText);
    }
}
