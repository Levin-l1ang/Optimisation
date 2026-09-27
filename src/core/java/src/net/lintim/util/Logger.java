package net.lintim.util;

/**
 * Encapsulation class for a logger implementation in LinTim. Currently uses a {@link java.util.logging.Logger}, but
 * this may change in the future.
 */
public class Logger {
    private final java.util.logging.Logger logger;

    /**
     * Create a new logger with the given name
     * @param name the name of the logger
     */
    public Logger(String name) {
        this.logger = java.util.logging.Logger.getLogger(name);
    }

    /**
     * Create a new logger for the given class
     * @param name the name of the logging class
     */
    public Logger(Class name) {
        this(name.getCanonicalName());
    }

    /**
     * Log the given message for debug purposes
     * @param message the message to log
     */
    public void debug(String message) {
        logger.log(LogLevel.DEBUG, message);
    }

    /**
     * Log the given message for info purposes
     * @param message the message to log
     */
    public void info(String message) {
        logger.log(LogLevel.INFO, message);
    }

    /**
     * Log the given message for warning purposes
     * @param message the message to log
     */
    public void warn(String message) {
        logger.log(LogLevel.WARN, message);
    }

    /**
     * Log the given message for error purposes
     * @param message the message to log
     */
    public void error(String message) {
        logger.log(LogLevel.ERROR, message);
    }

    /**
     * Log the given message for fatal purposes
     * @param message the message to log
     */
    public void fatal(String message) {
        logger.log(LogLevel.FATAL, message);
    }
}
