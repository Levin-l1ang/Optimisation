package net.lintim.exception;

/**
 * Exception to throw if the type of the config parameter does not match.
 */
public class ConfigTypeMismatchException extends LinTimException {
    /**
     * Exception to throw if the type of the config parameter does not match.
     *
     * @param configKey       config key
     * @param type            expected type
     * @param configParameter config parameter
     */
    public ConfigTypeMismatchException(String configKey, String type, String configParameter) {
        super("Error C3: Config parameter " + configKey + " should be of type " + type + " but is " + configParameter
            + ".");
    }

    /**
     * Create a new ConfigTypeMisMatchException from a {@link MapDataTypeMismatchException}.
     * @param e the base exception to inherit the values from
     */
    public ConfigTypeMismatchException(MapDataTypeMismatchException e) {
        this(e.getKey(), e.getType(), e.getParameter());
    }
}
