/**
 * Exception is thrown, when a path is not connected in a graph
 */
public class NoPathException extends Exception {

    /**
     * Constructor
     */
    public NoPathException() {
        super();
    }

    /**
     * Constructor
     *
     * @param s error message
     */
    public NoPathException(String s) {
        super(s);
    }
}
