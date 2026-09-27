package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;

/**
 * A class representing an aperiodic event, i.e., a node in the aperiodic event activity network (EAN).
 */
public class AperiodicEvent implements Node {
    protected int eventId;
    protected int periodicEventId;
    protected int stopId;
    protected EventType type;
    protected int time;
    protected double numberOfPassengers;

    /**
     * Creates an aperiodic event, i.e., a node in the aperiodic event activity network.
     *
     * @param eventId            event id
     * @param periodicEventId    id of the corresponding periodic event
     * @param stopId             id of the corresponding stop
     * @param type               type of the event
     * @param time               periodic time at which the event takes place
     * @param numberOfPassengers number of passengers using the event //TODO Check if this is correct
     */
    public AperiodicEvent(int eventId, int periodicEventId, int stopId, EventType type, int time, double
        numberOfPassengers) {
        this.eventId = eventId;
        this.periodicEventId = periodicEventId;
        this.stopId = stopId;
        this.type = type;
        this.time = time;
        this.numberOfPassengers = numberOfPassengers;
    }

    @Override
    public int getId() {
        return eventId;
    }

    @Override
    public void setId(int id) {
        eventId = id;
    }

    /**
     * Get the id of the corresponding periodic event
     *
     * @return the id of the corresponding periodic event
     */
    public int getPeriodicEventId() {
        return periodicEventId;
    }

    /**
     * Get the id of the corresponding stop.
     *
     * @return the id of the corresponding stop
     */
    public int getStopId() {
        return stopId;
    }

    /**
     * Get the type of the event, which is specified in EventType.
     *
     * @return the type of the event
     */
    public EventType getType() {
        return type;
    }

    /**
     * Get the periodic time at which the event takes place.
     *
     * @return the time of the event
     */
    public int getTime() {
        return time;
    }

    /**
     * Set the time of the event.
     *
     * @param time the time of the event
     */
    public void setTime(int time) {
        this.time = time;
    }

    /**
     * Get the number of passengers using the event.
     * //TODO check correctness
     *
     * @return the number of passengers using the event
     */
    public double getNumberOfPassengers() {
        return numberOfPassengers;
    }

    /**
     * Return a string array, representing the event for a LinTim csv file. The time in the event will be used.
     * @return the csv representation of this event
     */
    public String[] toCsvStrings(){
        // We dont want to output any origin or destination events
        if (getType() == EventType.ORIGIN || getType() == EventType.DESTINATION) {
            return new String[0];
        }
        return new String[] {
            String.valueOf(getId()),
            String.valueOf(getPeriodicEventId()),
            getType().toString(),
            String.valueOf(getTime()),
            CsvWriter.shortenDecimalValueForOutput(getNumberOfPassengers()),
            String.valueOf(getStopId())
        };
    }

    /**
     * Return a string array, representing the event for a LinTim csv file
     * @param timetable the timetable to use for the time
     * @return the csv representation of this event
     */
    public String[] toCsvStrings(Timetable<AperiodicEvent> timetable){
        // We dont want to output any origin or destination events
        if (getType() == EventType.ORIGIN || getType() == EventType.DESTINATION) {
            return new String[0];
        }
        return new String[] {
            String.valueOf(getId()),
            String.valueOf(getPeriodicEventId()),
            getType().toString(),
            String.valueOf(timetable.get(this)),
            CsvWriter.shortenDecimalValueForOutput(getNumberOfPassengers()),
            String.valueOf(getStopId())
        };
    }

    /**
     * Return a string array, representing a timetable line for a LinTim csv file. The time in the event will be used.
     * @return the csv representation of the time of this event
     */
    public String[] toTimetableCsvStrings() {
        // We dont want to output any origin or destination events
        if (getType() == EventType.ORIGIN || getType() == EventType.DESTINATION) {
            return new String[0];
        }
        return new String[] {
            String.valueOf(getId()),
            String.valueOf(getTime())
        };
    }

    /**
     * Return a string array, representing a timetable line for a LinTim csv file
     * @param timetable the timetable to use for the time
     * @return the csv representation of this event
     */
    public String[] toTimetableCsvStrings(Timetable<AperiodicEvent> timetable) {
        // We dont want to output any origin or destination events
        if (getType() == EventType.ORIGIN || getType() == EventType.DESTINATION) {
            return new String[0];
        }
        return new String[] {
            String.valueOf(getId()),
            String.valueOf(timetable.get(this))
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AperiodicEvent that = (AperiodicEvent) o;

        if (eventId != that.eventId) return false;
        if (getPeriodicEventId() != that.getPeriodicEventId()) return false;
        if (getStopId() != that.getStopId()) return false;
        if (getTime() != that.getTime()) return false;
        if (Double.compare(that.getNumberOfPassengers(), getNumberOfPassengers()) != 0) return false;
        return getType() != null ? getType().equals(that.getType()) : that.getType() == null;
    }

    @Override
    public int hashCode() {
        int result = eventId;
        result = 31 * result + getPeriodicEventId();
        result = 31 * result + getStopId();
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Aperiodic Event " + Arrays.toString(toCsvStrings());
    }
}
