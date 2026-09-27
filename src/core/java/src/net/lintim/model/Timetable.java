package net.lintim.model;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Mapping from events (of whatever type) to long integers,
 * additionally maintaining a value for "time units per minute".
 */
public class Timetable<E> extends HashMap<E, Long> {

    private double timeUnitsPerMinute;

    /**
     * Create a new timetable with the given "time units per minute" value.
     * @param timeUnitsPerMinute - time units per minute (may be fractional)
     */
    public Timetable(double timeUnitsPerMinute) {
        super();
        this.timeUnitsPerMinute = timeUnitsPerMinute;
    }

    /**
     * Returns the current "time units per minute" value for this timetable
     * @return - time units per minute (may be fractional)
     */
    public double getTimeUnitsPerMinute() {
        return timeUnitsPerMinute;
    }

    /**
     * Updates the "time units per minute" value and converts existing timetable entries.
     * @param timeUnitsPerMinute - new value for "time units per minute"
     * @param roundingFunction - a rounding method used for all recalculations (Double to Long)
     */
    public void setTimeUnitsPerMinute(int timeUnitsPerMinute, Function<Double, Long> roundingFunction) {
        if (this.timeUnitsPerMinute == timeUnitsPerMinute) return;
        double factor = 1.0 * timeUnitsPerMinute / this.timeUnitsPerMinute;
        this.replaceAll((E key, Long oldValue) -> roundingFunction.apply(oldValue * factor));
        this.timeUnitsPerMinute = timeUnitsPerMinute;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Timetable: \n");
        for(Map.Entry<E, Long> entry : entrySet()){
            builder.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return builder.toString();
    }
}
