package net.lintim.io;

import net.lintim.exception.ConfigTypeMismatchException;
import net.lintim.exception.InputFileException;
import net.lintim.exception.InputFormatException;
import net.lintim.util.Config;
import net.lintim.util.LogLevel;
import net.lintim.util.Logger;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Class to read files of a config.
 *
 * Use {@link Builder#build()} on a {@link Builder} object to create the reader and use {@link #read()} afterwards.
 */
public class ConfigReader {
    private final String sourceFileName;
    private final boolean onlyIfExists;
    private final Config config;
    private static final Logger logger = new Logger(ConfigReader.class);

    private ConfigReader(Builder builder) {
        Locale.setDefault(Locale.US);
        sourceFileName = builder.configFileName;
        this.onlyIfExists = builder.onlyIfExist;
        this.config = builder.config;
    }

    /**
     * Start the reading process. The behavior is controlled by the {@link Builder} object, this object was created
     * with.
     * @return the read config
     */
    public Config read() {
        CsvReader.readCsv(sourceFileName, this::processConfigLine);
        return config;
    }

    /**
     * Process the contents of a config line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line has less than two entries
     * @throws UncheckedIOException on any io error while trying to read included config files
     */
    private void processConfigLine(String[] args, int lineNumber) throws InputFormatException, UncheckedIOException {
        //We except at least two entries (more than two, if the value contains a ";"
        if (args.length <= 1) {
            throw new InputFormatException(sourceFileName, args.length, 2);
        }
        String key = args[0];
        String value = String.join("; ", Arrays.copyOfRange(args, 1, args.length));
        //Trim quotation marks, if available
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1);
        }
        switch (key) {
            case "include":
                try {
                    new Builder(Paths.get(sourceFileName).toAbsolutePath().getParent().toString() + File
                        .separator + value).setConfig(config).build().read();
                } catch (InputFileException e) {
                    logger.warn("Caught InputFileException while reading " + Paths.get(sourceFileName)
                        .toAbsolutePath().getParent().toString() + File.separator + value + ": " + e.toString());
                    if (!onlyIfExists) {
                        throw e;
                    }
                }
                break;
            case "include_if_exists":
                String fileToInclude = Paths.get(sourceFileName).toAbsolutePath().getParent().toString() + File
                    .separator + value;
                if (Files.exists(Paths.get(fileToInclude))) {
                    new Builder(fileToInclude).setConfig(config).build().read();
                }
                break;
            case "console_log_level":
                Level newConsoleLevel;
                switch (value.toUpperCase()) {
                    case "FATAL":
                        newConsoleLevel = LogLevel.FATAL;
                        break;
                    case "ERROR":
                        newConsoleLevel = LogLevel.ERROR;
                        break;
                    case "WARN":
                        newConsoleLevel = LogLevel.WARN;
                        break;
                    case "INFO":
                        newConsoleLevel = LogLevel.INFO;
                        break;
                    case "DEBUG":
                        newConsoleLevel = LogLevel.DEBUG;
                        break;
                    default:
                        throw new ConfigTypeMismatchException(key, "FATAL/ERROR/WARN/INFO/DEBUG", value);
                }
                setGlobalConsoleLogLevel(newConsoleLevel);
            default:
                config.put(key, value);
                break;
        }
    }

    /**
     * Set the global logger level for the console handler to the given level
     * @param newLevel the new level for the console handler
     */
    private static void setGlobalConsoleLogLevel(Level newLevel) {
        java.util.logging.Logger globalLogger = LogManager.getLogManager().getLogger("");
        //Set the level for all loggers, otherwise the message will be thrown away on logger level
        globalLogger.setLevel(newLevel);
        for (Handler handler : globalLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                //Set the new level for the console handler, otherwise the messages would not get handled here
                handler.setLevel(newLevel);
            }
        }
    }

    /**
     * Builder object for a config reader.
     *
     * Use {@link #Builder(String)} to create a builder with default options, afterwards use the setter to adapt it.
     * The setters return this object, therefore they can be chained.
     *
     * For the possible parameters and their default values, see {@link #Builder(String)}. To create a reader object,
     * use {@link #build()} after setting all parameters.
     */
    public static class Builder {
        private final String configFileName;
        private Config config = Config.getDefaultConfig();
        private boolean onlyIfExist = false;

        /**
         * Create a default builder object. Possible parameters for this class are (with the default in parentheses):
         * <ul>
         *     <li>
         *         config file name (set in constructor) - the file to read
         *     </li>
         *     <li>
         *          config ({@link Config#getDefaultConfig()}) - the config to write the read values to
         *     </li>
         *     <li>
         *          only if exist (false) - whether to ignore io errors on included files inside the read config
         *     </li>
         * </ul>
         * All values can be set using the corresponding setters of this class. If you are ready, call
         * {@link #build()} to create a reader with the given parameters.
         * @param configFileName the file name to read
         */
        public Builder(String configFileName) {
            if (configFileName == null || configFileName.isEmpty()) {
                throw new IllegalArgumentException("File name in config reader builder can not be null or empty");
            }
            this.configFileName = configFileName;
        }

        /**
         * Set the config to write the read values to
         * @param config the config to fill
         * @return this object
         */
        public Builder setConfig(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Set, whether to ignore io errors on included files inside the read config.
         * @param onlyIfExist whether to ignore io errors on included files
         * @return this object
         */
        public Builder setOnlyIfExist (boolean onlyIfExist) {
            this.onlyIfExist = onlyIfExist;
            return this;
        }

        /**
         * Create a new config reader with the current builder settings.
         * @return the new reader. Use {@link ConfigReader#read()} for the reading process.
         */
        public ConfigReader build() {
            return new ConfigReader(this);
        }
    }
}
