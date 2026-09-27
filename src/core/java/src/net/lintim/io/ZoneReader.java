package net.lintim.io;

import net.lintim.exception.*;
import net.lintim.model.Graph;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.model.Zone;
import net.lintim.model.impl.SimpleMapGraph;
import net.lintim.util.Config;
import java.util.*;

/**
 * Class to read a zone file.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class ZoneReader {
    private final Graph<Stop, Link> ptn;
    private final String zoneFileName;
    private Map<Integer, Zone> zones;

    private ZoneReader(Builder builder) {
        ptn = builder.ptn == null ? new SimpleMapGraph<>() : builder.ptn;
        zoneFileName = !"".equals(builder.zoneFileName) ? builder.zoneFileName : builder.config.getStringValue("filename_tariff_zone_file");
        zones = new HashMap<>();
    }


    /**
     * Read a ptn or parts of a ptn from LinTim input files. For controlling what to read and which files to read, the
     * given {@link Builder} object will be used. See the corresponding documentation for possible
     * configuration options.
     * @return a Map of the read zones indexed by ids.
     */
    public Map<Integer, Zone> read() {
        CsvReader.readCsv(zoneFileName, this::processZoneLine);
        return zones;
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
    private void processZoneLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, GraphNodeIdMultiplyAssignedException {
        if (args.length != 2) {
            throw new InputFormatException(zoneFileName, args.length, 2);
        }

        int zoneId;
        int stopId;

        try {
            zoneId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(zoneFileName, 1, lineNumber, "int", args[0]);
        }
        try {
            stopId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(zoneFileName, 2, lineNumber, "int", args[0]);
        }

        Stop stop = ptn.getNode(stopId);
        if (zones.containsKey(zoneId)) {
            zones.get(zoneId).addStop(stop);
        }
        else {
            HashSet<Stop> stops = new HashSet<>();
            stops.add(stop);
            Zone zone = new Zone(zoneId, stops);
            zones.put(zoneId, zone);
        }

    }

    
    /**
     * Builder object for a zone reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private Config config = Config.getDefaultConfig();
        private String zoneFileName = Config.getStringValueStatic("filename_tariff_zone_file");
        private Graph<Stop, Link> ptn;

        /**
         * Create a default builder object.
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder() {
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
         * Set the file name to read the zones from
         * @param zoneFileName the zone file name
         * @return this object
         */
        public Builder setZoneFileName(String zoneFileName) {
            this.zoneFileName = zoneFileName;
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
         * Create a new zone reader with the current builder settings
         * @return the new reader. Use {@link #read()} for the reading process.
         */
        public ZoneReader build() {
            return new ZoneReader(this);
        }
    }
}
