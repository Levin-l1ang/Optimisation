package net.lintim.io;

import net.lintim.exception.*;
import net.lintim.model.Graph;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.model.impl.SimpleMapGraph;
import net.lintim.util.Config;

/**
 * Class to read files of a ptn.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class PTNReader {
    private final Graph<Stop, Link> ptn;
    private final boolean readStops;
    private final String stopFileName;
    private final boolean readLinks;
    private final String linkFileName;
    private final boolean readLoads;
    private final String loadFileName;
    private final boolean readHeadways;
    private final String headwayFileName;
    private final boolean readGeoCoordinates;
    private final String geoCoordinatesFileName;
    private final double conversionFactorLength;
    private final double conversionFactorCoordinates;
    private final boolean directed;

    private PTNReader(Builder builder) {
        ptn = builder.ptn == null ? new SimpleMapGraph<>() : builder.ptn;
        readStops = builder.readStops;
        if (readStops) {
            stopFileName = !"".equals(builder.stopFileName) ? builder.stopFileName : builder.config.getStringValue
                ("default_stops_file");
            conversionFactorCoordinates = builder.conversionFactorCoordinates == null ? builder.config.getDoubleValue
                ("gen_conversion_coordinates") : builder.conversionFactorCoordinates;
        }
        else {
            stopFileName = "";
            conversionFactorCoordinates  = 0;
        }
        readLinks = builder.readLinks;
        if (readLinks) {
            linkFileName = !"".equals(builder.linkFileName) ? builder.linkFileName : builder.config.getStringValue
                ("default_edges_file");
            conversionFactorLength = builder.conversionFactorLength == null ? builder.config.getDoubleValue
                ("gen_conversion_length") : builder.conversionFactorLength;
        }
        else {
            linkFileName = "";
            conversionFactorLength = 0;
        }
        readLoads = builder.readLoads;
        if (readLoads) {
            loadFileName = !"".equals(builder.loadFileName) ? builder.loadFileName : builder.config.getStringValue
                ("default_loads_file");
        }
        else {
            loadFileName = builder.loadFileName;
        }
        readHeadways = builder.readHeadways;
        if (readHeadways) {
            headwayFileName = !"".equals(builder.headwayFileName) ? builder.headwayFileName : builder.config
                .getStringValue("default_headways_file");
        }
        else {
            headwayFileName = "";
        }
        directed = builder.ptnIsDirected == null ? !builder.config.getBooleanValue("ptn_is_undirected") :
            builder.ptnIsDirected;
        readGeoCoordinates = builder.readGeoCoordinates;
        if (readGeoCoordinates) {
            geoCoordinatesFileName = !"".equals(builder.geoCoordinateFileName) ? builder.geoCoordinateFileName :
                builder.config.getStringValue("default_stops_coordinates_file");
        }
        else {
            geoCoordinatesFileName = "";
        }
    }



    /**
     * Read a ptn or parts of a ptn from LinTim input files. For controlling what to read and which files to read, the
     * given {@link Builder} object will be used. See the corresponding documentation for possible
     * configuration options.
     * @return a ptn with the read data
     */
    public Graph<Stop, Link> read() {
        if(readStops){
            CsvReader.readCsv(stopFileName, this::processStopLine);
        }
        if(readLinks) {
            CsvReader.readCsv(linkFileName, this::processLinkLine);
        }
        if(readLoads) {
            CsvReader.readCsv(loadFileName, this::processLoadLine);
        }
        if(readHeadways) {
            CsvReader.readCsv(headwayFileName, this::processHeadwayLine);
        }
        if(readGeoCoordinates) {
            CsvReader.readCsv(geoCoordinatesFileName, this::processGeoCoordinateLine);
        }
        return ptn;
    }

    /**
     * Process the contents of a stop line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 3 or 4 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws GraphNodeIdMultiplyAssignedException if the node cannot be added to the PTN
     */
    private void processStopLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, GraphNodeIdMultiplyAssignedException {
        if (args.length != 5) {
            throw new InputFormatException(stopFileName, args.length, 5);
        }

        int stopId;
        String shortName;
        String longName;
        double xCoordinate;
        double yCoordinate;

        try {
            stopId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(stopFileName, 1, lineNumber, "int", args[0]);
        }
        shortName = args[1];
        longName = args[2];
        try {
            xCoordinate = Double.parseDouble(args[3]) * conversionFactorCoordinates;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(stopFileName, 4, lineNumber, "double", args[3]);
        }
        try {
            yCoordinate = Double.parseDouble(args[4]) * conversionFactorCoordinates;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(stopFileName, 5, lineNumber, "double", args[4]);
        }

        Stop stop = new Stop(stopId, shortName, longName, xCoordinate, yCoordinate);
        boolean stopAdded = ptn.addNode(stop);

        if (!stopAdded) {
            throw new GraphNodeIdMultiplyAssignedException(stopId);
        }
    }

    /**
     * Process the contents of a link line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 6 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws GraphIncidentNodeNotFoundException   if a stop incident to the link is not found
     * @throws GraphEdgeIdMultiplyAssignedException if the link cannot be added to the PTN
     */
    private void processLinkLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, GraphIncidentNodeNotFoundException, GraphEdgeIdMultiplyAssignedException {
        if (args.length != 6) {
            throw new InputFormatException(linkFileName, args.length, 6);
        }

        int linkId;
        int leftStopId;
        int rightStopId;
        double length;
        int lowerBound;
        int upperBound;

        try {
            linkId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(linkFileName, 1, lineNumber, "int", args[0]);
        }
        try {
            leftStopId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(linkFileName, 2, lineNumber, "int", args[1]);
        }
        try {
            rightStopId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(linkFileName, 3, lineNumber, "int", args[2]);
        }
        try {
            length = Double.parseDouble(args[3]) * conversionFactorLength;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(linkFileName, 4, lineNumber, "double", args[3]);
        }
        try {
            lowerBound = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(linkFileName, 5, lineNumber, "int", args[4]);
        }
        try {
            upperBound = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(linkFileName, 6, lineNumber, "int", args[5]);
        }

        Stop leftStop = ptn.getNode(leftStopId);
        if (leftStop == null) {
            throw new GraphIncidentNodeNotFoundException(linkId, leftStopId);
        }
        Stop rightStop = ptn.getNode(rightStopId);
        if (rightStop == null) {
            throw new GraphIncidentNodeNotFoundException(linkId, rightStopId);
        }
        Link link = new Link(linkId, leftStop, rightStop, length, lowerBound,
            upperBound, directed);

        boolean linkAdded = ptn.addEdge(link);

        if (!linkAdded) {
            throw new GraphEdgeIdMultiplyAssignedException(linkId);
        }

    }

    /**
     * Process the contents of a load line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if the link to the link id cannot be found
     */
    private void processLoadLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIndexNotFoundException {
        if (args.length != 4) {
            throw new InputFormatException(loadFileName, args.length, 4);
        }

        int linkId;
        double load;
        int lowerFreqBound;
        int upperFreqBound;

        try {
            linkId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(loadFileName, 1, lineNumber, "int", args[0]);
        }
        try {
            load = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(loadFileName, 2, lineNumber, "double", args[1]);
        }
        try {
            lowerFreqBound = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(loadFileName, 3, lineNumber, "int", args[2]);
        }
        try {
            upperFreqBound = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(loadFileName, 4, lineNumber, "int", args[3]);
        }

        Link link = ptn.getEdge(linkId);
        if (link == null) {
            throw new DataIndexNotFoundException("Link", linkId);
        }

        link.setLoad(load);
        link.setLowerFrequencyBound(lowerFreqBound);
        link.setUpperFrequencyBound(upperFreqBound);
    }

    private void processHeadwayLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIndexNotFoundException {
        if (args.length != 2) {
            throw new InputFormatException(headwayFileName, args.length, 2);
        }

        int linkId;
        int headway;

        try {
            linkId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(loadFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            headway = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(loadFileName, 2, lineNumber, "int", args[1]);
        }

        Link link = ptn.getEdge(linkId);
        if (link == null) {
            throw new DataIndexNotFoundException("Link", linkId);
        }

        link.setHeadway(headway);
    }

    private void processGeoCoordinateLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIndexNotFoundException {
        if (args.length != 3) {
            throw new InputFormatException(geoCoordinatesFileName, args.length, 3);
        }

        int stopId;
        double longitude;
        double latitude;

        try {
            stopId = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(geoCoordinatesFileName, 1, lineNumber, "int", args[0]);
        }

        try {
            latitude = Double.parseDouble(args[1]);
        }
        catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(geoCoordinatesFileName, 2, lineNumber, "double", args[1]);
        }

        try {
            longitude = Double.parseDouble(args[2]);
        }
        catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(geoCoordinatesFileName, 3, lineNumber, "double", args[2]);
        }

        Stop stop = ptn.getNode(stopId);
        if (stop == null) {
            throw new DataIndexNotFoundException("Stop", stopId);
        }

        stop.setLongitude(longitude);
        stop.setLatitude(latitude);
    }

    /**
     * Builder object for a ptn reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private boolean readStops = true;
        private boolean readLinks = true;
        private boolean readLoads = false;
        private boolean readHeadways = false;
        private boolean readGeoCoordinates = false;
        private Double conversionFactorLength;
        private Double conversionFactorCoordinates;
        private Config config = Config.getDefaultConfig();
        private String stopFileName = "";
        private String linkFileName = "";
        private String loadFileName = "";
        private String headwayFileName = "";
        private String geoCoordinateFileName = "";
        private Boolean ptnIsDirected;
        private Graph<Stop, Link> ptn;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         read stops (true) - whether to read stops
         *     </li>
         *     <li>
         *         read links (true) - whether to read links
         *     </li>
         *     <li>
         *         read loads (false) - whether to read loads
         *     </li>
         *     <li>
         *         read headways (false) - whether to read headways
         *     </li>
         *     <li>
         *         read geo coordinates (false) - read geo coordinates as well as the coordinates in the stop file
         *     </li>
         *     <li>
         *         config ({@link Config#getDefaultConfig()}) - the config to read the file names and conversion factors
         *         from. This will only happen, if the file names or conversion factors are not given, but queried.
         *     </li>
         *     <li>
         *         stop file name (dependent on config) - the file name to read the stops from
         *     </li>
         *     <li>
         *         link file name (dependent on config) - the file name to read the links from
         *     </li>
         *     <li>
         *         load file name (dependent on config) - the file name to read the loads from
         *     </li>
         *     <li>
         *         headway file name (dependent on config) - the file name to read the headways from
         *     </li>
         *     <li>
         *         geo coordinate file name (dependent on config) - the file name to read the geo coordinates from
         *     </li>
         *     <li>
         *         ptn (Empty {@link SimpleMapGraph}) - the ptn to store the read objects in.
         *     </li>
         *     <li>
         *         ptn is directed (dependent on config) - whether the ptn is directed. This will control whether read
         *         links are created directed or undirected.
         *     </li>
         *     <li>
         *         conversion factor length (dependent on config) - the factor to convert edge lengths in
         *         kilometers
         *     </li>
         *     <li>
         *         conversion factor coordinates (dependent on config) - the factor to convert coordinates so we can
         *         compute an euclidean distance in kilometers.
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder() {
        }

        /**
         * Set whether to read the stops
         * @param readStops whether to read the stops
         * @return this object
         */
        public Builder readStops(boolean readStops) {
            this.readStops = readStops;
            return this;
        }

        /**
         * Set whether to read the links
         * @param readLinks whether to read the links
         * @return this builder
         */
        public Builder readLinks(boolean readLinks) {
            this.readLinks = readLinks;
            return this;
        }

        /**
         * Set whether to read the loads
         * @param readLoads whether to read the loads
         * @return this object
         */
        public Builder readLoads(boolean readLoads) {
            this.readLoads = readLoads;
            return this;
        }

        /**
         * Set whether to read the headways
         * @param readHeadways whether to read the headways
         * @return this object
         */
        public Builder readHeadways(boolean readHeadways) {
            this.readHeadways = readHeadways;
            return this;
        }

        /**
         * Set whether to read the geo coordinates
         * @param readGeoCoordinates whether to read the geo coordinates
         * @return this object
         */
        public Builder readGeoCoordinates(boolean readGeoCoordinates) {
            this.readGeoCoordinates = readGeoCoordinates;
            return this;
        }

        /**
         * Set the config. The config is used to read file names or coordinate factors that are queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set the file name to read the stops from
         * @param stopFileName the stop file name
         * @return this object
         */
        public Builder setStopFileName(String stopFileName) {
            this.stopFileName = stopFileName;
            return this;
        }

        /**
         * Set the file name to read the links from
         * @param linkFileName the link file name
         * @return this object
         */
        public Builder setLinkFileName(String linkFileName) {
            this.linkFileName = linkFileName;
            return this;
        }

        /**
         * Set the file name to read the loads from
         * @param loadFileName the load file name
         * @return this object
         */
        public Builder setLoadFileName(String loadFileName) {
            this.loadFileName = loadFileName;
            return this;
        }

        /**
         * Set the file name to read the headways from
         * @param headwayFileName the headway file name
         * @return this object
         */
        public Builder setHeadwayFileName(String headwayFileName) {
            this.headwayFileName = headwayFileName;
            return this;
        }

        /**
         * Set the file name to read the geo coordinates from
         * @param geoCoordinateFileName the geo coordinates file name
         * @return this object
         */
        public Builder setGeoCoordinateFileName(String geoCoordinateFileName) {
            this.geoCoordinateFileName = geoCoordinateFileName;
            return this;
        }

        /**
         * Set the conversion factor for the coordinates. Is used to convert the coordinates to enable computing
         * the euclidean distance in kilometers.
         * @param conversionFactorCoordinates the conversion factor for the coordinates
         * @return this object
         */
        public Builder setConversionFactorCoordinates(Double conversionFactorCoordinates) {
            this.conversionFactorCoordinates = conversionFactorCoordinates;
            return this;
        }

        /**
         * Set the conversion factor for the lengths. Is used to convert the lengths of links into kilometers.
         * @param conversionFactorLength the conversion factor for lengths
         * @return this object
         */
        public Builder setConversionFactorLength(Double conversionFactorLength) {
            this.conversionFactorLength = conversionFactorLength;
            return this;
        }

        /**
         * Set whether the ptn is directed. This will control, whether read links are created directed or undirected.
         * @param ptnIsDirected whether the ptn is directed
         * @return this object
         */
        public Builder setPtnIsDirected(boolean ptnIsDirected) {
            this.ptnIsDirected = ptnIsDirected;
            return this;
        }

        /**
         * Set the ptn to store the read objects in.
         * @param ptn the ptn
         * @return this object
         */
        public Builder setPtn(Graph<Stop, Link> ptn) {
            this.ptn = ptn;
            return this;
        }

        /**
         * Create a new ptn reader with the current builder settings
         * @return the new reader. Use {@link #read()} for the reading process.
         */
        public PTNReader build() {
            return new PTNReader(this);
        }
    }
}
