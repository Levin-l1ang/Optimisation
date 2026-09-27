package net.lintim.exception;

/**
 * Exception to throw if the input file has a type inconsistency.
 */
public class InputTypeInconsistencyException extends LinTimException {
    /**
     * Exception to throw if there are type inconsistencies int he input file, i.e., if an entry is not of the
     * expected type.
     *
     * @param fileName     input file name
     * @param columnNumber column in which exception occurs
     * @param lineNumber   number of line in which the exception occurs
     * @param type         expected type
     * @param entry        entry of wrong type
     */
    public InputTypeInconsistencyException(String fileName, int columnNumber, int lineNumber, String type, String
        entry) {
        super("Error I3: Column " + columnNumber + " of file " + fileName + " should be of type " + type + " but " +
            "entry in line " + lineNumber + " is " + entry + ".");
    }
}
