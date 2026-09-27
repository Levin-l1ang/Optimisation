package net.lintim.exception;

public class SolverGlpkException extends LinTimException {

    public SolverGlpkException(String exceptionText) {
        super("Error S9: Glpk returned the following error: " + exceptionText);
    }
}
