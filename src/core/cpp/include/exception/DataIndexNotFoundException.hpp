
/**
 * Exception to throw if an element with a specific index is not found.
 */

#ifndef DATAINDEXNOTFOUNDEXCEPTION_HPP
#define DATAINDEXNOTFOUNDEXCEPTION_HPP


class DataIndexNotFoundException : public std::runtime_error{
public:
    DataIndexNotFoundException(std::string element, int index) :
        std::runtime_error("Error D3: " + element + " with index " + std::to_string(index) + " not found."){}
};

#endif
