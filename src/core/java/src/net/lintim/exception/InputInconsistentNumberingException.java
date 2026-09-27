package net.lintim.exception;

/**
 * Exception to throw, if the input is inconsistently numbered
 */
public class InputInconsistentNumberingException extends LinTimException{

    /**
     * Create a new inconsistent numbering exception for a datatype. There are algorithms, that require certain data
     * types to be numbered consistently from 1..n. This should be avoided as far as possible, but if it is not
     * possible (yet), throw this exception.
     * @param dataType the data type for which consistent numbering is required
     * @param algorithmName the algorithm that needs this numbering
     */
    public InputInconsistentNumberingException(String dataType, String algorithmName){
        super("Error I4: Datatype " + dataType + "is not numbered consistently starting from 1, but " + algorithmName
            + "needs that.");
    }
}
