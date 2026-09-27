package net.lintim.graph;

/**
 * Thrown if an problem occurs in this package.
 *
 */

@SuppressWarnings("serial")
public class GraphMalformedException extends Exception {

    public GraphMalformedException() {
        super();
    }

    public GraphMalformedException(String message, Throwable cause) {
        super(message, cause);
    }

    public GraphMalformedException(String message) {
        super(message);
    }

    public GraphMalformedException(Throwable cause) {
        super(cause);
    }

}
