package net.lintim.exception;

/**
 * Exception to throw if a link cannot be added to a line
 */
public class LineLinkNotAddableException extends LinTimException {
    /**
     * Exception to throw if a link cannot be added to a line.
     *
     * @param linkId link id
     * @param lineId line id
     */
    public LineLinkNotAddableException(int linkId, int lineId) {
        super("Error L1: Link " + linkId + " cannot be added to line " + lineId + ".");
    }
}
