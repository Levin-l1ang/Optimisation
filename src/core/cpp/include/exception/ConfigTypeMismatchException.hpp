/**
 * Exception to throw if the type of the config parameter does not match.
 */
#ifndef CONFIGTYPEMISMATCHEXCEPTION_HPP
#define CONFIGTYPEMISMATCHEXCEPTION_HPP

class ConfigTypeMismatchException : public std::runtime_error{
public:
    /**
	 * Exception to throw if the type of the config parameter does not match.
	 *
	 * @param configKey       config key
	 * @param type            expected type
	 * @param configParameter config parameter
	 */
    ConfigTypeMismatchException(std::string configKey, std::string type, std::string configParameter) :
        std::runtime_error("Error C3: Config parameter " + configKey + " should be of type " + type + " but is " + configParameter
                           + "."){}
};

#endif
