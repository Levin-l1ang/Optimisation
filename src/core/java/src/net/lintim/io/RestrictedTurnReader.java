package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.exception.InputTypeInconsistencyException;
import net.lintim.model.Graph;
import net.lintim.model.Link;
import net.lintim.model.Stop;
import net.lintim.util.Config;
import net.lintim.util.Pair;

import java.util.HashSet;
import java.util.Set;

/**
 * Class to read restricted turns
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class RestrictedTurnReader {
    private final Set<Pair<Integer, Integer>> restrictedTurns;
    private final String fileName;

    private RestrictedTurnReader(Builder builder) {
        this.restrictedTurns = builder.restrictedTurns;
        if (builder.infrastructure) {
            this.fileName = !"".equals(builder.fileName) ? builder.fileName : builder.config.getStringValue("filename_turn_restrictions_infrastructure");
        }
        else {
            this.fileName = !"".equals(builder.fileName) ? builder.fileName : builder.config.getStringValue("filename_turn_restrictions");
        }
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read restricted turns, as pairs of link ids
     */
    public Set<Pair<Integer, Integer>> read() {
        CsvReader.readCsv(fileName, this::processRestrictedTurnLine);
        return restrictedTurns;
    }

    private void processRestrictedTurnLine(String[] args, int lineNumber) {
        if (args.length != 2) {
            throw new InputFormatException(fileName, args.length, 2);
        }
        int firstLinkId = -1, secondLinkId = -1;
        try {
            firstLinkId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 1, lineNumber, "int", args[0]);
        }
        try {
            secondLinkId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new InputTypeInconsistencyException(fileName, 2, lineNumber, "int", args[1]);
        }
        restrictedTurns.add(new Pair<>(firstLinkId, secondLinkId));
    }

    /**
     * Builder object for a restricted turn reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private Config config = Config.getDefaultConfig();
        private String fileName = "";
        private Set<Pair<Integer, Integer>> restrictedTurns = new HashSet<>();
        private boolean infrastructure = false;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *          file name (dependent on config) - the file name to read the restricted turns from.
         *     </li>
         *     <li>
         *          restrictedTurns (empty @{@link Set<Pair>}) - the set to add the restricted turns to. Restricted
         *          turns are represented as a pair of link ids
         *     </li>
         *     <li>
         *          infrastructure (false) - whether to the read infrastructure or ptn restricted turns. This
         *          influences the default file name to read.
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()} - the config to read the file names from. This will only
         *          happen if the file names are not given but queried
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

        public Builder setRestrictedTurns(Set<Pair<Integer, Integer>> restrictedTurns) {
            this.restrictedTurns = restrictedTurns;
            return this;
        }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder isInfrastructure(boolean infrastructure) {
            this.infrastructure = infrastructure;
            return this;
        }

        /**
         * Create a new restricted turn reader with the current builder settings.
         * @return the new reader. Use {@link RestrictedTurnReader#read()} for the reading process.
         */
        public RestrictedTurnReader build() {
            return new RestrictedTurnReader(this);
        }
    }
}
