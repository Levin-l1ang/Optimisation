package net.lintim.exception;

/**
 * Exception to throw if the type of the parameter does not match the expected type.
 */
public class MapDataTypeMismatchException extends LinTimException {

    private String key;
    private String type;
    private String parameter;

    /**
     * Exception to throw if the type of the parameter does not match.
     *
     * @param key       key
     * @param type            expected type
     * @param parameter parameter
     */
    public MapDataTypeMismatchException(String key, String type, String parameter) {
        super("Error M3: MapData parameter " + key + " should be of type " + type + " but is " + parameter
            + ".");
        this.key = key;
        this.type = type;
        this.parameter = parameter;
    }

    /**
     * Get the key
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the expected type
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Get the found parameter
     * @return the parameter
     */
    public String getParameter() {
        return parameter;
    }
}
