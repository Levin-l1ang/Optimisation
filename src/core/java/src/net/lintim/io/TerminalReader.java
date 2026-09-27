package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.DemandPoint;
import net.lintim.util.Config;

import java.util.*;

/**
 * Class to read terminal files.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class TerminalReader {
    private final Set<Integer> terminals;
    private final String fileName;

    private TerminalReader(Builder builder) {
        terminals = builder.terminals == null ? new HashSet<>() : builder.terminals;
        fileName = !"".equals(builder.fileName) ? builder.fileName : builder.config.getStringValue("filename_terminals_file");
    }

    /**
     * Read the terminals. To determine which file to read, a {@link Builder} object is used. See the
     * corresponding documentation for possible configuration options.
     * @return the read terminal data
     */
    public Set<Integer> read() {
        CsvReader.readCsv(fileName, this::processTerminalLine);
        return terminals;
    }

    private void processTerminalLine(String[] args, int lineNumber) throws InputFormatException, InputTypeInconsistencyException {
        if (args.length != 1) {
            throw new InputFormatException(fileName, args.length, 1);
        }
        try {
            int stopId = Integer.parseInt(args[0]);
            terminals.add(stopId);
        }
        catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
        }
    }

    /**
     * Builder object for a terminal reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private Config config = Config.getDefaultConfig();
        private String fileName = "";
        private Set<Integer> terminals;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         file name (dependent on config) - the file to read
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file name from. This will only
         *         happen, if the file name is not given, but queried.
         *     </li>
         *     <li>
         *          terminals (empty {@link Set<Integer>}) - the collection to write the read terminal ids to.
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder() {
        }

        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder setTerminals(Set<Integer> terminals) {
            this.terminals = terminals;
            return this;
        }

        /**
         * Create a new terminal reader with the current builder settings.
         * @return the new reader. Use {@link TerminalReader#read()} for the reading process.
         */
        public TerminalReader build() {
            return new TerminalReader(this);
        }
    }

}
