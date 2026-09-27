package net.lintim.exception;

/**
 * Exception to throw if periodic event to aperiodic event does not exist.
 */
public class DataNoPeriodicEventToAperiodicEventException extends LinTimException {
    /**
     * Exception to throw if periodic event to aperiodic event does not exist.
     *
     * @param periodicEventId  periodic event id
     * @param aperiodicEventId aperiodic event id
     */
    public DataNoPeriodicEventToAperiodicEventException(int periodicEventId, int aperiodicEventId) {
        super("Error D1: Periodic event " + periodicEventId + " to aperiodic event " + aperiodicEventId + " does not " +
            "exist.");
    }
}
