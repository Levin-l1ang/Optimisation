package net.lintim.exception;


/**
 * Exception to throw if an output file cannot by written.
 */
public class OutputFileException extends LinTimException {
    /**
     * Exception to throw if an output file cannot be written.
     *
     * @param fileName name of the output file
     */
    public OutputFileException(String fileName) {
        super("Error O1: File " + fileName + " cannot be written.");
    }
}
