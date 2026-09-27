package net.lintim.exception;

/**
 * Exception to be thrown if an input file cannot be found.
 */
public class InputFileException extends LinTimException {
    /**
     * Exception to throw if an input file cannot be found.
     *
     * @param fileName name of the file to open
     */
    public InputFileException(String fileName) {
        super("Error I1: File " + fileName + " cannot be found.");
    }
}
