package net.lintim.io;

import net.lintim.model.LinePool;
import net.lintim.util.Config;
import net.lintim.util.Pair;

import java.util.ArrayList;
import java.util.Set;

/**
 * Class to write restricted turns.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the writer and use {@link #write()} afterwards.
 */
public class RestrictedTurnWriter {
    private final Set<Pair<Integer, Integer>> restrictedTurns;
    private final String fileName;
    private final String header;

    private RestrictedTurnWriter(Builder builder) {
        this.restrictedTurns = builder.restrictedTurns;
        String configFileKey = builder.infrastructure ? "filename_turn_restrictions_infrastructure" : "filename_turn_restrictions";
        this.fileName = !"".equals(builder.fileName) ? builder.fileName : builder.config.getStringValue(configFileKey);
        this.header = !"".equals(builder.header) ? builder.header : builder.config.getStringValue("restricted_turns_header");
    }

    /**
     * Start the writing process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     */
    public void write() {
        CsvWriter.writeList(fileName, header, new ArrayList<>(restrictedTurns), this::toOutput);
    }

    private String[] toOutput(Pair<Integer, Integer> pair) {
        String[] result = new String[2];
        result[0] = String.valueOf(pair.getFirstElement());
        result[1] = String.valueOf(pair.getSecondElement());
        return result;
    }

    /**
     * Builder object for a restricted turn writer.
     *
     * Use {@link #Builder(Set)} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(Set)}. To create a writer object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private final Set<Pair<Integer, Integer>> restrictedTurns;
        private boolean infrastructure = false;
        private String fileName = "";
        private String header = "";
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to read the file names and headers from. This
         *          will only happen, if the file names are not given, but queried.
         *     </li>
         *     <li>
         *          file name (dependent on config) - the file name to write the restricted turns to
         *     </li>
         *     <li>
         *          header (dependent on config) - the header to use for the file
         *     </li>
         *     <li>
         *         infrastructure (false) - whether to write restricted turns for a infrastructure or a ptn. This
         *         influences the default file name to write to.
         *     </li>
         *     <li>
         *          restrictedTurns (given in constructor) - the restricted turns to write. Can only be set in
         *          the constructor
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a writer with the given parameters.
         * @param restrictedTurns the restricted turns to write
         */
        public Builder(Set<Pair<Integer, Integer>> restrictedTurns) {
            this.restrictedTurns = restrictedTurns;
        }

        public Builder infrastructure(boolean infrastructure) {
            this.infrastructure = infrastructure;
            return this;
        }

        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder setHeader(String header) {
            this.header = header;
            return this;
        }

        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new restricted turn writer with the current builder settings
         * @return the new writer. Use {@link RestrictedTurnWriter#write()} for the writing process.
         */
        public RestrictedTurnWriter build() {
            return new RestrictedTurnWriter(this);
        }
    }
}
