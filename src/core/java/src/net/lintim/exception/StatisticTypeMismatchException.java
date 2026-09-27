package net.lintim.exception;

/**
 * Exception to throw if the type of the statistic parameter does not match.
 */
public class StatisticTypeMismatchException extends LinTimException {
    /**
     * Exception to throw if the type of the statistic parameter does not match
     * @param key statistic key
     * @param type expected type
     * @param value statistic value
     */
    public StatisticTypeMismatchException(String key, String type, String value){
        super("Error ST1: Statistic key " + key + " should have type " + type + " but has value " + value + ".");
    }

    /**
     * Create a new StatisticTypeMismatchException from a {@link MapDataTypeMismatchException}.
     * @param e the base exception to inherit the values from
     */
    public StatisticTypeMismatchException(MapDataTypeMismatchException e) {
        this(e.getKey(), e.getType(), e.getParameter());
    }
}
