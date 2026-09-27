package net.lintim.exception;

/**
 * Exception to throw if no config file name is given.
 */
public class ConfigNoFileNameGivenException extends LinTimException {
    /**
     * Exception to throw if no config file name is given.
     */
    public ConfigNoFileNameGivenException() {
        super("Error C4: No config file name given.");
    }
}
