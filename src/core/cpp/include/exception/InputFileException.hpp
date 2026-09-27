/**
 * Exception to be thrown if an input file cannot be found.
 */
#ifndef INPUTFILEEXCEPTION_HPP
#define INPUTFILEEXCEPTION_HPP

class InputFileException : public std::runtime_error{
public:
    /**
     * Exception to throw if an input file cannot be found.
     *
     * @param fileName name of the file to open
     */
    InputFileException(std::string fileName) :
        std::runtime_error("Error I1: File " + fileName + " cannot be found."){}

};

#endif
