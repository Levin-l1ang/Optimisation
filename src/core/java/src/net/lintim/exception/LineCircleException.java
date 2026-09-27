package net.lintim.exception;

/**
 * Exception to throw if a line contains a circle.
 */
public class LineCircleException extends LinTimException {
    /**
     * Exception to throw if a line contains a circle.
     *
     * @param lineId line id
     */
    public LineCircleException(int lineId) {
        super("Error L2: Line " + lineId + " contains a circle.");
    }
}
