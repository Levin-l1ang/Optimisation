//
//

#ifndef CPP_ALGORITHMSTOPPINGCRITERIONEXCEPTION_HPP
#define CPP_ALGORITHMSTOPPINGCRITERIONEXCEPTION_HPP

#include <stdexcept>

class AlgorithmStoppingCriterionException : public std::runtime_error{
public:
    /**
   * Exception to throw if no config file can be found
   *
   */
    AlgorithmStoppingCriterionException(std::string algorithm) :
        std::runtime_error("Error A1: Stopping criterion of algorithm " + algorithm + " reached without finding a feasible/optimal solution."){}

};

#endif //CPP_ALGORITHMSTOPPINGCRITERIONEXCEPTION_HPP
