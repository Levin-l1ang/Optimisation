package net.lintim.io;

import net.lintim.exception.*;
import net.lintim.model.*;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Class to read files of a line pool or line concept.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class LineReader {
    private static final Logger logger = new Logger(LineReader.class);

    private final boolean readLines;
    private final boolean readLineCosts;
    private final boolean readFrequencies;
    private final String lineFileName;
    private final String lineCostFileName;
    private final LinePool lines;
    private final Graph<Stop, Link> ptn;
    private final boolean directed;
    private final Set<Line> alreadyReadLines;
    private final double conversionFactorLength;

    private LineReader(Builder builder) {
        if (!builder.readLines && builder.readFrequencies) {
            logger.warn("Cannot read frequencies but no lines, will read lines as well!");
            this.readLines = true;
        }
        else {
            this.readLines = builder.readLines;
        }
        this.readLineCosts = builder.readCosts;
        this.readFrequencies = builder.readFrequencies;
        if (readLines) {
            String lineFileConfigKey = readFrequencies ? "default_lines_file" : "default_pool_file";
            this.lineFileName = "".equals(builder.lineFileName) ? builder.config.getStringValue(lineFileConfigKey) :
                builder.lineFileName;
            conversionFactorLength = builder.conversionFactorLength == null ? builder.config.getDoubleValue
                ("gen_conversion_length") : builder.conversionFactorLength;
        }
        else {
            this.lineFileName = "";
            conversionFactorLength = 0;
        }
        if (readLineCosts) {
            this.lineCostFileName = "".equals(builder.lineCostFileName) ?
                builder.config.getStringValue("default_pool_cost_file") : builder.lineCostFileName;
        }
        else {
            this.lineCostFileName = "";
        }
        this.lines = builder.linePool == null ? new LinePool() : builder.linePool;
        this.ptn = builder.ptn;
        this.directed = builder.createDirectedLines;
        this.alreadyReadLines = new HashSet<>();
    }


    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read lines
     */
    public LinePool read() {
        if (readLines) {
            CsvReader.readCsv(lineFileName, this::processLinePoolLine);
        }
        if (readLineCosts) {
            CsvReader.readCsv(lineCostFileName, this::processLineCostLine);
            if (alreadyReadLines.size() != lines.getLines().size()) {
                throw new DataLinePoolCostInconsistencyException(lines.getLines().size(), alreadyReadLines.size(),
                    lineCostFileName);
            }
        }
        return lines;
    }

    /**
     * Process the contents of a line pool or line concept line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 3 or 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if a line or link can not be found by their index
     * @throws LineLinkNotAddableException     if a link cannot be added to a line
     */
    private void processLinePoolLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIndexNotFoundException, LineLinkNotAddableException {
        if (readFrequencies && args.length != 4) {
            throw new InputFormatException(lineFileName, args.length, 4);
        }
        else if (!readFrequencies && args.length != 3 && args.length != 4){
            throw new InputFormatException(lineFileName, args.length, 3);
        }
        int lineId;
        int linkNumber;
        int linkId;
        int frequency;
        try {
            lineId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(lineFileName, 1, lineNumber, "int", args[0]);
        }
        try {
            linkNumber = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(lineFileName, 2, lineNumber, "int", args[1]);
        }
        try {
            linkId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(lineFileName, 3, lineNumber, "int", args[2]);
        }
        Line line;
        if (linkNumber == 1) {
            line = new Line(lineId, directed);
            lines.addLine(line);
        } else {
            line = lines.getLine(lineId);
            if (line == null) {
                throw new DataIndexNotFoundException("Line", lineId);
            }
        }
        Link link = ptn.getEdge(linkId);
        if (link == null) {
            throw new DataIndexNotFoundException("Link", linkId);
        }
        boolean linkAdded = line.addLink(link);
        if (!linkAdded) {
            throw new LineLinkNotAddableException(linkId, lineId);
        }
        if (readFrequencies) {
            try {
                frequency = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                throw new InputTypeInconsistencyException(lineFileName, 4, lineNumber, "int", args[3]);
            }
            line.setFrequency(frequency);
        }
    }

    /**
     * Process the contents of a line cost line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 3 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if a line cannot be found by its index
     */
    private void processLineCostLine(String[] args, int lineNumber) throws InputFormatException,
        InputTypeInconsistencyException, DataIndexNotFoundException {

        if (args.length != 3) {
            throw new InputFormatException(lineCostFileName, args.length, 3);
        }

        int lineId;
        double length;
        double cost;

        try {
            lineId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(lineCostFileName, 1, lineNumber, "int", args[0]);
        }
        try {
            length = Double.parseDouble(args[1]) * conversionFactorLength;
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(lineCostFileName, 2, lineNumber, "double", args[1]);
        }
        try {
            cost = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(lineCostFileName, 3, lineNumber, "double", args[2]);
        }

        Line line;
        line = lines.getLine(lineId);
        if (line == null) {
            throw new DataIndexNotFoundException("Line", lineId);
        }

        line.setLength(length);
        line.setCost(cost);
        alreadyReadLines.add(line);
    }

    /**
     * Builder object for a line reader.
     *
     * Use {@link #Builder(Graph)} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(Graph)}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private boolean readLines = true;
        private boolean readCosts = true;
        private boolean readFrequencies = false;
        private Double conversionFactorLength;
        private String lineFileName = "";
        private String lineCostFileName = "";
        private LinePool linePool;
        private final Graph<Stop, Link> ptn;
        private boolean createDirectedLines;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *          read lines (true) - whether to read in the lines. Needs to be true if frequencies should be read as
         *          well.
         *     </li>
         *     <li>
         *          read costs (true) - whether to read the costs. It is possible to read only the costs without lines,
         *          but then all lines need to be already contained in the provided line pool.
         *     </li>
         *     <li>
         *          read frequencies (false) - whether to read the frequencies. Note that the read line file needs
         *          to contain the frequency column if frequencies should be read! There is no possibility to only read
         *          the frequencies, therefore read lines will be set to true as well if frequencies should be read.
         *     </li>
         *     <li>
         *          line file name (dependent on config) - the file name to read the lines from. Dependent on whether to
         *          read the frequencies, the file needs to contain the frequency column,
         *     </li>
         *     <li>
         *          line cost file name (dependent on config) - the file name to read the line costs from
         *     </li>
         *     <li>
         *          linepool (empty line pool) - the pool to add the read lines or costs to. If only costs should be
         *          read, the corresponding lines have to be present in the line pool.
         *     </li>
         *     <li>
         *          ptn (set in constructor) - the base ptn. Needs to contain all links that should be read into lines.
         *          May be null if only costs should be read.
         *     </li>
         *     <li>
         *          create directed lines (same as {@link Graph#isDirected()} of the provided ptn) - whether directed
         *          lines should be created.
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()} - the config to read the file names from. This will only
         *          happen if the file names are not given but queried
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         * @param ptn the base ptn. Needs to contain all links that should be read into lines. May be null if only costs
         *            should be read.
         */
        public Builder(Graph<Stop, Link> ptn) {
            this.ptn = ptn;
            if (ptn != null) {
                this.createDirectedLines = ptn.isDirected();
            }
            else {
                this.createDirectedLines = false;
            }
        }

        /**
         * Set whether to read the lines. Will be set to true if frequencies should be read as well.
         * @param readLines whether to read lines
         * @return this object
         */
        public Builder readLines(boolean readLines) {
            this.readLines = readLines;
            return this;
        }

        /**
         * Set whether to read the costs. It is possible to read only the costs without lines, but then all lines need to
         * be already contained in the provided line pool.
         * @param readCosts whether to read costs
         * @return this object
         */
        public Builder readCosts(boolean readCosts) {
            this.readCosts = readCosts;
            return this;
        }

        /**
         * Set whether to read the frequencies. Note that the read line file needs to contain the frequency column
         * if frequencies should be read! There is no possibility to only read the frequencies, therefore read lines
         * will be set to true as well if frequencies should be read.
         * @param readFrequencies whether to read the frequencies
         * @return this object
         */
        public Builder readFrequencies(boolean readFrequencies) {
            this.readFrequencies = readFrequencies;
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

        /**
         * Set the file name to read the lines from. Dependent on whether to read the frequencies, the file needs to
         * contain the frequency column,
         * @param lineFileName the file name to read the lines from
         * @return this object
         */
        public Builder setLineFileName(String lineFileName) {
            this.lineFileName = lineFileName;
            return this;
        }

        /**
         * Set the file name to read the costs from.
         * @param lineCostFileName the file name to read the costs from
         * @return this object
         */
        public Builder setLineCostFileName(String lineCostFileName) {
            this.lineCostFileName = lineCostFileName;
            return this;
        }

        /**
         * Set the line pool to add the read lines and costs to.  If only costs should be read, the corresponding lines
         * have to be present in the line pool.
         * @param linePool the line pool to add the lines and costs to.
         * @return this object
         */
        public Builder setLinePool(LinePool linePool) {
            this.linePool = linePool;
            return this;
        }

        /**
         * Set whether to create directed lines.
         * @param createDirectedLines whether to create directed lines
         * @return this object
         */
        public Builder setCreateDirectedLines(boolean createDirectedLines) {
            this.createDirectedLines = createDirectedLines;
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
         * Create a new line reader with the current builder settings.
         * @return the new reader. Use {@link LineReader#read()} for the reading process.
         */
        public LineReader build() {
            return new LineReader(this);
        }
    }
}
