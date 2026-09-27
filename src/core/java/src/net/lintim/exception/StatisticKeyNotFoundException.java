package net.lintim.exception;

/**
 * Exception to throw if a statistic key cannot be found
 */
public class StatisticKeyNotFoundException extends LinTimException {
    /**
     * Exception to throw if a statistic key cannot be found
     * @param key the required key
     */
    public StatisticKeyNotFoundException(String key){
        super("Error ST2: Statistic parameter " + key + " does not exist.");
    }
}
