package net.lintim.io;

import net.lintim.exception.OutputFileException;
import net.lintim.model.Line;
import net.lintim.model.LinePool;
import net.lintim.model.Link;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;

/**
 * Class to write files of lines.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class LineWriter {
    private static final Logger logger = new Logger(LineWriter.class);

    private final LinePool linePool;
    private final boolean writePool;
    private final boolean writeCosts;
    private final boolean writeLineConcept;
    private final String poolFileName;
    private final String poolCostFileName;
    private final String conceptFileName;
    private final String poolHeader;
    private final String poolCostHeader;
    private final String conceptHeader;
    private final double conversionFactorLength;

    private LineWriter(Builder builder) {
        this.linePool = builder.linePool;
        this.writePool = builder.writePool;
        this.writeCosts = builder.writeCosts;
        this.writeLineConcept = builder.writeLineConcept;
        if (this.writePool) {
            this.poolFileName = "".equals(builder.poolFileName) ?
                builder.config.getStringValue("default_pool_file") : builder.poolFileName;
            this.poolHeader = "".equals(builder.poolHeader) ? builder.config.getStringValue("lpool_header") :
                builder.poolHeader;
        }
        else {
            this.poolFileName = "";
            this.poolHeader = "";
        }
        if (this.writeCosts) {
            this.poolCostFileName = "".equals(builder.poolCostFileName) ?
                builder.config.getStringValue("default_pool_cost_file") : builder.poolCostFileName;
            this.poolCostHeader = "".equals(builder.poolCostHeader) ?
                builder.config.getStringValue("lpool_cost_header") : builder.poolCostHeader;
            conversionFactorLength = builder.conversionFactorLength == null ? builder.config.getDoubleValue
                ("gen_conversion_length") : builder.conversionFactorLength;
        }
        else {
            this.poolCostFileName = "";
            this.poolCostHeader = "";
            this.conversionFactorLength = 0;
        }
        if (this.writeLineConcept) {
            this.conceptFileName = "".equals(builder.conceptFileName) ?
                builder.config.getStringValue("default_lines_file") : builder.conceptFileName;
            this.conceptHeader = "".equals(builder.conceptHeader) ?
                builder.config.getStringValue("lines_header") : builder.conceptHeader;
        }
        else {
            this.conceptFileName = "";
            this.conceptHeader = "";
        }
    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     */
    public void write() {
        LinkedList<Line> lines = new LinkedList<>(linePool.getLines());
        lines.sort(Comparator.comparingInt(Line::getId));
        if (writePool) {
            try {
                CsvWriter poolWriter = new CsvWriter(poolFileName, poolHeader);
                for (Line line : lines) {
                    writeLinePoolLine(line, poolWriter);
                }
                poolWriter.close();
            }
            catch (IOException e) {
                throw new OutputFileException(poolFileName);
            }
        }
        if (writeCosts) {
            CsvWriter.writeList(poolCostFileName, poolCostHeader, lines, x -> x.toLineCostCsvStrings(conversionFactorLength));
        }
        if (writeLineConcept) {
            try {
                CsvWriter conceptWriter = new CsvWriter(conceptFileName, conceptHeader);
                for (Line line : lines) {
                    writeLineConceptLine(line, conceptWriter);
                }
                conceptWriter.close();
            }
            catch (IOException e){
                logger.warn("Encountered exception: " + e.toString());
                throw new OutputFileException(conceptFileName);
            }
        }
    }

    private static void writeLinePoolLine(Line line, CsvWriter writer) throws IOException {
        int edgeIndex = 1;
        for(Link link : line.getLinePath().getEdges()){
            writer.writeLine(
                String.valueOf(line.getId()),
                String.valueOf(edgeIndex),
                String.valueOf(link.getId())
            );
            edgeIndex++;
        }
    }

    private static void writeLineConceptLine(Line line, CsvWriter writer) throws IOException {
        int edgeIndex = 1;
        for(Link link : line.getLinePath().getEdges()){
            writer.writeLine(
                String.valueOf(line.getId()),
                String.valueOf(edgeIndex),
                String.valueOf(link.getId()),
                String.valueOf(line.getFrequency())
            );
            edgeIndex++;
        }
    }

    /**
     * Builder object for a line writer.
     *
     * Use {@link #Builder(LinePool)} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(LinePool)}. To create a writer object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private final LinePool linePool;
        private boolean writePool = false;
        private boolean writeCosts = false;
        private boolean writeLineConcept = true;
        private String poolFileName = "";
        private String poolCostFileName = "";
        private String conceptFileName = "";
        private String poolHeader = "";
        private String poolCostHeader = "";
        private String conceptHeader = "";
        private Double conversionFactorLength;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         write line concept (true) - whether to write the line concept, i.e., the lines with the frequencies
         *     </li>
         *     <li>
         *         write pool (false) - whether to write the line pool, i.e., the lines without the frequencies
         *     </li>
         *     <li>
         *         write costs (false) - whether to write the line costs
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file names and headers from. This
         *          will only happen, if the file names are not given, but queried.
         *     </li>
         *     <li>
         *          pool file name (dependent on config) - the file name to write the pool to, i.e., the lines without
         *          the frequencies.
         *     </li>
         *     <li>
         *          pool cost file name (dependent on config) - the file name to write the line costs to
         *     </li>
         *     <li>
         *          line concept file name (dependent on config) - the file name to write the line concept to, i.e., the
         *          lines with the frequencies
         *     </li>
         *     <li>
         *          pool header (dependent on config) - the header to use for the line pool file, i.e., the file for the
         *          lines without the frequencies
         *     </li>
         *     <li>
         *          pool cost header (dependent on config) - the header to use for the pool cost file
         *     </li>
         *     <li>
         *          line concept header (dependent on config) - the header to use for the line concept file, i.e., the file
         *          for the lines with the frequencies
         *     </li>
         *     <li>
         *          line pool (given in constructor) - the lines to write. Can only be set in the constructor
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a writer with the given parameters.
         * @param linePool the lines to write
         */
        public Builder(LinePool linePool) {
            if (linePool == null) {
                throw new IllegalArgumentException("The line pool in a line writer builder can not be null!");
            }
            this.linePool = linePool;
        }

        /**
         * Create a new line writer with the current builder settings
         * @return the new writer. Use {@link LineWriter#write()} for the writing process.
         */
        public LineWriter build() {
            return new LineWriter(this);
        }

        /**
         * Set whether to write the line pool, i.e., the lines without the frequencies.
         * @param writePool whether to write the line pool
         * @return this object
         */
        public Builder writePool(boolean writePool) {
            this.writePool = writePool;
            return this;
        }

        /**
         * Set whether to write the line costs.
         * @param writeCosts whether to write the line costs
         * @return this object
         */
        public Builder writeCosts(boolean writeCosts) {
            this.writeCosts = writeCosts;
            return this;
        }

        /**
         * Set whether to write the line concept, i.e., the lines with their frequencies.
         * @param writeLineConcept whether to write the line concept
         * @return this object
         */
        public Builder writeLineConcept(boolean writeLineConcept) {
            this.writeLineConcept = writeLineConcept;
            return this;
        }

        /**
         * Set the line pool file name, i.e., the file to write the lines without their frequencies to.
         * @param poolFileName the line pool file name
         * @return this object
         */
        public Builder setPoolFileName(String poolFileName) {
            this.poolFileName = poolFileName;
            return this;
        }

        /**
         * Set the line cost file name.
         * @param poolCostFileName the line cost file name
         * @return this object
         */
        public Builder setPoolCostFileName(String poolCostFileName) {
            this.poolCostFileName = poolCostFileName;
            return this;
        }

        /**
         * Set the line concept file name, i.e., the file to write the lines with their frequencies to.
         * @param conceptFileName the line concept file name
         * @return this object
         */
        public Builder setConceptFileName(String conceptFileName) {
            this.conceptFileName = conceptFileName;
            return this;
        }

        /**
         * Set the config to read default file names and headers from
         * @param config the config to read default file names and headers from
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set the header for the line pool file, i.e., the file of the lines without their frequencies.
         * @param poolHeader the line pool header
         * @return this object
         */
        public Builder setPoolHeader(String poolHeader) {
            this.poolHeader = poolHeader;
            return this;
        }

        /**
         * Set the header for the line cost file.
         * @param poolCostHeader the line cost header
         * @return this object
         */
        public Builder setPoolCostHeader(String poolCostHeader) {
            this.poolCostHeader = poolCostHeader;
            return this;
        }

        /**
         * Set the header for the line concept file, i.e., the file of the lines with their frequencies.
         * @param conceptHeader the line concept header
         * @return this object
         */
        public Builder setConceptHeader(String conceptHeader) {
            this.conceptHeader = conceptHeader;
            return this;
        }

        /**
         * Set the conversion factor for the lengths. Is used to convert the lengths of the lines to kilometers.
         * @param conversionFactorLength the conversion factor for lengths
         * @return this object
         */
        public Builder setConversionFactorLength(Double conversionFactorLength) {
            this.conversionFactorLength = conversionFactorLength;
            return this;
        }
    }
}
