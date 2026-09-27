/**
 * Exception to throw if a config key cannot be found.
 */

#ifndef CONFIGKEYNOTFOUNDEXCEPTION_HPP
#define CONFIGKEYNOTFOUNDEXCEPTION_HPP

class ConfigKeyNotFoundException : public std::runtime_error{
public:
    /**
   * Exception to throw if a config key cannot be found.
   *
   * @param configKey the required key
   */
    ConfigKeyNotFoundException(std::string configKey) : std::runtime_error("Error C2: Config parameter " + configKey + " does not exist."){}

};


#endif
