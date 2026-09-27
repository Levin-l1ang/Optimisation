package net.lintim.util;

import net.lintim.exception.ConfigKeyNotFoundException;
import net.lintim.exception.ConfigTypeMismatchException;
import net.lintim.exception.LinTimException;
import net.lintim.io.ConfigReader;
import net.lintim.solver.Solver;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Implementation of a config class, handling all the config interaction. Based on the "old" LinTim implementation.
 * Has static and non-static methods, where the static methods operate on a "default" config object
 */
public class Config implements MapData {

    /**
     * The default config. The static methods in this class will use this object as data storage
     */
    private static Config defaultConfig = new Config();

    /**
     * The map to hold the config data.
     */
    private final TreeMap<String, String> data;

    /**
     * The logger to log read values.
     */
    private static Logger logger = new Logger(Config.class);

    /**
     * Initialize an empty config. Can be filled manually with {@link #put(String, String)} or automatically with
     * a {@link ConfigReader}. Information from the config can be read with {@link #getBooleanValue(String)},
     * {@link #getIntegerValue(String)}, {@link #getLogLevel(String)}, {@link #getSolverType(String)} and
     * {@link #getStringValue(String)}.
     */
    public Config() {
        this.data = new TreeMap<>();
    }

    /**
     * Get the string value of the given config key.
     *
     * @param key the key to look for in the config
     * @return the value to the given key
     */
    public String getStringValue(String key) throws ConfigKeyNotFoundException {
        if (key.equals("default_activities_periodic_file")
            && data.keySet().contains("use_buffered_activities")
            && getBooleanValue("use_buffered_activities")) {
            key = "default_activity_buffer_file";
        } else if (key.equals("default_activities_periodic_unbuffered_file")) {
            if (data.keySet().contains("use_buffered_activities") && getBooleanValue("use_buffered_activities")) {
                key = "default_activity_relax_file";
            } else {
                key = "default_activities_periodic_file";
            }
        }
        String value = data.get(key);
        if (value == null) {
            throw new ConfigKeyNotFoundException(key);
        }
        logger.debug("Read key " + key + " with value " + value + " from config");
        return value;
    }

    /**
     * Get the string value of the given config key from the default config.
     *
     * @param key the key to look for in the config
     * @return the value to the given key
     */
    public static String getStringValueStatic(String key) throws ConfigKeyNotFoundException {
        return defaultConfig.getStringValue(key);
    }

    /**
     * Get the double value of the given config key from the default config
     *
     * @param key the key to look for
     * @return the double value to the given key
     * @throws ConfigKeyNotFoundException  if the queried key cannot be found
     * @throws ConfigTypeMismatchException if the queried value cannot be parsed as double
     */
    public static double getDoubleValueStatic(String key) throws ConfigKeyNotFoundException,
        ConfigTypeMismatchException {
        return defaultConfig.getDoubleValue(key);
    }

    /**
     * Get the boolean value of the given config key from the default config
     *
     * @param key the key to look for
     * @return the boolean value to the given key
     * @throws ConfigKeyNotFoundException if the queried key cannot be found
     * @throws ConfigTypeMismatchException if the queried value cannot be parsed as boolean
     */
    public static boolean getBooleanValueStatic(String key) throws ConfigKeyNotFoundException,
        ConfigTypeMismatchException {
        return defaultConfig.getBooleanValue(key);
    }

    /**
     * Get the integer value of the given config key from the default config.
     *
     * @param key the key to look for
     * @return the integer value to the given key
     * @throws ConfigKeyNotFoundException  if the queried key cannot be found
     * @throws ConfigTypeMismatchException if the queried value cannot be parsed as integer
     */
    public static int getIntegerValueStatic(String key) throws ConfigKeyNotFoundException,
        ConfigTypeMismatchException {
        return defaultConfig.getIntegerValue(key);
    }

    public static long getLongValueStatic(String key) throws ConfigKeyNotFoundException, ConfigTypeMismatchException {
        return defaultConfig.getLongValue(key);
    }

