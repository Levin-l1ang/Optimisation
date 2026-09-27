package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.StationLimit;
import net.lintim.util.Config;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to read a station limit file.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class StationLimitReader {

    private final Map<Integer, StationLimit> stationLimits;
    private final String fileName;

    private StationLimitReader(Builder builder) {
        stationLimits = builder.stationLimits == null ? new HashMap<>() : builder.stationLimits;
        fileName = !"".equals(builder.fileName) ? builder.fileName : builder.config.getStringValue("filename_station_limit_file");
    }

    /**
     * Read the station limits into a map from a LinTim input file. For controlling which file to read, the given
     * {@link Builder} object will be used. See the corresponding documentation for possible configuration options.
     * @return a map with the read station limits, stored by stop id.
     */
    public Map<Integer, StationLimit> read() {
        CsvReader.readCsv(fileName, this::processStationLimitLine);
        return stationLimits;
    }

    private void processStationLimitLine(String[] args, int lineNumber) {
        if (args.length != 5) {
            throw new InputFormatException(fileName, args.length, 5);
        }
        int stopId, minWaitTime, maxWaitTime, minChangeTime, maxChangeTime;
        try {
            stopId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
        }
        try {
            minWaitTime = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 2, lineNumber, "int", args[1]);
        }
        try {
            maxWaitTime = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 3, lineNumber, "int", args[2]);
        }
        try {
            minChangeTime = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 4, lineNumber, "int", args[3]);
        }
        try {
            maxChangeTime = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 5, lineNumber, "int", args[4]);
        }
        stationLimits.put(stopId, new StationLimit(stopId, minWaitTime, maxWaitTime, minChangeTime, maxChangeTime));
    }

    /**
     * Builder object for a station limit reader.
     *
     * User {@link #Builder()} to create a builder with default options, afterwards use the setters to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private Map<Integer, StationLimit> stationLimits;
        private Config config = Config.getDefaultConfig();
        private String fileName = "";

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in paranthesis):
         * <ul>
         *     <li>
         *         station limits (Empty {@link HashMap}) - the map to store the station limits by stop id
         *     </li>
         *     <li>
         *         file name (dependent on config) - the file to read the station limits from
         *     </li>
         *     <li>
         *         config ({@link Config#getDefaultConfig()} - the config to read the file name from. This will only
         *         happen if the file name is not given before reading.
         *     </li>
         * </ul>
         */
        public Builder() {
        }

        public Builder setStationLimits(Map<Integer, StationLimit> stationLimits) {
            this.stationLimits = stationLimits;
            return this;
        }

        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Create a new station limit reader with the current builder settings.
         * @return the new reader. Use {@link #read()} for the reading process.
         */
        public StationLimitReader build() {
            return new StationLimitReader(this);
        }
    }
}
