package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.*;
import net.lintim.util.Config;

/**
 * Class to read files of a vehicle schedule.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class VehicleScheduleReader {
	private final String fileName;
	private final VehicleSchedule vehicleSchedule;

	private VehicleScheduleReader(Builder builder) {
        this.vehicleSchedule = builder.vehicleSchedule == null ? new VehicleSchedule() : builder.vehicleSchedule;
        this.fileName = "".equals(builder.fileName) ?
            builder.config.getStringValue("default_vehicle_schedule_file") : builder.fileName;
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read vehicle schedule
     */
	public VehicleSchedule read() {
	    CsvReader.readCsv(fileName, this::processVehicleScheduleLine);
        return vehicleSchedule;
    }

    /**
     * Method to process a line from a vehicle scheduling file in LinTim format. The read trip will be appended to
     * the vehicle schedule provided in the {@link Builder} object used to create this reader.
     * @param args the line to process
     * @param lineNumber the number of the line read, will be used for error handling
     * @throws InputFormatException if the line is formatted incorrectly, i.e., has the wrong number of columns
     * @throws InputTypeInconsistencyException if some entry is in the wrong format
     */
	private void processVehicleScheduleLine(String[] args, int lineNumber) throws InputFormatException,
			InputTypeInconsistencyException {
		if(args.length != 13){
			throw new InputFormatException(fileName, args.length, 13);
		}
		int circulationId;
		int vehicleId;
		int tripNumberOfVehicle;
		TripType tripType;
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
			circulationId = Integer.parseInt(args[0]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
		}
		try{
			vehicleId = Integer.parseInt(args[1]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 2, lineNumber, "int", args[1]);
		}
		try{
			tripNumberOfVehicle = Integer.parseInt(args[2]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 3, lineNumber, "int", args[2]);
		}
		switch (args[3].toUpperCase()){
			case "EMPTY":
				tripType = TripType.EMPTY;
				break;
			case "TRIP":
				tripType = TripType.TRIP;
				break;
			default:
				throw new InputTypeInconsistencyException(fileName, 4, lineNumber, "TripType", args[3]);
		}
		try{
			startAperiodicEventId = Integer.parseInt(args[4]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 5, lineNumber, "int", args[4]);
		}
		try{
			startPeriodicEventId = Integer.parseInt(args[5]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 6, lineNumber, "int", args[5]);
		}
		try{
			startStopId = Integer.parseInt(args[6]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 7, lineNumber, "int", args[6]);
		}
		try{
			startTime = Integer.parseInt(args[7]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 8, lineNumber, "int", args[7]);
		}
		try{
			endAperiodicEventId = Integer.parseInt(args[8]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 9, lineNumber, "int", args[8]);
		}
		try{
			endPeriodicEventId = Integer.parseInt(args[9]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 10, lineNumber, "int", args[9]);
		}
		try{
			endStopId = Integer.parseInt(args[10]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 11, lineNumber, "int", args[10]);
		}
		try{
			endTime = Integer.parseInt(args[11]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 12, lineNumber, "int", args[11]);
		}
		try{
			lineId = Integer.parseInt(args[12]);
		} catch (NumberFormatException e){
			throw new InputTypeInconsistencyException(fileName, 13, lineNumber, "int", args[12]);
		}
		Trip trip = new Trip(startAperiodicEventId, startPeriodicEventId, startStopId, startTime,
				endAperiodicEventId, endPeriodicEventId, endStopId, endTime, lineId, tripType);
		//Try to find the appropriate vehicle to add the trip to.
		Circulation circulation = vehicleSchedule.getCirculation(circulationId);
		if(circulation == null){
			circulation = new Circulation(circulationId);
			vehicleSchedule.addCirculation(circulation);
		}
		VehicleTour vehicleTour = circulation.getVehicleTour(vehicleId);
		if(vehicleTour == null){
			vehicleTour = new VehicleTour(vehicleId);
			circulation.addVehicle(vehicleTour);
		}
		vehicleTour.addTrip(tripNumberOfVehicle, trip);
	}

    /**
     * Builder object for a vehicle schedule reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String fileName = "";
        private Config config = Config.getDefaultConfig();
        private VehicleSchedule vehicleSchedule;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         vehicle schedule (Empty {@link VehicleSchedule}) - the vehicle schedule to store the read objects in.
         *     </li>
         *     <li>
         *         config ({@link Config#getDefaultConfig()}) - the config to read the file name from. This will only
         *         happen, if the file name is not given, but queried.
         *     </li>
         *     <li>
         *         file name (dependent on config) - the file name to read the vehicle schedule from
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder() {}

        /**
         * Set the file name to read the vehicle schedule from
         * @param fileName the vehicle schedule file name
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
         * Set the vehicle schedule to store the data in
         * @param vehicleSchedule the vehicle schedule
         * @return this object
         */
        public Builder setVehicleSchedule(VehicleSchedule vehicleSchedule) {
            this.vehicleSchedule = vehicleSchedule;
            return this;
        }

        /**
         * Create a new vehicle schedule reader with the current builder settings
         * @return the new reader. Use {@link #read()} for the reading process.
         */
        public VehicleScheduleReader build() {
            return new VehicleScheduleReader(this);
        }
    }
}
