/**
 */
#ifndef STATISTICTYPEMISMATCHEXCEPTION_HPP
#define STATISTICTYPEMISMATCHEXCEPTION_HPP

class StatisticTypeMismatchException : public std::runtime_error{
public:
    StatisticTypeMismatchException(std::string key, std::string type, std::string value) :
        std::runtime_error("Error ST1: Statistic key " + key + " should have type " + type +
                           " but has value " + value + "."){}
};

#endif
