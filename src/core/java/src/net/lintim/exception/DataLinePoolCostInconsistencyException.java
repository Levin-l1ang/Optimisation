package net.lintim.exception;

/**
 * Exception to throw when the number of read line costs does not match the number of lines in the corresponding pool.
 */
public class DataLinePoolCostInconsistencyException extends LinTimException {
    /**
     * Exception to throw when the number of read line costs does not match the number of lines in the corresponding pool.
     * @param readLines the number of lines in the line pool
     * @param readLineCosts the number of read line costs
     * @param costFileName the read cost file
     */
    public DataLinePoolCostInconsistencyException(int readLines, int readLineCosts, String costFileName) {
        super("Error D7: Read " + readLineCosts + " entries in the line cost file " + costFileName + ", but " +
            readLines + " lines are in the line pool!");
    }
}