    /**
     * Get the solver type value of the given config key
     *
     * @param key the key to look for
     * @return the solver type of the given key
     * @throws ConfigKeyNotFoundException  if the queried key cannot be found
     * @throws ConfigTypeMismatchException if the queried value cannot be parsed as a solver type
     */
    public SolverType getSolverType(String key) throws ConfigKeyNotFoundException, ConfigTypeMismatchException {
        String value = getStringValue(key);
        try {
            return Solver.parseSolverType(value);
        } catch (LinTimException e) {
            throw new ConfigTypeMismatchException(key, "Solvertype", value);
        }
    }

    /**
     * Get the solver type value of the given config key from the default config.
     *
     * @param key the key to look for
     * @return the solver type of the given key
     * @throws ConfigKeyNotFoundException  if the queried key cannot be found
     * @throws ConfigTypeMismatchException if the queried value cannot be parsed as a solver type
     */
    public static SolverType getSolverTypeStatic(String key) throws ConfigKeyNotFoundException,
        ConfigTypeMismatchException {
        return defaultConfig.getSolverType(key);
    }

    /**
     * Get the log level value of the given config key
     *
     * @param key the key to look for
     * @return the log level of the given key
     * @throws ConfigKeyNotFoundException  if the queried key cannot be found
     * @throws ConfigTypeMismatchException if the queried value cannot be parsed as a solver type
     */
    public Level getLogLevel(String key) throws ConfigKeyNotFoundException, ConfigTypeMismatchException {
        String value = getStringValue(key);
        switch (value.toUpperCase()) {
            case "FATAL":
                return LogLevel.FATAL;
            case "ERROR":
                return LogLevel.ERROR;
            case "WARN":
                return LogLevel.WARN;
            case "INFO":
                return LogLevel.INFO;
            case "DEBUG":
                return LogLevel.DEBUG;
            default:
                throw new ConfigTypeMismatchException(key, "FATAL/ERROR/WARN/INFO/DEBUG", value);
        }
    }

    /**
     * Get the log level value of the given config key from the default config.
     *
     * @param key the key to look for
     * @return the log level of the given key
     * @throws ConfigKeyNotFoundException  if the queried key cannot be found
     * @throws ConfigTypeMismatchException if the queried value cannot be parsed as a solver type
     */
    public static Level getLogLevelStatic(String key) throws ConfigKeyNotFoundException, ConfigTypeMismatchException {
        return defaultConfig.getLogLevel(key);
    }

    /**
     * Put the specified data into the config collection. Content with the same key will be overwritten
     *
     * @param key   the key to add
     * @param value the value to add
     */
    public void put(String key, String value) {
        data.put(key, value);
    }

    /**
     * Put the specified data into the config collection of the default config. Content with the same key will be
     * overwritten.
     *
     * @param key   the key to add
     * @param value the value to add
     */
    public static void putStatic(String key, String value) {
        defaultConfig.put(key, value);
    }

    /**
     * Put the specified data into the config collection of the default config. Content with the same key will be
     * overwritten.
     *
     * @param key   the key to add
     * @param value the value to add
     */
    public static void putStatic(String key, int value) {
        defaultConfig.put(key, value);
    }
    /**
     * Put the specified data into the config collection of the default config. Content with the same key will be
     * overwritten.
     *
     * @param key   the key to add
     * @param value the value to add
     */
    public static void putStatic(String key, double value) {
        defaultConfig.put(key, value);
    }
    /**
     * Put the specified data into the config collection of the default config. Content with the same key will be
     * overwritten.
     *
     * @param key   the key to add
     * @param value the value to add
     */
    public static void putStatic(String key, boolean value) {
        defaultConfig.put(key, value);
    }
    /**
     * Put the specified data into the config collection of the default config. Content with the same key will be
     * overwritten.
     *
     * @param key   the key to add
     * @param value the value to add
     */
    public static void putStatic(String key, long value) {
        defaultConfig.put(key, value);
    }

    /**
     * Get the default config object.
     * @return the default config
     */
    public static Config getDefaultConfig(){
        return defaultConfig;
    }

    /**
     * Get the data in the config. Changes to the returned map will reflect in the config!
     * @return the data
     */
    public Map<String, String> getData() {
        return data;
    }

    @Override
    public String toString(){
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<String, String> dataEntry : data.entrySet()){
            builder.append(dataEntry.getKey()).append(";").append(dataEntry.getValue()).append("\n");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Config config = (Config) o;

        return data.equals(config.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
