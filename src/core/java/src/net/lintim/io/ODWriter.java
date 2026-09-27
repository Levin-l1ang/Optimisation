package net.lintim.io;

import net.lintim.model.*;
import net.lintim.util.Config;

import java.util.LinkedList;

/**
 * Class to write files of an od matrix.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class ODWriter {
    private final OD od;
    private final Graph<Stop, Link> ptn;
    private final String fileName;
    private final String header;

    private ODWriter(Builder builder) {
        this.od = builder.od;
        this.ptn = builder.ptn;
        this.fileName = "".equals(builder.fileName) ? builder.config.getStringValue("default_od_file") :
            builder.fileName;
        this.header = "".equals(builder.header) ? builder.config.getStringValue("od_header") : builder.header;
    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     */
    public void write() {
        LinkedList<ODPair> odPairs = new LinkedList<>();
        for(Stop origin : ptn.getNodes()){
            for(Stop destination : ptn.getNodes()){
                odPairs.add(new ODPair(origin.getId(), destination.getId(), od.getValue(origin.getId(), destination
                    .getId())));
            }
        }
        CsvWriter.writeList(
            fileName,
            header,
            odPairs,
            ODPair::toCsvStrings
        );
    }

    /**
     * Builder object for a od writer.
     *
     * Use {@link #Builder(OD, Graph)} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(OD, Graph)}. To create a writer object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private final OD od;
        private final Graph<Stop, Link> ptn;
        private String fileName = "";
        private String header = "";
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         od (given in constructor) - the od matrix to write. Can only be set in the constructor.
         *     </li>
         *     <li>
         *         ptn (given in constructor) - the base ptn. Can only be set in the constructor.
         *     </li>
         *     <li>
         *         config ({@link Config#getDefaultConfig()}) - the config to read the file name and header from. This
         *         will only happen, if the file name or the header are not given, but queried.
         *     </li>
         *     <li>
         *          file name (dependent on config) - the file name to write the od matrix to
         *     </li>
         *     <li>
         *          header (dependent on config) - the header to use for the od file
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a writer with the given parameters.
         * @param od the od matrix to write
         * @param ptn the base ptn
         */
        public Builder(OD od, Graph<Stop, Link> ptn) {
            if (od == null || ptn == null) {
                throw new IllegalArgumentException("od and ptn can not be null in od writer builder");
            }
            this.od = od;
            this.ptn = ptn;
        }

        /**
         * Set the od file name
         * @param fileName the file name to write the od matrix to
         * @return this object
         */
        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Set the config. The config is used to read the file name or header, that are queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set the header for the od file
         * @param header the od header
         * @return this object
         */
        public Builder setHeader(String header) {
            this.header = header;
            return this;
        }

        /**
         * Create a new od writer with the current builder settings
         * @return the new writer. Use {@link #write()} for the writing process.
         */
        public ODWriter build() {
            return new ODWriter(this);
        }
    }
}
