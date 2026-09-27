/**
 * Exception to throw if the input file has a type inconsistent.
 */
#ifndef INPUTTYPEINCONSISTENCYEXCEPTION_HPP
#define INPUTTYPEINCONSISTENCYEXCEPTION_HPP

#include <stdexcept>
#include <string>
#include <exception>

class InputTypeInconsistencyException : public std::runtime_error{
public:
    InputTypeInconsistencyException(std::string fileName, int columnNumber, int lineNumber, std::string type, std::string entry) : std::runtime_error(
        "Error I3: Column " + std::to_string(columnNumber) + " of file " + fileName + " should be of type " + type + " but entry in line " +
        std::to_string(lineNumber) + " is " + entry + "."){}
};

#endif
