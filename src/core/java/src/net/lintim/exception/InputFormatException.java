package net.lintim.exception;

/**
 * Exception to throw if the input file is not formatted correctly.
 */
public class InputFormatException extends LinTimException {
    /**
     * Exception to throw if the input file is not formatted correctly, i.e., if the wrong number of columns is given.
     *
     * @param fileName        name of the input file
     * @param columnsGiven    number of columns in input file
     * @param columnsRequired expected number of columns
     */
    public InputFormatException(String fileName, int columnsGiven, int columnsRequired) {
        super("Error I2: File " + fileName + " is not formatted correctly: " + columnsGiven + " columns given, " +
            columnsRequired + " needed.");
    }
}
