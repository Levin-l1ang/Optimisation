package net.lintim.io;

import net.lintim.model.Graph;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.util.Config;

import java.util.Comparator;
import java.util.LinkedList;

/**
 * Class to write files of a ptn.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class PTNWriter {
    private final boolean writeStops;
    private final boolean writeLinks;
    private final boolean writeLoads;
    private final boolean writeHeadways;
    private final String stopFileName;
    private final String linkFileName;
    private final String loadFileName;
    private final String headwayFileName;
    private final String stopHeader;
    private final String linkHeader;
    private final String loadHeader;
    private final String headwayHeader;
    private final double conversionFactorLength;
    private final double conversionFactorCoordinates;
    private final Graph<Stop, Link> ptn;

    private PTNWriter(Builder builder) {
        this.ptn = builder.ptn;
        this.writeStops = builder.writeStops;
        this.writeLinks = builder.writeLinks;
        this.writeLoads = builder.writeLoads;
        this.writeHeadways = builder.writeHeadways;
        if (writeStops) {
            this.stopFileName = "".equals(builder.stopFileName) ?
                builder.config.getStringValue("default_stops_file") : builder.stopFileName;
            this.stopHeader = "".equals(builder.stopHeader) ? builder.config.getStringValue("stops_header") :
                builder.stopHeader;
            this.conversionFactorCoordinates = builder.conversionFactorCoordinates == -1 ?
                builder.config.getDoubleValue("gen_conversion_coordinates") : builder.conversionFactorCoordinates;
        }
        else {
            this.stopFileName = "";
            this.stopHeader = "";
            this.conversionFactorCoordinates = 1;
        }
        if (writeLinks) {
            this.linkFileName = "".equals(builder.linkFileName) ?
                builder.config.getStringValue("default_edges_file") : builder.linkFileName;
            this.linkHeader = "".equals(builder.linkHeader) ?
                builder.config.getStringValue("edges_header") : builder.linkHeader;
            this.conversionFactorLength = builder.conversionFactorLength == -1 ?
                builder.config.getDoubleValue("gen_conversion_length") : builder.conversionFactorLength;
        }
        else {
            this.linkFileName = "";
            this.linkHeader = "";
            this.conversionFactorLength = 1;
        }
        if (writeLoads) {
            this.loadFileName = "".equals(builder.loadFileName) ?
                builder.config.getStringValue("default_loads_file") : builder.loadFileName;
            this.loadHeader = "".equals(builder.loadHeader) ?
                builder.config.getStringValue("loads_header") : builder.loadHeader;
        }
        else {
            this.loadFileName = "";
            this.loadHeader = "";
        }
        if (writeHeadways) {
            this.headwayFileName = "".equals(builder.headwayFileName) ?
                builder.config.getStringValue("default_headways_file") : builder.headwayFileName;
            this.headwayHeader = "".equals(builder.headwayHeader) ?
                builder.config.getStringValue("headways_header") : builder.headwayHeader;
        }
        else {
            this.headwayFileName = "";
            this.headwayHeader = "";
        }
    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object this object was created with.
     */
    public void write() {
        if(writeStops) {
            CsvWriter.writeCollection(
                stopFileName,
                stopHeader,
                ptn.getNodes(),
                s -> s.toCsvStrings(conversionFactorCoordinates),
                Comparator.comparingInt(Stop::getId)
            );
        }

        LinkedList<Link> links = null;
        if(writeLinks || writeLoads || writeHeadways) {
            //Sort the links beforehand, we may need to write it three times
            links = new LinkedList<>(ptn.getEdges());
            links.sort(Comparator.comparingInt(Link::getId));
        }

        if(writeLinks) {
            CsvWriter.writeList(
                linkFileName,
                linkHeader,
                links,
                l -> l.toCsvStrings(conversionFactorLength)
            );
        }

        if (writeLoads) {
            CsvWriter.writeList(
                loadFileName,
                loadHeader,
                links,
                Link::toCsvLoadStrings
            );
        }

        if (writeHeadways) {
            CsvWriter.writeList(
                headwayFileName,
                headwayHeader,
                links,
                Link::toCsvHeadwayStrings
            );
        }
    }

    /**
     * Builder object for a ptn writer.
     *
     * Use {@link #Builder(Graph)} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(Graph)}. To create a writer object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private boolean writeStops = true;
        private boolean writeLinks = true;
        private boolean writeLoads = false;
        private boolean writeHeadways = false;
        private String stopFileName = "";
        private String linkFileName = "";
        private String loadFileName = "";
        private String headwayFileName = "";
        private String stopHeader = "";
        private String linkHeader = "";
        private String loadHeader = "";
        private String headwayHeader = "";
        private double conversionFactorLength = -1;
        private double conversionFactorCoordinates = -1;
        private final Graph<Stop, Link> ptn;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         write stops (true) - whether to write the stops
         *     </li>
         *     <li>
         *         write links (true) - whether to write the links
         *     </li>
         *     <li>
         *         write loads (false) - whether to write the loads
         *     </li>
         *     <li>
         *         write headways (false) - whether to write the headways
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file names and headers from.
         *          This will only happen, if the file names or headers are not given, but queried.
         *     </li>
         *     <li>
         *          stop file name (dependent on config) - the file name to write the stops to
         *     </li>
         *     <li>
         *          link file name (dependent on config) - the file name to write the links to
         *     </li>
         *     <li>
         *          load file name (dependent on config) - the file name to write the loads to
         *     </li>
         *     <li>
         *          headway file name (dependent on config) - the file name to write the headways to
         *     </li>
         *     <li>
         *          stop header (dependent on config) - the header to use for the stop file
         *     </li>
         *     <li>
         *          link header (dependent on config) - the header to use for the link file
         *     </li>
         *     <li>
         *          load header (dependent on config) - the header to use for the load file
         *     </li>
         *     <li>
         *          headway header (dependent on config) - the header to use for the headway file
         *     </li>
         *     <li>
         *         conversionFactorLength (dependent on config) - the factor to converse edge lengths to km
         *     </li>
         *     <li>
         *         conversionFactorCoordinates (dependent on config) - the factor to converse the stop coordinates
         *     </li>
         *     <li>
         *          ptn (given in constructor) - the ptn to write. Can only be set in the constructor
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a writer with the given parameters.
         * @param ptn the ptn to write
         */
        public Builder(Graph<Stop, Link> ptn) {
            if (ptn == null) {
                throw new IllegalArgumentException("ptn can not be null in ptn writer builder");
            }
            this.ptn = ptn;
        }

        /**
         * Set whether to write the stops
         * @param writeStops whether to write the stops
         * @return this object
         */
        public Builder writeStops(boolean writeStops) {
            this.writeStops = writeStops;
            return this;
        }

        /**
         * Set whether to write the links
         * @param writeLinks whether to write the links
         * @return this object
         */
        public Builder writeLinks(boolean writeLinks) {
            this.writeLinks = writeLinks;
            return this;
        }

        /**
         * Set whether to write the loads
         * @param writeLoads whether to write the loads
         * @return this object
         */
        public Builder writeLoads(boolean writeLoads) {
            this.writeLoads = writeLoads;
            return this;
        }

        /**
         * Set whether to write the headways
         * @param writeHeadways whether to write the headways
         * @return this object
         */
        public Builder writeHeadways(boolean writeHeadways) {
            this.writeHeadways = writeHeadways;
            return this;
        }

        /**
         * Set the stop file name
         * @param stopFileName the file to write the stops to
         * @return this object
         */
        public Builder setStopFileName(String stopFileName) {
            this.stopFileName = stopFileName;
            return this;
        }

        /**
         * Set the link file name
         * @param linkFileName the file to write the links to
         * @return this object
         */
        public Builder setLinkFileName(String linkFileName) {
            this.linkFileName = linkFileName;
            return this;
        }

        /**
         * Set the load file name
         * @param loadFileName the file to write the loads to
         * @return this object
         */
        public Builder setLoadFileName(String loadFileName) {
            this.loadFileName = loadFileName;
            return this;
        }

        /**
         * Set the headway file name
         * @param headwayFileName the file to write the headways to
         * @return this object
         */
        public Builder setHeadwayFileName(String headwayFileName) {
            this.headwayFileName = headwayFileName;
            return this;
        }

        /**
         * Set the header for the stop file
         * @param stopHeader the stop header
         * @return this object
         */
        public Builder setStopHeader(String stopHeader) {
            this.stopHeader = stopHeader;
            return this;
        }

        /**
         * Set the header for the link file
         * @param linkHeader the link header
         * @return this object
         */
        public Builder setLinkHeader(String linkHeader) {
            this.linkHeader = linkHeader;
            return this;
        }

        /**
         * Set the header for the load file
         * @param loadHeader the load header
         * @return this object
         */
        public Builder setLoadHeader(String loadHeader) {
            this.loadHeader = loadHeader;
            return this;
        }

        /**
         * Set the header for the headway file
         * @param headwayHeader the headway header
         * @return this object
         */
        public Builder setHeadwayHeader(String headwayHeader) {
            this.headwayHeader = headwayHeader;
            return this;
        }

        public Builder setConversionFactorLength(double conversionFactorLength) {
            this.conversionFactorLength = conversionFactorLength;
            return this;
        }

        public Builder setConversionFactorCoordinates(double conversionFactorCoordinates) {
            this.conversionFactorCoordinates = conversionFactorCoordinates;
            return this;
        }

        /**
         * Set the config. The config is used to read file names or headers, that are queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new ptn writer with the current builder settings
         * @return the new writer. Use {@link #write()} for the writing process.
         */
        public PTNWriter build() {
            return new PTNWriter(this);
        }
    }
}
