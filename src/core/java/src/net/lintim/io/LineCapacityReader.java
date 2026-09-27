package net.lintim.io;

import net.lintim.exception.DataIndexNotFoundException;
import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.Graph;
import net.lintim.model.Line;
import net.lintim.model.LinePool;
import net.lintim.util.Config;
import net.lintim.util.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to read line capacities.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class LineCapacityReader {
    private static final Logger logger = new Logger(LineCapacityReader.class.getCanonicalName());

    private final String fileName;
    private final LinePool linePool;
    private final Map<Line, Integer> capacities;

    private LineCapacityReader(Builder builder) {
        this.fileName = "".equals(builder.fileName) ?
            builder.config.getStringValue("filename_lc_fixed_line_capacities") : builder.fileName;
        this.linePool = builder.linePool;
        this.capacities = builder.capacities == null ? new HashMap<>() : builder.capacities;
    }

    /**
     * Read the line capacities. To determine which file to read, a {@link Builder} object is used. See the
     * corresponding documentation for possible configuration options.
     * @return the read line capacities
     */
    public Map<Line, Integer> read() throws InputFormatException, InputTypeInconsistencyException,
        DataIndexNotFoundException {
        CsvReader.readCsv(this.fileName, this::processLine);
        return capacities;
    }

    private void processLine(String[] args, int lineNumber) {
        if (args.length != 2) {
            throw new InputFormatException(fileName, args.length, 2);
        }
        int lineId;
        int capacitiy;
        try {
            lineId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
        }
        try {
            capacitiy = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 2, lineNumber, "int", args[1]);
        }
        Line line = linePool.getLine(lineId);
        if (line == null) {
            throw new DataIndexNotFoundException("Line", lineId);
        }
        this.capacities.put(line, capacitiy);
    }

    /**
     * Builder object for a line capacity reader.
     *
     * Use {@link #Builder(LinePool)} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(LinePool)}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String fileName = "";
        private final LinePool linePool;
        private Map<Line, Integer> capacities;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *          file name (dependent on config) - the file name to read the line capacities from.
         *     </li>
         *     <li>
         *          capacities (empty {@link Map}) - the map to add the line capacities to
         *     </li>
         *     <li>
         *          linePool (set in constructor) - the lines to read the capacities for.
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()} - the config to read the file names from. This will only
         *          happen if the file names are not given but queried
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         * @param linePool the lines to read the capacities for.
         */
        public Builder(LinePool linePool) {
            this.linePool = linePool;
        }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder setCapacityMap(Map<Line, Integer> capacities) {
            this.capacities = capacities;
            return this;
        }

        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        public LineCapacityReader build() {
            return new LineCapacityReader(this);
        }
    }
}


