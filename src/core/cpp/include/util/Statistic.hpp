/**
 * Implementation for a statistic class, handling all evaluation parameter interaction. Based on the "old" LinTim
 * implementation, adapted for static use
 */
#ifndef STATISTIC_HPP
#define STATISTIC_HPP

#include "../exception/exceptions.hpp"
#include <map>
#include <string>
#include <algorithm>

class Statistic{
private:

    std::map <std::string, std::string> data;

public:
    Statistic(void){

    }

    /**
     * Set the generic value for the given key. Please use one of the specialised methods, namely
     * {@link #setStringValue(std::string, std::string)}, {@link #setDoubleValue(std::string, double)},
     * {@link #setBooleanValue(std::string, boolean)}, {@link #setIntegerValue(std::string, int)} or
     * {@link #setLongValue(std::string, long)}.
     *
     * @param key   the key to search for
     * @param value the value to set
     */
    void setValue(std::string key, std::string value){
        this->data[key] = value;
    }

    /**
     * Get the statistic entry for the given key, as a std::string.
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     */
    std::string getStringValue(std::string key){
        std::string value = data[key];
        if(value.empty()){
            throw new StatisticKeyNotFoundException(key);
        }
        return value;
    }

    /**
     * Get the value for the given key, as a Double
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     */
    double getDoubleValue(std::string key){
        std::string value = getStringValue(key);
        try{
            return std::stod(data[key]);
        } catch(std::exception& e){
            throw new StatisticTypeMismatchException(key, "double", value);
        }
    }

    /**
     * Get the value for the given key, as a Boolean
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     */
    bool getBooleanValue(std::string key){
        std::string value = getStringValue(key);
        transform(value.begin(), value.end(), value.begin(), ::tolower);
        if(value.find("true") != std::string::npos){
            return true;
        } else if(value.find("false") != std::string::npos){
            return false;
        } else{
            throw StatisticTypeMismatchException(key, "boolean", value);
        }
    }

    /**
     * Get the value for the given key, as an Integer
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     */
    int getIntegerValue(std::string key){
        std::string value = getStringValue(key);
        try{
            return std::stoi(data[key]);
        } catch(std::exception& e){
            throw StatisticTypeMismatchException(key, "integer", value);
        }
    }

    /**
     * Get the value for the given key, as a Long
     *
     * @param key the key to search for
     * @return the value for the given key or null, if there is none
     */
    long getLongValue(std::string key){
        std::string value = getStringValue(key);
        try{
            return std::stol(data[key]);
        } catch(std::exception& e){
            throw StatisticTypeMismatchException(key, "long", value);
        }
    }

    /**
     * Set the given value for the given key. An old value, if it exists, will be overwritten.
     *
     * @param key   the key
     * @param value the value to set
     */
    void setStringValue(std::string key, std::string value){
        setValue(key, value);
    }

    /**
     * Set the given value for the given key. An old value, if it exists, will be overwritten.
     *
     * @param key   the key
     * @param value the value to set
     */
    void setDoubleValue(std::string key, double value){
        setValue(key, std::to_string(value));
    }

    /**
     * Set the given value for the given key. An old value, if it exists, will be overwritten.
     *
     * @param key   the key
     * @param value the value to set
     */
    void setBooleanValue(std::string key, bool value){
        if (value) setValue(key, "true");
        else setValue(key, "false");
    }

    /**
     * Set the given value for the given key. An old value, if it exists, will be overwritten.
     *
     * @param key   the key
     * @param value the value to set
     */
    void setIntegerValue(std::string key, int value){
        setValue(key, std::to_string(value));
    }

    /**
     * Set the given value for the given key. An old value, if it exists, will be overwritten.
     *
     * @param key   the key
     * @param value the value to set
     */
    void setLongValue(std::string key, long value){
        setValue(key, std::to_string(value));
    }

    /**
     * Get the current contents of the statistic. Changes on the returned map will be reflected in the config and
     * vice versa!
     *
     * @return the statistic data
     */
    std::map <std::string, std::string>* getData(){
        return &data;
    }
};

#endif
