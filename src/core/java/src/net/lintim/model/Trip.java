package net.lintim.model;

import net.lintim.util.Logger;

import java.util.Arrays;

/**
 * Class representing a trip in the vehicle schedule. A trip contains all necessary information to reconstruct the
 * trip of a line served by a vehicle.
 */
public class Trip {
	private static Logger logger = new Logger(Trip.class);
	private int startAperiodicEventId;
	private int startPeriodicEventId;
	private int startStopId;
	private int startTime;
	private int endAperiodicEventId;
	private int endPeriodicEventId;
	private int endStopId;
	private int endTime;
	private int lineId;
	private TripType tripType;

    /**
     * Create a new trip with the given information.
     * @param startAperiodicEventId the id of the aperiodic start event
     * @param startPeriodicEventId the id of the periodic start event
     * @param startStopId the id of the start stop
     * @param startTime the start time of the trip, in seconds
     * @param endAperiodicEventId the id of the aperiodic end event
     * @param endPeriodicEventId the id of the periodic end event
     * @param endStopId the id of the end stop
     * @param endTime the end time of the trip, in seconds
     * @param lineId the id of the corresponding line
     * @param tripType the type of the trip
     */
	public Trip(int startAperiodicEventId, int startPeriodicEventId, int startStopId, int startTime, int
			endAperiodicEventId, int endPeriodicEventId, int endStopId, int endTime, int lineId, TripType tripType) {
		this.startAperiodicEventId = startAperiodicEventId;
		this.startPeriodicEventId = startPeriodicEventId;
		this.startStopId = startStopId;
		this.startTime = startTime;
		this.endAperiodicEventId = endAperiodicEventId;
		this.endPeriodicEventId = endPeriodicEventId;
		this.endStopId = endStopId;
		this.endTime = endTime;
		this.lineId = lineId;
		this.tripType = tripType;
		if((lineId == -1 && tripType == TripType.TRIP) || (lineId != -1 && tripType == TripType.EMPTY)){
			logger.warn("Unfitting line id and trip type " + lineId + " and " + tripType.name());
		}
	}

    /**
     * Get the id of the aperiodic start event
     * @return the id of the aperiodic start event
     */
	public int getStartAperiodicEventId() {
		return startAperiodicEventId;
	}

    /**
     * Set the id of the aperiodic start event
     * @param startAperiodicEventId the new aperiodic start event id
     */
	public void setStartAperiodicEventId(int startAperiodicEventId) {
		this.startAperiodicEventId = startAperiodicEventId;
	}

    /**
     * Get the id of the periodic start event
     * @return the id of the periodic start event
     */
	public int getStartPeriodicEventId() {
		return startPeriodicEventId;
	}

    /**
     * Set the id of the periodic start event
     * @param startPeriodicEventId the new periodic start event id
     */
	public void setStartPeriodicEventId(int startPeriodicEventId) {
		this.startPeriodicEventId = startPeriodicEventId;
	}

    /**
     * Get the id of the start stop
     * @return the id of the start stop
     */
	public int getStartStopId() {
		return startStopId;
	}

    /**
     * Set the id of the start stop
     * @param startStopId the new id of the start stop
     */
	public void setStartStopId(int startStopId) {
		this.startStopId = startStopId;
	}

    /**
     * Get the start time of the trip
     * @return the start time
     */
	public int getStartTime() {
		return startTime;
	}

    /**
     * Set the start time of the trip
     * @param startTime the new start time
     */
	public void setStartTime(int startTime) {
		this.startTime = startTime;
	}

    /**
     * Get the id of the aperiodic end event
     * @return the id of the aperiodic end event
     */
	public int getEndAperiodicEventId() {
		return endAperiodicEventId;
	}

    /**
     * Set the id of the aperiodic end event
     * @param endAperiodicEventId the enw aperiodic end event id
     */
	public void setEndAperiodicEventId(int endAperiodicEventId) {
		this.endAperiodicEventId = endAperiodicEventId;
	}

    /**
     * Get the id of the periodic end event
     * @return the id of the periodic end event
     */
	public int getEndPeriodicEventId() {
		return endPeriodicEventId;
	}

    /**
     * Set the id of the periodic end event
     * @param endPeriodicEventId the new periodic end event id
     */
	public void setEndPeriodicEventId(int endPeriodicEventId) {
		this.endPeriodicEventId = endPeriodicEventId;
	}

    /**
     * Get the id of the end stop
     * @return the id of the end stop
     */
	public int getEndStopId() {
		return endStopId;
	}

    /**
     * Set the id of the end stop
     * @param endStopId the new id of the end stop
     */
	public void setEndStopId(int endStopId) {
		this.endStopId = endStopId;
	}

    /**
     * Get the end time of the trip
     * @return the end time
     */
	public int getEndTime() {
		return endTime;
	}

    /**
     * Set the end time of the trip
     * @param endTime the new end time
     */
	public void setEndTime(int endTime) {
		this.endTime = endTime;
	}

    /**
     * Get the id of the corresponding line of the trip. -1 is used for empty trips.
     * @return the id of the corresponding line of the trip
     */
	public int getLineId() {
		return lineId;
	}

    /**
     * Set a new line if for this trip.
     * @param lineId the new line id.
     */
	public void setLineId(int lineId) {
		this.lineId = lineId;
	}

    /**
     * Get the type of the trip, see {@link TripType}.
     * @return the trip type
     */
	public TripType getTripType() {
		return tripType;
	}

    /**
     * Set a new trip type of this trip
     * @param tripType the new type
     */
	public void setTripType(TripType tripType) {
		this.tripType = tripType;
	}

    /**
     * Get a csv representation of this trip.
     * @return the representation of this trip
     */
    public String[] toCsvStrings(){
	    return new String[] {
            String.valueOf(startAperiodicEventId),
            String.valueOf(startPeriodicEventId),
            String.valueOf(startStopId),
            String.valueOf(startTime),
            String.valueOf(endAperiodicEventId),
            String.valueOf(endPeriodicEventId),
            String.valueOf(endStopId),
            String.valueOf(endTime),
            String.valueOf(lineId)
        };
    }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Trip trip = (Trip) o;

		if (startAperiodicEventId != trip.startAperiodicEventId) return false;
		if (startPeriodicEventId != trip.startPeriodicEventId) return false;
		if (startStopId != trip.startStopId) return false;
		if (startTime != trip.startTime) return false;
		if (endAperiodicEventId != trip.endAperiodicEventId) return false;
		if (endPeriodicEventId != trip.endPeriodicEventId) return false;
		if (endStopId != trip.endStopId) return false;
		if (endTime != trip.endTime) return false;
		if (lineId != trip.lineId) return false;
		return tripType == trip.tripType;
	}

	@Override
	public int hashCode() {
		int result = startAperiodicEventId;
		result = 31 * result + startPeriodicEventId;
		result = 31 * result + startStopId;
		result = 31 * result + startTime;
		result = 31 * result + endAperiodicEventId;
		result = 31 * result + endPeriodicEventId;
		result = 31 * result + endStopId;
		result = 31 * result + endTime;
		result = 31 * result + lineId;
		result = 31 * result + (tripType != null ? tripType.hashCode() : 0);
		return result;
	}

    @Override
    public String toString() {
        return "Trip " + Arrays.toString(toCsvStrings());
    }
}
