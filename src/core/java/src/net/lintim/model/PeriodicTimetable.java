package net.lintim.model;

import java.util.function.Function;

/**
 * Mapping from events (of whatever type) to long integers,
 * additionally maintaining a value for "time units per minute"
 * and an integer "period length"
 */
public class PeriodicTimetable<E> extends Timetable<E> {

    private int period;

    /**
     * Create a new periodic timetable with the given "time units per minute" and "period length" values.
     * @param timeUnitsPerMinute - time units per minute (may be fractional)
     * @param period - period length
     */
    public PeriodicTimetable(double timeUnitsPerMinute, int period) {
        super(timeUnitsPerMinute);
        this.period = period;
    }

    /**
     * Returns an array of times expanding one event by a given frequency within the timetable's period length.
     * @param key - the event for which the list of actual occurrence times shall be calculated
     * @param frequency - the frequency with which the event should be spread; must be at least 1
     * @param roundingFunction - a rounding method used for all instance times (Double to Long)
     * @return - an array of long integer times of length "frequency"
     */
    public long[] getRepetitionTimesInPeriod(E key, int frequency, Function<Double, Long> roundingFunction) {
        if (frequency < 1) return new long[0];
        double interval = 1.0 * period / frequency;
        long[] periodicTimes = new long[frequency];
        for (int i = 0; i < frequency; i++)
            periodicTimes[i] = (this.get(key) + roundingFunction.apply(i * interval)) % period;
        return periodicTimes;
    }

    /**
     * Get the period of this timetable.
     * @return the period length
     */
    public int getPeriod(){
        return period;
    }

    @Override
    public String toString() {
        String builder = "Periodic Timetable: Period " + period + "\n" +
            super.toString();
        return builder;
    }
}
