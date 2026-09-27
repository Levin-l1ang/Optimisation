/**
 * Exception to throw if the type of an line direction is undefined.
 */

#ifndef DATAILLEGALLINEDIRECTIONEXCEPTION_HPP
#define DATAILLEGALLINEDIRECTIONEXCEPTION_HPP
#include <string>


class DataIllegalLineDirectionException : public std::runtime_error{
public:
    DataIllegalLineDirectionException(int eventId, std::string LineDirection) :
        std::runtime_error("Error D6: " + LineDirection + " of event " + std::to_string(eventId)
                           + " is no legal line direction."){}
};

#endif
