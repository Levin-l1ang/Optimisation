package net.lintim.exception;

/**
 * Exception to throw if a config key cannot be found.
 */
public class ConfigKeyNotFoundException extends LinTimException {
    /**
     * Exception to throw if a config key cannot be found.
     *
     * @param configKey the required key
     */
    public ConfigKeyNotFoundException(String configKey) {
        super("Error C2: Config parameter " + configKey + " does not exist.");
    }
}
