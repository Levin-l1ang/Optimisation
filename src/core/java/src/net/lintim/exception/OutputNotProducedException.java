package net.lintim.exception;

/**
 * Exception to throw if no output was produced.
 */
public class OutputNotProducedException extends LinTimException {
    /**
     * Exception to throw if no output was produced.
     *
     * @param algo algorithms that did not produce an output
     */
    public OutputNotProducedException(String algo) {
        super("Error O2: Algorithm " + algo + " did not terminate correctly, no output will be produced.");
    }
}
