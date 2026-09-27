/**
 * Exception to throw if an element with a specific index is not found.
 */

#ifndef INDEXOUTOFBOUNDSEXCEPTION_HPP
#define INDEXOUTOFBOUNDSEXCEPTION_HPP

 class IndexOutOfBoundsException : public std::runtime_error{
 public:
   IndexOutOfBoundsException(int index, int max_size) :
    std::runtime_error("Tried to access index " + std::to_string(index) +
     ". But container has only size" + std::to_string(max_size) + " not found." ){}
 };
#endif
