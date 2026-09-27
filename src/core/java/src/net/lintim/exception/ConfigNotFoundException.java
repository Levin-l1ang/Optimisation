package net.lintim.exception;

/**
 * Exception to throw if no config file was found.
 */
public class ConfigNotFoundException extends LinTimException {
    /**
     * Exception to throw if no config file was found.
     */
    public ConfigNotFoundException(){
        super("Error C1: No config file can be found.");
    }
}
