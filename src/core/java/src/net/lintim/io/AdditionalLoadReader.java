package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.util.Config;
import net.lintim.util.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to read an additional load file.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create a reader and use {@link #read()} afterwards.
 */
public class AdditionalLoadReader {

    private final String fileName;
    private final Map<Integer, Map<Pair<Integer, Integer>, Double>> loads;

    private AdditionalLoadReader(Builder builder) {
        this.loads = builder.loads == null ? new HashMap<>() : builder.loads;
        this.fileName = "".equals(builder.fileName) ? builder.config.getStringValue("filename_additional_load_file") : builder.fileName;
    }

    /**
     * Read an additional load file. For controlling which file to read, the given {@link Builder} object will be used.
     * See the corresponding documentation for possible configuration options
     * @return the read load information
     */
    public Map<Integer, Map<Pair<Integer, Integer>, Double>> read() {
        CsvReader.readCsv(fileName, this::processLoadLine);
        return loads;
    }

    private void processLoadLine(String[] args, int lineNumber) throws InputFormatException, InputTypeInconsistencyException {
        if (args.length != 4) {
            throw new InputFormatException(fileName, args.length, 4);
        }
        int linkId, leftStopId, rightStopId;
        double load;
        try {
            linkId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
        }
        try {
            leftStopId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 2, lineNumber, "int", args[1]);
        }
        try {
            rightStopId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 3, lineNumber, "int", args[2]);
        }
        try {
            load = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 4, lineNumber, "double", args[3]);
        }
        loads.computeIfAbsent(linkId, HashMap::new).put(new Pair<>(leftStopId, rightStopId), load);
    }

    public static class Builder {
        private String fileName = "";
        private Config config = Config.getDefaultConfig();
        private Map<Integer, Map<Pair<Integer, Integer>, Double>> loads;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parantheses):
         * <ul>
         *     <li>
         *         file name (dependent on config) - the file to read
         *     </li>
         *     <li>
         *         config ({@link Config#getDefaultConfig()}) - the config to read the file name from. This will only
         *         happen if the file name is not given but queried.
         *     </li>
         *     <li>
         *         loads (Emtpy {@link HashMap}) - The map to fill, indexed by link id and pair of stops.
         *     </li>
         * </ul>
         */
        public Builder() { }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        public Builder setMap(Map<Integer, Map<Pair<Integer, Integer>, Double>> loads) {
            this.loads = loads;
            return this;
        }

        public AdditionalLoadReader build() {
            return new AdditionalLoadReader(this);
        }

    }
}
