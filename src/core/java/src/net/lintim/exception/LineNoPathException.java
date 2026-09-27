package net.lintim.exception;

/**
 * Exception to throw if a line is no path.
 */
public class LineNoPathException extends LinTimException {
    /**
     * Exception to throw if a line is no path
     *
     * @param lineId line id
     */
    public LineNoPathException(int lineId) {
        super("Error L3: Line " + lineId + " is no path.");
    }
}
