package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;

/**
 * A class representing a periodic event, i.e., a node in the periodic event activity network (EAN).
 */
public class PeriodicEvent implements Node {
    protected int eventId;
    protected int stopId;
    protected EventType type;
    protected int lineId;
    protected int time;
    protected double numberOfPassengers;
    protected LineDirection direction;
    protected int lineFrequencyRepetition;

    /**
     * Creates a periodic event, i.e., a node in the periodic event activity network.
     *
     * @param eventId                   event id
     * @param stopId                    id of the corresponding stop
     * @param type                      type of the event
     * @param lineId                    id of the corresponding line
     * @param time                      periodic time at which the event takes place
     * @param numberOfPassengers        number of passengers using the event //TODO Check if this is correct
     * @param direction                 the direction of the associated line
     * @param lineFrequencyRepetition   the repetition of the line, i.e., the number of the current iteration of the
     *                                  line in this period
     */
    public PeriodicEvent(int eventId, int stopId, EventType type, int lineId, int time, double numberOfPassengers,
                         LineDirection direction, int lineFrequencyRepetition) {
        this.eventId = eventId;
        this.stopId = stopId;
        this.type = type;
        this.lineId = lineId;
        this.time = time;
        this.numberOfPassengers = numberOfPassengers;
        this.direction = direction;
        this.lineFrequencyRepetition = lineFrequencyRepetition;
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
     * Get the id of the corresponding line.
     *
     * @return the corresponding line id
     */
    public int getLineId() {
        return lineId;
    }

    /**
     * Get the periodic time at which the event takes place.
     *
     * @return the periodic time of the event
     */
    public int getTime() {
        return time;
    }

    /**
     * Set the time of the event.
     *
     * @param time the periodic time of the event
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
     * Get the direction of the line associated to the event
     * @return the direction
     */
    public LineDirection getDirection() {
        return direction;
    }

    /**
     * Set the direction of the line associated to the event. Will not change the line, only changes the event locally.
     * @param newLineDirection the new direction
     */
    public void setDirection(LineDirection newLineDirection){
        this.direction = newLineDirection;
    }

    /**
     * Get the line frequency repetition, i.e., the number of the current iteration of the line in this period
     * @return the line frequency repetition
     */
    public int getLineFrequencyRepetition() {
        return lineFrequencyRepetition;
    }

    /**
     * Set the line frequency repetition, i.e., the number of the current iteration of the line in this period.
     * @param newLineFrequencyRepetition the new repetition number
     */
    public void setLineFrequencyRepetition(int newLineFrequencyRepetition){
        this.lineFrequencyRepetition = newLineFrequencyRepetition;
    }

    /**
     * Return a string array, representing the event for a LinTim csv file
     * @return the csv representation of this event
     */
    public String[] toCsvStrings(){
        // We dont want to output any origin or destination events
        if (getType() == EventType.ORIGIN || getType() == EventType.DESTINATION) {
            return new String[0];
        }
        return new String[]{
            String.valueOf(getId()),
            getType().toString(),
            String.valueOf(getStopId()),
            String.valueOf(getLineId()),
            CsvWriter.shortenDecimalValueForOutput(numberOfPassengers),
            direction.equals(LineDirection.FORWARDS) ? ">" : "<",
            String.valueOf(lineFrequencyRepetition)
        };
    }

    /**
     * Return a string array, representing the activity for a LinTim csv file.
     * @return the csv representation of this activity
     */
    public String[] toCsvTimetableStrings(){
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
     * Return a string array, representing the activity for a LinTim csv file. Will use the given timetable for the time
     * @param timetable the timetable to use for the time
     * @return the csv representation of this activity
     */
    public String[] toCsvTimetableStrings(PeriodicTimetable<PeriodicEvent> timetable) {
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

        PeriodicEvent that = (PeriodicEvent) o;

        if (eventId != that.eventId) return false;
        if (getStopId() != that.getStopId()) return false;
        if (getLineId() != that.getLineId()) return false;
        if (getTime() != that.getTime()) return false;
        if (Double.compare(that.getNumberOfPassengers(), getNumberOfPassengers()) != 0) return false;
        return getType() != null ? getType().equals(that.getType()) : that.getType() == null;
    }

    @Override
    public int hashCode() {
        int result = eventId;
        result = 31 * result + getStopId();
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        result = 31 * result + getLineId();
        return result;
    }

    @Override
    public String toString() {
        return "Periodic Event " + Arrays.toString(toCsvStrings());
    }
}
