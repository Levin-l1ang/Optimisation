package net.lintim.util;

import net.lintim.exception.StatisticKeyNotFoundException;
import net.lintim.exception.StatisticTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

/**
 * Implementation for a statistic class, handling all evaluation parameter interaction. Based on the "old" LinTim
 * implementation. Supports static and non-static use, where the static methods operate on a "default" statistic object.
 */
public class Statistic implements MapData {

    /**
     * The default statistic. All static methods in this class will operate on this object.
     */
    private static Statistic defaultStatistic = new Statistic();
    /**
     * The map to hold the stored data
     */
    private final TreeMap<String, String> data;

    /**
     * Create a new statistic object. Can be filled automatically using a {@link net.lintim.io.StatisticReader} or
     * manually using {@link #put(String, String)}, {@link #put(String, double)},
     * {@link #put(String, boolean)}, {@link #put(String, int)} or
     * {@link #put(String, long)}. Can be queried using {@link #getBooleanValue(String)},
     * {@link #getDoubleValue(String)}, {@link #getIntegerValue(String)}, {@link #getLongValue(String)} and
     * {@link #getStringValue(String)}.
      */
    public Statistic(){
        this.data = new TreeMap<>();
    }

    /**
     * Set the generic value for the given key. Please use one of the specialised methods, namely
     * {@link #put(String, double)},
     * {@link #put(String, boolean)}, {@link #put(String, int)} or
     * {@link #put(String, long)}.
     *
     * @param key   the key to search for
     * @param value the value to set
     */
    public void put(String key, String value) {
        data.put(key, value);
    }

    /**
     * Set the generic value for the given key in the default statistic. Please use one of the specialised methods,
     * namely {@link #put(String, String)}, {@link #put(String, double)},
     * {@link #put(String, boolean)}, {@link #put(String, int)} or
     * {@link #put(String, long)}.
     * @param key the key to search for
     * @param value the value to set
     */
    public static void putStatic(String key, String value){
        defaultStatistic.put(key, value);
    }

    /**
     * Get the statistic entry for the given key, as a String.
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     * @throws StatisticKeyNotFoundException if there is no such key
     */
    public String getStringValue(String key) throws StatisticKeyNotFoundException {
        String value = data.get(key);
        if (value == null) {
            throw new StatisticKeyNotFoundException(key);
        }
        return value;
    }

    /**
     * Get the statistic entry for the given key from the default statistic, as a String.
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     * @throws StatisticKeyNotFoundException if there is no such key
     */
    public static String getStringValueStatic(String key) throws StatisticKeyNotFoundException {
        return defaultStatistic.getStringValue(key);
    }

    /**
     * Get the value for the given key from the default statistic, as a Double
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     * @throws StatisticKeyNotFoundException if there is no such key
     * @throws StatisticTypeMismatchException if the retrieved value cannot be cast to double
     */
    public static double getDoubleValueStatic(String key) throws StatisticKeyNotFoundException,
        StatisticTypeMismatchException {
        return defaultStatistic.getDoubleValue(key);
    }

    /**
     * Get the value for the given key from the default statistic, as a Boolean
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     * @throws StatisticKeyNotFoundException if there is no such key
     * @throws StatisticTypeMismatchException if the retrieved value is not true or false
     */
    public static boolean getBooleanValueStatic(String key) throws StatisticKeyNotFoundException,
        StatisticTypeMismatchException {
        return defaultStatistic.getBooleanValue(key);
    }

    /**
     * Get the value for the given key from the default statistic, as an Integer
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     * @throws StatisticKeyNotFoundException if there is no such key
     * @throws StatisticTypeMismatchException if the retrieved value cannot be cast to integer
     */
    public static int getIntegerValueStatic(String key) throws StatisticKeyNotFoundException,
        StatisticTypeMismatchException {
        return defaultStatistic.getIntegerValue(key);
    }

    /**
     * Get the value for the given key from the default statistic, as a Long
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     * @throws StatisticKeyNotFoundException if there is no such key
     * @throws StatisticTypeMismatchException if the retrieved value cannot be cast to long
     */
    public static Long getLongValueStatic(String key) throws StatisticKeyNotFoundException,
        StatisticTypeMismatchException {
        return defaultStatistic.getLongValue(key);
    }

    /**
     * Put the given value in the default statistic, associated with the given key.
     * @param key the key to put
     * @param value the value to put
     */
    public static void putStatic(String key, int value) {
        defaultStatistic.put(key, value);
    }
    /**
     * Put the given value in the default statistic, associated with the given key.
     * @param key the key to put
     * @param value the value to put
     */
    public static void putStatic(String key, double value) {
        defaultStatistic.put(key, value);
    }
    /**
     * Put the given value in the default statistic, associated with the given key.
     * @param key the key to put
     * @param value the value to put
     */
    public static void putStatic(String key, boolean value) {
        defaultStatistic.put(key, value);
    }
    /**
     * Put the given value in the default statistic, associated with the given key.
     * @param key the key to put
     * @param value the value to put
     */
    public static void putStatic(String key, long value) {
        defaultStatistic.put(key, value);
    }

    /**
     * Get the current contents of the statistic. Changes on the returned map will be reflected in the config and
     * vice versa!
     *
     * @return the statistic data
     */
    public Map<String, String> getData() {
        return data;
    }

    /**
     * Get the default statistic. This is the statistic object that is accessed using the static methods in this class
     * @return the default statistic
     */
    public static Statistic getDefaultStatistic(){
        return defaultStatistic;
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

        Statistic statistic = (Statistic) o;

        return data.equals(statistic.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
