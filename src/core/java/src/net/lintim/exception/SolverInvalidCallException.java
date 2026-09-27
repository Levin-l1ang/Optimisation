package net.lintim.exception;

/**
 * Exception to throw if there was an invalid call, i.e., an invalid combination of model status and method call. For
 * more information, see the included error message
 */
public class SolverInvalidCallException extends LinTimException {

    /**
     * Exception to throw if there was an invalid call, i.e., an invalid combination of model status and method call.
     * For more information, see the included error message
     * @param errorMessage the error message.
     */
    public SolverInvalidCallException(String errorMessage) {
        super("Error S?: " + errorMessage);
    }
}
