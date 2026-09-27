/**
 */
#ifndef STATISTICKEYNOTFOUNDEXCEPTION_HPP
#define STATISTICKEYNOTFOUNDEXCEPTION_HPP

class StatisticKeyNotFoundException : public std::runtime_error{
public:
    StatisticKeyNotFoundException(std::string key) :
        std::runtime_error("Error ST2: Statistic parameter " + key + " does not exist."){}

};

#endif
