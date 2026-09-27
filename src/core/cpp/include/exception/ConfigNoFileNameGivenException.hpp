/**
 * Exception to throw if no config file name is given
 */

#ifndef CPP_CONFIGNOFILENAMEGIVENEXCEPTION_H
#define CPP_CONFIGNOFILENAMEGIVENEXCEPTION_H

#include <stdexcept>

class ConfigNoFileNameGivenException : public std::runtime_error{
public:
	/**
   * Exception to throw if no config file can be found
   *
   */
	ConfigNoFileNameGivenException() : std::runtime_error("Error C4: No config file name given."){}

};

#endif //CPP_CONFIGNOFILENAMEGIVENEXCEPTION_H
