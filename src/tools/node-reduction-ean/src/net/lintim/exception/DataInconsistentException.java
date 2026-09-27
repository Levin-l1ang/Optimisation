package net.lintim.exception;

/**
 * The standard exception used throughout JavaTools.
 */

@SuppressWarnings("serial")
public class DataInconsistentException extends Exception {

    public DataInconsistentException() {
        super();
    }

    public DataInconsistentException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataInconsistentException(String message) {
        super(message);
    }

    public DataInconsistentException(Throwable cause) {
        super(cause);
    }

}
