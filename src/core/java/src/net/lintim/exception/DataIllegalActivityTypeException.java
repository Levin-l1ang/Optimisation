package net.lintim.exception;

/**
 * Exception to throw if the type of an activity is undefined.
 */
public class DataIllegalActivityTypeException extends LinTimException {
    /**
     * Exception to throw if the type of an activity is undefined.
     *
     * @param activityId   activity id
     * @param activityType activity type
     */
    public DataIllegalActivityTypeException(int activityId, String activityType) {
        super("Error D5: " + activityType + " of activity " + activityId + " is no legal activity type.");
    }
}
