/**
 * Exception to throw if the input file is not formatted correctly.
 */

#ifndef INPUTFORMATEXCEPTION_HPP
#define INPUTFORMATEXCEPTION_HPP

class InputFormatException : public std::runtime_error{
public:
    InputFormatException(std::string fileName, int columnsGiven, int columnsRequired) :
        std::runtime_error("Error I2: File " + fileName + " is not formatted correctly: " + std::to_string(columnsGiven) + " columns given, " +
                           std::to_string(columnsRequired) + " needed."){}
};

#endif
