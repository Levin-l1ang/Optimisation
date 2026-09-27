//
//

#ifndef CPP_ALGORITHMINFEASIBLEPARAMETERSETTINGEXCEPTION_HPP
#define CPP_ALGORITHMINFEASIBLEPARAMETERSETTINGEXCEPTION_HPP

#include <stdexcept>

class AlgorithmInfeasibleParameterSettingException : public std::runtime_error{
public:
	/**
   * Exception to throw if no config file can be found
   *
   */
	AlgorithmInfeasibleParameterSettingException(std::string algorithm, std::string configKey, std::string configParameter) :
		std::runtime_error("Error A2: Algorithm " + algorithm + " cannot be run with parameter setting " + configKey + "; " + configParameter + "."){}

};

#endif //CPP_ALGORITHMINFEASIBLEPARAMETERSETTINGEXCEPTION_HPP
