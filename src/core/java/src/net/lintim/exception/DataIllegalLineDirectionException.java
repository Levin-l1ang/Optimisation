package net.lintim.exception;

/**
 * Exception to throw if the direction of an event is undefined.
 */
public class DataIllegalLineDirectionException extends LinTimException {

    /**
     * Exception to throw if the direction of an event is undefined.
     *
     * @param eventId   event id
     * @param value the false direction value
     */
    public DataIllegalLineDirectionException(int eventId, String value){
        super("Error D6: " + value + " of event " + eventId + "is no legal line direction");
    }
}
