package net.lintim.model;

import net.lintim.exception.DataInconsistentException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Javatools internal LinTim statistic representation: a {@link Map} with
 * {@link String} as key and {@link String} as value with some additional helper
 * functions for conversions.
 *
 */
public class Statistic {

    LinkedHashMap<String, String> data = new LinkedHashMap<String, String>();

    public void setValue(String key, String value){
        data.put(key, value);
    }

    /**
     * Throws an exception if <code>key</code> does not exist.
     *
     * @param key The statistic key to check.
     * @throws DataInconsistentException If the key does not exist in the map.
     */
    public void ensureSettingExists(String key) throws DataInconsistentException{
        if(!data.keySet().contains(key)){
            throw new DataInconsistentException("no setting with key " + key
                    + " found");
        }
    }

    /**
     * Access the original string value of <code>key</code>.
     *
     * @param key The statistic key.
     * @return Value of the map at <code>key</code>.
     * @throws DataInconsistentException
     */
    public String getStringValue(String key) throws DataInconsistentException{
        ensureSettingExists(key);
        return data.get(key);
    }

    /**
     * Access a double conversion of the string value at <code>key</code>.
     *
     * @param key The statistic key.
     * @return Double conversion of the string at <code>key</code>.
     * @throws DataInconsistentException
     */
    public Double getDoubleValue(String key) throws DataInconsistentException{
        ensureSettingExists(key);
        return Double.parseDouble(data.get(key));
    }

    /**
     * Access a boolean conversion of the string value at <code>key</code>.
     *
     * @param key The statistic key.
     * @return Boolean conversion of the string at <code>key</code>.
     * @throws DataInconsistentException
     */
    public Boolean getBooleanValue(String key) throws DataInconsistentException{
        ensureSettingExists(key);
        return Boolean.parseBoolean(data.get(key));
    }

    /**
     * Access an integer conversion of the string value at <code>key</code>.
     *
     * @param key The statistic key.
     * @return Integer conversion of the string at <code>key</code>.
     * @throws DataInconsistentException
     */
    public Integer getIntegerValue(String key) throws DataInconsistentException{
        ensureSettingExists(key);
        return Integer.parseInt(data.get(key));
    }

    /**
     * Sets a string value at <code>key</code>.
     *
     * @param key The statistic key.
     * @param value The value to set at the key.
     * @throws DataInconsistentException
     */
    public void setStringValue(String key, String value) throws DataInconsistentException{
        data.put(key, value);
    }

    /**
     * Sets a string conversion of a double value at <code>key</code>.
     *
     * @param key The statistic key.
     * @param value The double to be converted to string to set at key.
     * @throws DataInconsistentException
     */
    public void setDoubleValue(String key, Double value) throws DataInconsistentException{
        data.put(key, ""+value);
    }

    /**
     * Sets a string conversion of a boolean value at <code>key</code>.
     *
     * @param key The statistic key.
     * @param value The boolean to be converted to string to set at key.
     * @throws DataInconsistentException
     */
    public void setBooleanValue(String key, Boolean value) throws DataInconsistentException{
        data.put(key, value ? "true" : "false");
    }

    /**
     * Sets a string conversion of an integer value at <code>key</code>.
     *
     * @param key The statistic key.
     * @param value The integer to be converted to string to set at key.
     * @throws DataInconsistentException
     */
    public void setIntegerValue(String key, Integer value) throws DataInconsistentException{
        data.put(key, ""+value);
    }

    /**
     * Sets a string conversion of a long value at <code>key</code>.
     *
     * @param key The statistic key.
     * @param value The long to be converted to string to set at key.
     * @throws DataInconsistentException
     */
    public void setLongValue(String key, Long value) throws DataInconsistentException{
        data.put(key, ""+value);
    }

    public LinkedHashMap<String, String> getData() {
        return data;
    }

}
