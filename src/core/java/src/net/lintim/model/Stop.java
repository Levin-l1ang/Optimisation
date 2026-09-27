package net.lintim.model;

import net.lintim.io.CsvWriter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Template implementation of a stop in a PTN.
 */
public class Stop implements Node {
    protected int stopId;
    protected String shortName;
    protected String longName;
    protected double xCoordinate;
    protected double yCoordinate;
    protected double longitude;
    protected double latitude;
    protected boolean station;

    /**
     * Create a new Stop given the information of a LinTim stop.
     * @param stopId the id of the stop. Needs to be unique for any graph, this stop may be part of.
     * @param shortName the short name of the stop. This is a short representation of the stop. Need not be unique.
     * @param longName the long name of the stop. This is a longer representation of the stop.
     * @param xCoordinate the x-coordinate of the stop. This should be the longitude coordinate of the stop. Using
     *                       the euclidean distance on the coordinates of the stop should result in a length given in
     *                       kilometers.
     * @param yCoordinate the y-coordinate of the stop. This should be the latitude coordinate of the stop. Using
     *                       the euclidean distance on the coordinates of the stop should result in a length given in
     *                       kilometers.
     * @param longitude the longitude of the stop
     * @param latitude the latitude of the stop
     */
    public Stop(int stopId, String shortName, String longName, double xCoordinate, double yCoordinate, double longitude,
                double latitude) {
        this.stopId = stopId;
        this.shortName = shortName;
        this.longName = longName;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.longitude = longitude;
        this.latitude = latitude;
        this.station = true;
    }

	/**
	 * Create a new Stop given the information of a LinTim stop. Will set longitude and latitude to 0.
	 * @param stopId the id of the stop. Needs to be unique for any graph, this stop may be part of.
	 * @param shortName the short name of the stop. This is a short representation of the stop. Need not be unique.
	 * @param longName the long name of the stop. This is a longer representation of the stop.
	 * @param xCoordinate the x-coordinate of the stop. This should be the longitude coordinate of the stop. Using
     *                       the euclidean distance on the coordinates of the stop should result in a length given in
     *                       kilometers.
	 * @param yCoordinate the y-coordinate of the stop. This should be the latitude coordinate of the stop. Using
     *                       the euclidean distance on the coordinates of the stop should result in a length given in
     *                       kilometers.
	 */
	public Stop(int stopId, String shortName, String longName, double xCoordinate, double yCoordinate) {
		this(stopId, shortName, longName, xCoordinate, yCoordinate, 0, 0);
	}

	public int getId() {
		return stopId;
	}

	public void setId(int id) {
		this.stopId = id;
	}

	/**
	 * Get the short name of the stop. This is a short representation of the stop. Need not be unique.
	 * @return the short name
	 */
	public String getShortName() {
		return shortName;
	}

	/**
	 * Get the long name of the stop. This is a longer representation of the stop.
	 * @return the long name
	 */
	public String getLongName() {
		return longName;
	}

	/**
	 * Get the x-coordinate of the stop. This should be the longitude coordinate of the stop.  Using
     * the euclidean distance on the coordinates of the stop should result in a length given in kilometers.
	 * @return the x-coordinate
	 */
	public double getxCoordinate() {
		return xCoordinate;
	}

	/**
	 * Get the y-coordinate of the stop. This should be the latitude coordinate of the stop. Using the euclidean
     * distance on the coordinates of the stop should result in a length given in kilometers.
	 * @return the y-coordinate
	 */
	public double getyCoordinate() {
		return yCoordinate;
	}

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
	 * Get whether this stop is actually a station. E.g. it may be the case that the stop is just a candidate in a
	 * stop location problem and not a built station (at least at the time of creation). Is initially set to true.
	 * @return whether the stop is a station
	 */
	public boolean isStation() {
		return station;
	}

	/**
	 * Set whether this stop is actually a station. E.g. it may be the case that the stop is just a candidate in a
	 * stop location problem and not a built station (at least at the time of creation).
	 * @param isStation the new value
	 */
	public void setStation(boolean isStation) {
		this.station = isStation;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stop stop = (Stop) o;
        return stopId == stop.stopId && Double.compare(stop.xCoordinate, xCoordinate) == 0 &&
            Double.compare(stop.yCoordinate, yCoordinate) == 0 && Double.compare(stop.longitude, longitude) == 0 &&
            Double.compare(stop.latitude, latitude) == 0 && station == stop.station &&
            Objects.equals(shortName, stop.shortName) && Objects.equals(longName, stop.longName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopId, shortName, longName, xCoordinate, yCoordinate, station);
    }

    @Override
	public String toString() {
		return "Stop " + Arrays.toString(toCsvStrings());
	}

    /**
     * Return a string array, representing the stop for a LinTim csv file.
     * @return the csv representation of this stop
     */
    public String[] toCsvStrings() {
        return toCsvStrings(1);
    }

    /**
     * Return a string array representing the stop for a LinTim csv file.
     * @param conversionFactorCoordinates the internal coordinates are divided by this number
     * @return the csv representation of this stop
     */
	public String[] toCsvStrings(double conversionFactorCoordinates){
	    return new String[] {
	        String.valueOf(getId()),
            getShortName(),
            getLongName(),
            CsvWriter.shortenDecimalValueForOutput(getxCoordinate() / conversionFactorCoordinates),
            CsvWriter.shortenDecimalValueForOutput(getyCoordinate() / conversionFactorCoordinates)
        };
    }
}
