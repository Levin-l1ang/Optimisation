package net.lintim.model;

import java.util.LinkedHashMap;
import java.util.Map;

import net.lintim.exception.DataInconsistentException;

/**
 * Javatools internal LinTim configuration representation: a {@link Map} with
 * {@link String} as key and {@link String} as value with some additional helper
 * functions for conversions.
 *
 */
public class Configuration {

    LinkedHashMap<String, String> data = new LinkedHashMap<String, String>();

    public Configuration(){

    }

    public Configuration(Configuration toCopy){
        data.putAll(toCopy.data);
    }

    public void setValue(String name, String value){
        data.put(name, value);
    }

    /**
     * Throws an exception if <code>key</code> does not exist.
     *
     * @param key The statistic key to check.
     * @throws DataInconsistentException If the key does not exist in the map.
     */
    public void ensureSettingExists(String name) throws DataInconsistentException{
        if(!data.keySet().contains(name)){
            throw new DataInconsistentException("no setting with name " + name
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
    public String getStringValue(String name) throws DataInconsistentException{
        ensureSettingExists(name);
        return data.get(name);
    }

    /**
     * Access a double conversion of the string value at <code>key</code>.
     *
     * @param key The statistic key.
     * @return Double conversion of the string at <code>key</code>.
     * @throws DataInconsistentException
     */
    public Double getDoubleValue(String name) throws DataInconsistentException{
        ensureSettingExists(name);
        return Double.parseDouble(data.get(name));
    }

    /**
     * Access a boolean conversion of the string value at <code>key</code>.
     *
     * @param key The statistic key.
     * @return Boolean conversion of the string at <code>key</code>.
     * @throws DataInconsistentException
     */
    public Boolean getBooleanValue(String name) throws DataInconsistentException{
        ensureSettingExists(name);
        return Boolean.parseBoolean(data.get(name));
    }

    /**
     * Access a integer conversion of the string value at <code>key</code>.
     *
     * @param key The statistic key.
     * @return Integer conversion of the string at <code>key</code>.
     * @throws DataInconsistentException
     */
    public Integer getIntegerValue(String name) throws DataInconsistentException{
        ensureSettingExists(name);
        return Integer.parseInt(data.get(name));
    }

    public LinkedHashMap<String, String> getData() {
        return data;
    }

}
