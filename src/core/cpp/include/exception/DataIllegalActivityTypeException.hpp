/**
 * Exception to throw if the type of an activity is undefined.
 */

#ifndef DATAILLEGALACTIVITYTYPEEXCEPTION_HPP
#define DATAILLEGALACTIVITYTYPEEXCEPTION_HPP


class DataIllegalActivityTypeException : public std::runtime_error{
public:
    DataIllegalActivityTypeException(int activityId, std::string activityType) :
        std::runtime_error("Error D5: " + activityType + " of activity " +
                           std::to_string(activityId) + " is no legal activity type."){}
};

#endif
