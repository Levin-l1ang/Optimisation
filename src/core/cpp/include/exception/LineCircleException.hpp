/**
 * Exception to throw if a line contains a circle.
 */
#ifndef LINECIRCLEEXCEPTION_HPP
#define LINECIRCLEEXCEPTION_HPP

#include <stdexcept>
#include <string>
#include <exception>

class LineCircleException : public std::runtime_error{
public:
    LineCircleException(int id) : std::runtime_error("Error L2: Line " + std::to_string(id) + "contains a circle.\n"){}
};

#endif
