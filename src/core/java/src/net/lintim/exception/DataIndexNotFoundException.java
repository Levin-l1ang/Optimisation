package net.lintim.exception;

/**
 * Exception to throw if an element with a specific index is not found.
 */
public class DataIndexNotFoundException extends LinTimException {
    /**
     * Exception to throw if an element with a specific index is not found.
     *
     * @param element type of element which is searched
     * @param index   index of the element
     */
    public DataIndexNotFoundException(String element, int index) {
        super("Error D3: " + element + " with index " + index + " not found.");
    }
}
