package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.exception.LinTimException;
import net.lintim.model.Depot;
import net.lintim.util.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to read depot files.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class DepotReader {
    private final String depotFileName;
    private final List<Depot> depots;
    private final double conversionFactorCoordinates;

    /**
     * Private constructor of a depot reader.
     *
     * @param builder the builder object containing all configuration
     */
    private DepotReader(Builder builder) {
        depotFileName = "".equals(builder.depotFileName) ? builder.config.getStringValue("default_depot_file") :
            builder.depotFileName;
        depots = builder.depots == null ? new ArrayList<>() : builder.depots;
        conversionFactorCoordinates = builder.conversionFactorCoordinates == -1 ?
            builder.config.getDoubleValue("gen_conversion_coordinates") : builder.conversionFactorCoordinates;
    }

    /**
     * Read the depots. To determine which file to read, a {@link Builder} object is used. See the
     * corresponding documentation for possible configuration options.
     *
     * @return the read depot data
     * @throws LinTimException if the depot file contains no depots
     */
    public List<Depot> read() {
        CsvReader.readCsv(depotFileName, this::processDepotLine);
        return depots;
    }

    /**
     * Process the contents of a depot line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException             if the line contains not exactly 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     */
    private void processDepotLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException {
        if (args.length != 4) {
            throw new InputFormatException(depotFileName, args.length, 4);
        }

        int depotId;
        double xCoordinate;
        double yCoordinate;
        int numberOfVehicles;

        try {
            depotId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(depotFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            xCoordinate = Double.parseDouble(args[1]) * conversionFactorCoordinates;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(depotFileName, 2, lineNumber, "double", args[1]);
        }

        try {
            yCoordinate = Double.parseDouble(args[2]) * conversionFactorCoordinates;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(depotFileName, 3, lineNumber, "double", args[2]);
        }

        try {
            numberOfVehicles = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(depotFileName, 4, lineNumber, "int", args[3]);
        }

        Depot depot = new Depot(depotId, xCoordinate, yCoordinate, numberOfVehicles);
        depots.add(depot);
    }

    /**
     * Builder object for a depot reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String depotFileName = "";
        private Config config = Config.getDefaultConfig();
        private List<Depot> depots;
        private double conversionFactorCoordinates = -1;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         depot file name (dependent on config) - the file to read
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file name from. This will only
         *         happen, if the file name is not given, but queried.
         *     </li>
         *     <li>
         *          depots (empty {@link List<Depot>}) - the list to write the read depots to.
         *     </li>
         *     <li>
         *         conversionFactorCoordinate (dependent on config) - the conversion factor for the coordinates
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder() {}

        /**
         * Set the file name to read the depots from.
         *
         * @param depotFileName the depot file name
         * @return this object
         */
        public Builder setDepotFileName(String depotFileName) {
            this.depotFileName = depotFileName;
            return this;
        }

        /**
         * Set the list to add the read depots to.
         *
         * @param depots the list for the depots
         * @return this object
         */
        public Builder setDepots(List<Depot> depots) {
            this.depots = depots;
            return this;
        }

        /**
         * Set the conversion factor for coordinates.
         *
         * @param conversionFactorCoordinates the conversion factor for coordinates
         * @return this object
         */
        public Builder setConversionFactorCoordinates(double conversionFactorCoordinates) {
            this.conversionFactorCoordinates = conversionFactorCoordinates;
            return this;
        }

        /**
         * Set the config to read the filename from, if none is given beforehand and the filename is queried.
         *
         * @param config the config to read from
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new depot reader with the current builder settings.
         *
         * @return the new reader. Use {@link DepotReader#read()} for the reading process.
         */
        public DepotReader build() {
            return new DepotReader(this);
        }
    }
}