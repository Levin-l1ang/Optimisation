/**
 * Exception to throw if the type of an activity is undefined.
 */

#ifndef DATAILLEGALEVENTTYPEEXCEPTION_HPP
#define DATAILLEGALEVENTTYPEEXCEPTION_HPP


class DataIllegalEventTypeException : public std::runtime_error{
public:
    DataIllegalEventTypeException(int eventId, std::string eventType) :
        std::runtime_error("Error D4: " + eventType + " of event " + std::to_string(eventId)
                           + " is no legal event type."){}
};

#endif
