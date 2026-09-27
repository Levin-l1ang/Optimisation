package net.lintim.exception;

/**
 * Exception to throw if periodic activity to aperiodic activity does not exist.
 */
public class DataNoPeriodicActivityToAperiodicActivityException extends LinTimException {
    /**
     * Exception to throw if periodic activity to aperiodic activity does not exist.
     *
     * @param periodicActivityId  periodic activity id
     * @param aperiodicActivityId aperiodic activity id
     */
    public DataNoPeriodicActivityToAperiodicActivityException(int periodicActivityId, int aperiodicActivityId) {
        super("Error D2: Periodic activity " + periodicActivityId + " to aperiodic activity " + aperiodicActivityId +
            " does not exist.");
    }
}
