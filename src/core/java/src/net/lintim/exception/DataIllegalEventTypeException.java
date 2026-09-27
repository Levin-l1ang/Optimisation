package net.lintim.exception;

/**
 * Exception to throw if the type of an event is undefined.
 */
public class DataIllegalEventTypeException extends LinTimException {
    /**
     * Exception to throw if the type of an event is undefined.
     *
     * @param eventId   event id
     * @param eventType event type
     */
    public DataIllegalEventTypeException(int eventId, String eventType) {
        super("Error D4: " + eventType + " of event " + eventId + " is no legal event type.");
    }
}
