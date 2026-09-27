package net.lintim.io;

import net.lintim.exception.InputFormatException;
import net.lintim.util.Config;
import net.lintim.util.Statistic;

/**
 * Class to read files of a statistic.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class StatisticReader {
    private final String sourceFileName;
    private final Statistic statistic;

    private StatisticReader(Builder builder) {
        this.statistic = builder.statistic == null ? Statistic.getDefaultStatistic() : builder.statistic;
        this.sourceFileName = "".equals(builder.fileName) ?
            builder.config.getStringValue("default_statistic_file") : builder.fileName;
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read statistic
     */
    public Statistic read() {
        CsvReader.readCsv(sourceFileName, this::processStatisticLine);
        return statistic;
    }

    /**
     * Process the contents of a statistic line.
     * @param args the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line contains not exactly 3 or 4 entries
     */
    private void processStatisticLine(String[] args, int lineNumber) throws InputFormatException {
        if(args.length != 2){
            throw new InputFormatException(sourceFileName, args.length, 2);
        }
        String key = args[0];
        String value = args[1];
        //Remove quotation marks
        if(value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length()-1) == '"'){
            value = value.substring(1, value.length()-1);
        }
        statistic.put(key, value);
    }

    /**
     * Builder object for a statistic reader.
     *
     * Use {@link #Builder()} to create a builder with default options, afterwards use the setter to adapt it. The
     * setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder()}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private String fileName = "";
        private Statistic statistic = Statistic.getDefaultStatistic();
        private Config config = Config.getDefaultConfig();

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         statistic ({@link Statistic#getDefaultStatistic()}) - the statistic to store the read data in.
         *     </li>
         *     <li>
         *         config ({@link Config#getDefaultConfig()}) - the config to read the file name from. This will only
         *         happen, if the file name is not given, but queried.
         *     </li>
         *     <li>
         *         file name (dependent on config) - the file name to read the statistic from
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         */
        public Builder() { }

        /**
         * Set the file name to read the statistic from
         * @param fileName the file name
         * @return this object
         */
        public Builder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Set the statistic to store the read data in
         * @param statistic the statistic
         * @return this object
         */
        public Builder setStatistic(Statistic statistic) {
            this.statistic = statistic;
            return this;
        }

        /**
         * Set the config. The config is used to read the file name if it is queried but not given.
         * @param config the config
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Create a new statistic reader with the current builder settings
         * @return the new reader. Use {@link #read()} for the reading process.
         */
        public StatisticReader build() {
            return new StatisticReader(this);
        }
    }
}
