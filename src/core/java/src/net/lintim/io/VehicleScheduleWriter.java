package net.lintim.io;

import net.lintim.exception.OutputFileException;
import net.lintim.model.Circulation;
import net.lintim.model.Trip;
import net.lintim.model.VehicleSchedule;
import net.lintim.model.VehicleTour;
import net.lintim.util.Config;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class to write files of a vehicle schedule.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class VehicleScheduleWriter {
    private final String filename;
    private final String header;
    private final VehicleSchedule vehicleSchedule;

    private VehicleScheduleWriter(Builder builder) {
        this.vehicleSchedule = builder.vehicleSchedule;
        this.filename = "".equals(builder.fileName) ?
            builder.config.getStringValue("default_vehicle_schedule_file") : builder.fileName;
        this.header = "".equals(builder.header) ? builder.config.getStringValue("vehicle_schedule_header") :
            builder.header;
    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     */
    public void write() {
        CsvWriter writer = new CsvWriter(filename, header);
        try {
            writeVehicleSchedule(writer, vehicleSchedule);
            writer.close();
        } catch (IOException e) {
            throw new OutputFileException(filename);
        }
    }

    private static void writeVehicleSchedule(CsvWriter writer, VehicleSchedule vehicleSchedule) throws IOException {
        for(Circulation circulation : vehicleSchedule.getCirculations()){
            for(VehicleTour vehicleTour : circulation.getVehicleTourList()){
                List<Map.Entry<Integer, Trip>> tripList = vehicleTour.getTripsWithIds().stream().sorted(Comparator.comparing(Map.Entry::getKey)).collect(Collectors.toList());
                for(Map.Entry<Integer, Trip> tripEntry : tripList){
                    writer.writeLine(vehicleSchedule.toCsvStrings(circulation.getCirculationId(), vehicleTour
                        .getVehicleId(), tripEntry.getKey()));
                }
            }
        }
    }

    /**
     * Builder object for an aperiodic ean writer.
     *
     * Use {@link #Builder(VehicleSchedule)} to create a builder with default options, afterwards use the setter
     * to adapt it. The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(VehicleSchedule)}. To create a writer
     * object, use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String fileName = "";
        private String header = "";
        private final VehicleSchedule vehicleSchedule;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *          vehicle schedule (given in constructor) - the vehicle schedule to write. Can only be set in the
         *          constructor.
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file name and header from.
         *          This will only happen, if the file name or header is not given, but queried.
         *     </li>
         *     <li>
         *          file name (dependent on config) - the file name to write the vehicle schedule to
         *     </li>
         *     <li>
         *          header (dependent on config) - the header to use for the vehicle schedule file
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a writer with the given parameters.
         * @param vehicleSchedule the vehicle schedule to write
         */
        public Builder(VehicleSchedule vehicleSchedule) {
            if (vehicleSchedule == null) {
                throw new IllegalArgumentException("vehicle schedule in vehicle schedule writer builder can not be null");
            }
            this.vehicleSchedule = vehicleSchedule;
        }

        /**
         * Set the vehicle schedule file name
         * @param fileName the file to write the vehicle schedule to
         * @return this object
         */
        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Set the header for the vehicle schedule file
         * @param header the vehicle schedule header
         * @return this object
         */
        public Builder setHeader(String header) {
            this.header = header;
            return this;
        }

        /**
         * Set the config. The config is used to read the file name or header if it is queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new vehicle schedule writer with the current builder settings
         * @return the new writer. Use {@link #write()} for the writing process.
         */
        public VehicleScheduleWriter build() {
            return new VehicleScheduleWriter(this);
        }
    }


}
