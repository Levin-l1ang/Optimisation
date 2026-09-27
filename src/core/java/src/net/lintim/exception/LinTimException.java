package net.lintim.exception;

import net.lintim.util.Logger;

/**
 * Add an "master" exception for every other exception to inherit from. This exception can log, if the correct
 * constructor is used.
 */
public class LinTimException extends RuntimeException {

    private static Logger logger = new Logger(LinTimException.class);

    /**
     * Create a new LinTim exception to throw. The exception will be logged as a warning as well.
     * @param exceptionText the exception to throw
     */
    public LinTimException(String exceptionText){
        this(exceptionText, true);
    }

    /**
     * Create a new LinTim exception to throw.
     * @param exceptionText the exception to throw
     * @param logException whether the exception should be logged as a warning
     */
    public LinTimException(String exceptionText, boolean logException){
        super(exceptionText);
        if (logException) {
            logger.error(exceptionText);
        }
    }
}
