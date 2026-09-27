#ifndef OUTPUTFILEEXCEPTION_HPP
#define OUTPUTFILEEXCEPTION_HPP


/**
 * Exception to throw if an output file cannot by written.
 */
class OutputFileException : public std::runtime_error{
public:
    /**
     * Exception to throw if an output file cannot be written.
     *
     * @param fileName name of the output file
     */
    OutputFileException(std::string fileName) :
        std::runtime_error("Error O1: File " + fileName + " cannot be written."){}

};

#endif
