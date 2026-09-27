package net.lintim.io;

import net.lintim.model.Trip;
import net.lintim.util.Config;

import java.util.Collection;

/**
 * Class to write files of trips.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class TripWriter {

    private final Collection<Trip> trips;
    private final String fileName;
    private final String header;

    private TripWriter(Builder builder) {
        this.trips = builder.trips;
        this.fileName = "".equals(builder.fileName) ? builder.config.getStringValue("default_trips_file") :
            builder.fileName;
        this.header = "".equals(builder.header) ? builder.config.getStringValue("trip_header") : builder.header;
    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     */
    public void write() {
        CsvWriter.writeCollection(fileName, header, trips, Trip::toCsvStrings, null);
    }

    /**
     * Builder object for a trip writer.
     *
     * Use {@link #Builder(Collection)} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(Collection)}. To create a writer object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String fileName = "";
        private String header = "";
        private Collection<Trip> trips;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *          trips (given in constructor) - the trips to write. Can only be set in the constructor
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file name and header from.
         *          This will only happen, if the file name or header is not given, but queried.
         *     </li>
         *     <li>
         *          file name (dependent on config) - the file name to write the trips to
         *     </li>
         *     <li>
         *          header (dependent on config) - the header to use for the trip file
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a writer with the given parameters.
         * @param trips the trips to write
         */
        public Builder(Collection<Trip> trips) {
            if (trips == null) {
                throw new IllegalArgumentException("trips in trip writer builder can not be null");
            }
            this.trips = trips;
        }

        /**
         * Set the trip file name
         * @param fileName the file to write the trips to
         * @return this object
         */
        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Set the header for the trip file
         * @param header the trip header
         * @return this object
         */
        public Builder setHeader(String header) {
            this.header = header;
            return this;
        }

        /**
         * Set the config. The config is used to read the file name or the header if they are queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new trip writer with the current builder settings
         * @return the new writer. Use {@link #write()} for the writing process.
         */
        public TripWriter build() {
            return new TripWriter(this);
        }
    }
}
