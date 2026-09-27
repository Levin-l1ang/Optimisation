package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.DemandPoint;
import net.lintim.util.Config;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Class to read demand files.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class DemandReader {
    private final String demandFileName;
    private final Collection<DemandPoint> demand;
    private final double conversionFactorCoordinates;


    private DemandReader(Builder builder) {
        demandFileName = "".equals(builder.demandFileName) ? builder.config.getStringValue("default_demand_file") :
            builder.demandFileName;
        demand = builder.demand == null ? new ArrayList<>() : builder.demand;
        conversionFactorCoordinates = builder.conversionFactorCoordinates == -1 ?
            builder.config.getDoubleValue("gen_conversion_coordinates") : builder.conversionFactorCoordinates;
    }

    /**
     * Read the demand. To determine which file to read, a {@link Builder} object is used. See the
     * corresponding documentation for possible configuration options.
     * @return the read demand data
     */
    public Collection<DemandPoint> read() {
        CsvReader.readCsv(demandFileName, this::processDemandLine);
        return demand;
    }

    /**
     * Process the contents of a demand line.
     * @param args the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line contains not exactly 6 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     */
    private void processDemandLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException {
        if (args.length != 6) {
            throw new InputFormatException(demandFileName, args.length, 6);
        }
        int stopId;
        String shortName;
        String longName;
        double xCoordinate;
        double yCoordinate;
        int demandAtDemandPoint;
        try {
            stopId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(demandFileName, 1, lineNumber, "int", args[0]);
        }
        shortName = args[1];
        longName = args[2];
        try {
            xCoordinate = Double.parseDouble(args[3]) * conversionFactorCoordinates;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(demandFileName, 4, lineNumber, "double", args[3]);
        }
        try {
            yCoordinate = Double.parseDouble(args[4]) * conversionFactorCoordinates;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(demandFileName, 5, lineNumber, "double", args[4]);
        }
        try {
            demandAtDemandPoint = Integer.parseInt(args[5]);
        }
        catch (NumberFormatException e){
            throw new InputTypeInconsistencyException(demandFileName, 6, lineNumber, "int", args[5]);
        }
        DemandPoint demandPoint = new DemandPoint(stopId, shortName, longName, xCoordinate, yCoordinate,
            demandAtDemandPoint);
        demand.add(demandPoint);
    }

    /**
     * Builder object for a demand reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String demandFileName = "";
        private Config config = Config.getDefaultConfig();
        private Collection<DemandPoint> demand;
        private double conversionFactorCoordinates = -1;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         demand file name (dependent on config) - the file to read
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file name from. This will only
         *         happen, if the file name is not given, but queried.
         *     </li>
         *     <li>
         *          demand (empty {@link Collection<DemandPoint>}) - the collection to write the read demand points to.
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
         * Set the file name to read the demand points from
         * @param demandFileName the demand file name
         * @return this object
         */
        public Builder setDemandFileName(String demandFileName) {
            this.demandFileName = demandFileName;
            return this;
        }

        /**
         * Set the collection to add the read demand points to
         * @param demand the collection for the demand points
         * @return this object
         */
        public Builder setDemand(Collection<DemandPoint> demand) {
            this.demand = demand;
            return this;
        }

        public Builder setConversionFactorCoordinates(double conversionFactorCoordinates) {
            this.conversionFactorCoordinates = conversionFactorCoordinates;
            return this;
        }

        /**
         * Set the config to read the filename from, if none is given beforehand and the filename is queried.
         * @param config the config to read from
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new demand reader with the current builder settings.
         * @return the new reader. Use {@link DemandReader#read()} for the reading process.
         */
        public DemandReader build() {
            return new DemandReader(this);
        }
    }
}
