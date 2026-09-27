package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.Trip;
import net.lintim.model.TripType;
import net.lintim.util.Config;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Class to read files of trips.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class TripReader {
	private final String fileName;
	private final Collection<Trip> trips;

	private TripReader(Builder builder) {
        this.trips = builder.trips == null ? new ArrayList<>() : builder.trips;
        this.fileName = "".equals(builder.fileName) ? builder.config.getStringValue("default_trips_file") :
            builder.fileName;
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read trips
     */
	public Collection<Trip> read() {
	    CsvReader.readCsv(fileName, this::processTripLine);
	    return trips;
    }

    /**
     * Process a line in the LinTim trip format and append the information to the collection given in the
     * {@link Builder}.
     * @param args the line to process
     * @param lineNumber the number of the line read, will be used for error handling
     * @throws InputFormatException if the line is formatted incorrectly, i.e., has the wrong number of columns
     * @throws InputTypeInconsistencyException if some entry is in the wrong format
     */
	private void processTripLine(String[] args, int lineNumber) throws InputFormatException,
			InputTypeInconsistencyException {
		if(args.length != 9){
			throw new InputFormatException(fileName, args.length, 9);
		}
		int startAperiodicEventId;
		int startPeriodicEventId;
		int startStopId;
		int startTime;
		int endAperiodicEventId;
		int endPeriodicEventId;
		int endStopId;
		int endTime;
		int lineId;
		try{
			startAperiodicEventId = Integer.parseInt(args[0]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
		}
		try{
			startPeriodicEventId = Integer.parseInt(args[1]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 2, lineNumber, "int", args[1]);
		}
		try{
			startStopId = Integer.parseInt(args[2]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 3, lineNumber, "int", args[2]);
		}
		try{
			startTime = Integer.parseInt(args[3]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 4, lineNumber, "int", args[3]);
		}
		try{
			endAperiodicEventId = Integer.parseInt(args[4]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 5, lineNumber, "int", args[4]);
		}
		try{
			endPeriodicEventId = Integer.parseInt(args[5]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 6, lineNumber, "int", args[5]);
		}
		try{
			endStopId = Integer.parseInt(args[6]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 7, lineNumber, "int", args[6]);
		}
		try{
			endTime = Integer.parseInt(args[7]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 8, lineNumber, "int", args[7]);
		}
		try{
			lineId = Integer.parseInt(args[8]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 9, lineNumber, "int", args[8]);
		}
		Trip trip = new Trip(startAperiodicEventId, startPeriodicEventId, startStopId, startTime,
				endAperiodicEventId, endPeriodicEventId, endStopId, endTime, lineId, TripType.TRIP);
		trips.add(trip);
	}

    /**
     * Builder object for an aperiodic ean reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String fileName = "";
        private Config config  = Config.getDefaultConfig();
        private Collection<Trip> trips;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         trips (empty collection) - the collection to store the read trips
         *     </li>
         *     <li>
         *         config ({@link Config#getDefaultConfig()}) - the config to read the file name from. This will only
         *         happen, if the file name is not given, but queried.
         *     </li>
         *     <li>
         *         file name (dependent on config) - the file name to read the trips from
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder(){}

        /**
         * Set the file name to read the trips from
         * @param fileName the trip file name
         * @return this object
         */
        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Set the config. The config is used to read the file name if it is queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set the collection to store the read trips in
         * @param trips the trip collection
         * @return this object
         */
        public Builder setTrips(Collection<Trip> trips) {
            this.trips = trips;
            return this;
        }

        /**
         * Create a new trip reader with the current builder settings
         * @return the new reader. Use {@link #read()} for the reading process.
         */
        public TripReader build() {
            return new TripReader(this);
        }
    }

}
