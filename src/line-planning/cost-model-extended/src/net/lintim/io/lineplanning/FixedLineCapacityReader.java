package net.lintim.io.lineplanning;

import net.lintim.exception.DataIndexNotFoundException;
import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.io.CsvReader;
import net.lintim.model.Line;
import net.lintim.model.LinePool;
import net.lintim.util.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A reader for line capacities. Use a {@link Builder} to create a reader and {@link #read()} afterwards.
 */
public class FixedLineCapacityReader {

    private final String fileName;
    private final LinePool fixedLines;
    private final Map<Line, Integer> capacities;

    private FixedLineCapacityReader(Builder builder) {
        this.fixedLines = builder.fixedLines;
        this.fileName = "".equals(builder.fileName) ?
            builder.config.getStringValue("filename_lc_fixed_line_capacities") : builder.fileName;
        this.capacities = builder.capacities == null ? new HashMap<>() : builder.capacities;
    }

    /**
     * Start the reader process. The behaviour of the reader is determined by the {@link Builder} that was used for
     * creating the reader
     *
     * @return the read line capacities
     */
    public Map<Line, Integer> read() {
        CsvReader.readCsv(fileName, this::processCapacityLine);
        return capacities;
    }

    private void processCapacityLine(String[] args, int lineNumber) {
        if (args.length != 2) {
            throw new InputFormatException(fileName, args.length, 2);
        }
        int lineId;
        int capacity;
        try {
            lineId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
        }
        try {
            capacity = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 2, lineNumber, "int", args[1]);
        }
        Line line = fixedLines.getLine(lineId);
        if (line == null) {
            throw new DataIndexNotFoundException("Line", lineId);
        }
        capacities.put(line, capacity);
    }

    /**
     * A Builder object for a line capacity reader. See {@link #Builder(LinePool)} for the default values and an
     * explanation of the parameters
     */
    public static class Builder {
        private final LinePool fixedLines;
        private String fileName = "";
        private Map<Line, Integer> capacities = null;
        private Config config = Config.getDefaultConfig();

        /**
         * Create a new Builder with default values for all parameters. The parameters are (with their default in
         * paranthesis):
         * <ul>
         *     <li>
         *          pool of fixed lines (set in constructor) - the line pool. Needs to contain all fixed lines, i.e.,
         *          all lines the capacities should be read for. The reading procedure will fail with a
         *          {@link DataIndexNotFoundException} if there is a line in the capacities file that can not be
         *          found in the given pool.
         *     </li>
         *     <li>
         *          capacities (empty {@link java.util.Map<Line,Integer>}) - the map to store the line capacities in
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file name from, if none was
         *          given
         *     </li>
         *     <li>
         *          file name (dependent on config) - the file to read
         *     </li>
         * </ul>
         *
         * @param fixedLines the pool containing all fixed lines
         */
        public Builder(LinePool fixedLines) {
            this.fixedLines = fixedLines;
        }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder setCapacities(Map<Line, Integer> capacities) {
            Objects.requireNonNull(capacities);
            this.capacities = capacities;
            return this;
        }

        public Builder setConfig(Config config) {
            Objects.requireNonNull(config);
            this.config = config;
            return this;
        }

        /**
         * Build a line capacity reader with the current parameter set in the Builder. Use {@link #read()} afterwards
         * for the reading process
         *
         * @return the finished reader
         */
        public FixedLineCapacityReader build() {
            return new FixedLineCapacityReader(this);
        }

    }
}
