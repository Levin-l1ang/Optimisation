package net.lintim.io;

import net.lintim.exception.*;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.util.List;


/**
 * Class to read files of a ride pool or ridepooling concept.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class RidepoolingPoolReader {
    private static final Logger logger = new Logger(RidepoolingPoolReader.class);

    private final String ridepoolFileName;
    private final String ridepoolDistributionFileName;
    private final String ridepoolStretchFileName;
    private final RidepoolingPool ridepoolingPool;
    private final Graph<Stop, Link> ptn;
    private final boolean readAreas;
    private final boolean readNumberVehicles;
    private final boolean readDistribution;
    private final boolean readStretchFactors;

    public RidepoolingPoolReader(Builder builder) {
        if (!builder.readAreas && builder.readNumberVehicles) {
            logger.warn("Cannot read number of vehicles but no areas, will read areas as well!");
            this.readAreas = true;
        } else{
            this.readAreas = builder.readAreas;
        }
        this.readNumberVehicles = builder.readNumberVehicles;
        this.readStretchFactors = builder.readStretchFactors;
        this.readDistribution = builder.readDistribution;

        if (readAreas && builder.ridepoolFileName.isEmpty()) {
            if (readNumberVehicles) {
                this.ridepoolFileName = builder.config.getStringValue("filename_rc_file");
            } else {
                this.ridepoolFileName = builder.config.getStringValue("filename_rpool_file");
            }
        } else {
            this.ridepoolFileName = builder.ridepoolFileName;
        }

        if (readDistribution && builder.ridepoolDistributionFileName.isEmpty()) {
            this.ridepoolDistributionFileName = builder.config.getStringValue("filename_rpool_vehicle_frequencies_file");
        } else {
            this.ridepoolDistributionFileName = builder.ridepoolDistributionFileName;
        }

        if (readStretchFactors && builder.ridepoolStretchFileName.isEmpty()) {
            this.ridepoolStretchFileName = builder.config.getStringValue("filename_rpool_stretch_factors_file");
        } else {
            this.ridepoolStretchFileName = builder.ridepoolStretchFileName;
        }

        this.ridepoolingPool = builder.ridepoolingPool == null ? new RidepoolingPool(builder.config.getDoubleValue("rpool_costs_fixed")) : builder.ridepoolingPool;
        this.ptn = builder.ptn;
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read ride pool
     */
    public RidepoolingPool read() {
        if (readAreas) {
            CsvReader.readCsv(ridepoolFileName, this::processRidepoolingPoolLine);
        }

        int areaId = ridepoolingPool.testConnected();
        if (areaId != -1) {
            throw new DataRidepoolingAreaNotConnectedException(areaId);
        }

        if (readDistribution) {
            CsvReader.readCsv(ridepoolDistributionFileName, this::processRidepoolingPoolDistributionLine);
        }

        if (readStretchFactors) {
            CsvReader.readCsv(ridepoolStretchFileName, this::processRidepoolingPoolStretchLine);
        }

        return ridepoolingPool;
    }

    /**
     * Process the contents of a ride pool or ridepooling concept line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 3 or 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if an area or link can not be found by their index
     */
    public void processRidepoolingPoolLine(String[] args, int lineNumber) throws InputFormatException, InputTypeInconsistencyException, DataIndexNotFoundException {
        if (!readNumberVehicles && args.length != 2) {
            throw new InputFormatException(ridepoolFileName, args.length, 2);
        } else if (readNumberVehicles && args.length != 3) {
            throw new InputFormatException(ridepoolFileName, args.length, 3);
        }

        int areaId;
        int linkId;
        int nbVehicles;

        try {
            areaId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(ridepoolFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            linkId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(ridepoolFileName, 2, lineNumber, "int", args[1]);
        }

        if (readNumberVehicles) {
            try {
                nbVehicles = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                throw new InputTypeInconsistencyException(ridepoolFileName, 3, lineNumber, "int", args[2]);
            }
        } else {
            nbVehicles = 0;
        }

        RidepoolingArea area;
        area = ridepoolingPool.getArea(areaId);
        if (area == null) {
            area = new RidepoolingArea(areaId);
            ridepoolingPool.addArea(area);
            area.setNumberOfVehicles(nbVehicles);
        }

        Link link = ptn.getEdge(linkId);
        if (link == null) {
            throw new DataIndexNotFoundException("Link", linkId);
        }

        area.addLink(link);
        //ridepoolingPool.addArea(area);
    }

    /**
     * Process the contents of a ridepooling vehicle frequency file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 3 or 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if an area or link can not be found by their index
     */
    public void processRidepoolingPoolDistributionLine(String[] args, int lineNumber) throws InputFormatException, InputTypeInconsistencyException, DataIndexNotFoundException {
        int areaId;
        int linkId;
        double distribution;

        try {
            areaId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(ridepoolDistributionFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            linkId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(ridepoolDistributionFileName, 2, lineNumber, "int", args[1]);
        }

        try {
            distribution = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(ridepoolDistributionFileName, 3, lineNumber, "float", args[2]);
        }

        boolean success = ridepoolingPool.getArea(areaId).setDistribution(linkId, distribution);
        if (!success) {
            if (ridepoolingPool.getArea(areaId) == null) {
                throw new DataIndexNotFoundException("area", areaId);
            } else {
                throw new DataIndexNotFoundException("Link with index " + linkId + " in area", areaId);
            }
        }
    }

    /**
     * Process the contents of a ride pool stretch factors file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 3 or 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if an area or link can not be found by their index
     */
    public void processRidepoolingPoolStretchLine(String[] args, int lineNumber) throws InputFormatException, InputTypeInconsistencyException, DataIndexNotFoundException {
        int areaId;
        int linkId;
        double stretchFactor;

        try {
            areaId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(ridepoolStretchFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            linkId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(ridepoolStretchFileName, 2, lineNumber, "int", args[1]);
        }

        try {
            stretchFactor = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(ridepoolStretchFileName, 3, lineNumber, "float", args[2]);
        }

        boolean success = ridepoolingPool.getArea(areaId).setStretchFactor(linkId, stretchFactor);
        if (!success) {
            if (ridepoolingPool.getArea(areaId) == null) {
                throw new DataIndexNotFoundException("area", areaId);
            } else {
                throw new DataIndexNotFoundException("Link with index " + linkId + " in area", areaId);
            }
        }
    }


    /**
     * Builder object for a ridepoolingPool reader.
     *
     * Use {@link #Builder(Graph)} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(Graph)}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private boolean readAreas = true;
        private boolean readNumberVehicles = true;
        private boolean readDistribution = false;
        private boolean readStretchFactors = false;
        private String ridepoolFileName = "";
        private String ridepoolDistributionFileName = "";
        private String ridepoolStretchFileName = "";
        private RidepoolingPool ridepoolingPool;
        private final Graph<Stop, Link> ptn;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *          read areas (true) - whether to read the areas. Needs to be true if number of vehicles should be read as
         *          well.
         *     </li>
         *     <li>
         *          read number of vehicles (true) - Whether to read the number of vehicles of each area, i.e. whether
         *          to read a ride pool or ridepooling concept
         *     </li>
         *     <li>
         *          read distribution (false) - whether to read the vehicle frequencies from the ridepoolDistributionFile
         *     </li>
         *     <li>
         *          read stretch factors (false) - whether to read stretch factors for the length of every edge in every area from
         *          the ridepoolStretchFile
         *     </li>
         *     <li>
         *          ridepoolingPool file name (dependent on config) - the file name to read the ridepoolingPool from.
         *     </li>
         *     <li>
         *          ridepoolingPool distribution file name (dependent on config) - the file name to read the distribuion values from
         *     </li>
         *     <li>
         *          ridepoolingPool stretch file name (dependent on config) - the file name to read the stretch factors from
         *     </li>
         *     <li>
         *          ridepoolingPool (empty ride pool) - the pool to add the read areas to. If only distributions or stretch factors should be
         *          read, the corresponding areas have to be present in the ride pool.
         *     </li>
         *     <li>
         *          ptn (set in constructor) - the base ptn. Needs to contain all links that should be read into areas.
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()} - the config to read the file names from. This will only
         *          happen if the file names are not given but queried
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         * @param ptn the base ptn. Needs to contain all links that should be read into lines.
         */
        public Builder(Graph<Stop, Link> ptn) {
            this.ptn = ptn;
        }

        /**
         * Set whether to read the areas. Will be set to true if number of vehicles should be read as well.
         * @param readLines whether to read lines
         * @return this object
         */
        public Builder readAreas(boolean readAreas) {
            this.readAreas = readAreas;
            return this;
        }

        /**
         * Set whether to read the vehicle frequency. It is possible to read only them without the areas, but then all areas need to
         * be already contained in the provided ride pool.
         * @param readDistribution whether to read distribution
         * @return this object
         */
        public Builder readDistribution(boolean readDistribution) {
            this.readDistribution = readDistribution;
            return this;
        }

        /**
         * Set whether to read the number of vehicles assigned to each zone.
         * @param readNumberVehicles whether to read the number of vehicles
         * @return this object
         */
        public Builder readNumberVehicles(boolean readNumberVehicles) {
            this.readNumberVehicles = readNumberVehicles;
            return this;
        }

        /**
         * Set whether to read the stretch factors. It is possible to read only them without the areas, but then all areas need to
         * be already contained in the provided ride pool.
         * @param readStretch whether to read Stretch
         * @return this object
         */
        public Builder readStretch(boolean readStretch) {
            this.readStretchFactors = readStretch;
            return this;
        }

        /**
         * Set the file name to read the ridepoolingPool from.
         * @param ridepoolFileName the file name to read the ridepoolingPool from
         * @return this object
         */
        public Builder setRidepoolFileName(String ridepoolFileName) {
            this.ridepoolFileName = ridepoolFileName;
            return this;
        }

        /**
         * Set the file name to read the distribution values from.
         * @param ridepoolDistributionFileName the file name to read the costs from
         * @return this object
         */
        public Builder setRidepoolDistributionFileName(String ridepoolDistributionFileName) {
            this.ridepoolDistributionFileName = ridepoolDistributionFileName;
            return this;
        }

        /**
         * Set the file name to read the stretch factors from.
         * @param ridepoolStretchFileName the file name to read the stretch factors from
         * @return this object
         */
        public Builder setRidepoolStretchFileName(String ridepoolStretchFileName) {
            this.ridepoolStretchFileName = ridepoolStretchFileName;
            return this;
        }

        /**
         * Set the ride pool to add the read areas to. If only distribution or stretch should be read, the corresponding areas
         * have to be present in the ride pool.
         * @param ridepoolingPool the ride pool to add the areas to.
         * @return this object
         */
        public Builder setRidepoolingPool(RidepoolingPool ridepoolingPool) {
            this.ridepoolingPool = ridepoolingPool;
            return this;
        }

        /**
         * Set the config to read the file names from. This will only happen if the file names are not given but queried.
         * @param config the config to read the file names from
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new ridepoolingPool reader with the current builder settings.
         * @return the new reader. Use {@link LineReader#read()} for the reading process.
         */
        public RidepoolingPoolReader build() {
            return new RidepoolingPoolReader(this);
        }
    }
}
