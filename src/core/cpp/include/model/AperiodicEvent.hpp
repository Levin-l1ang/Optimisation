#ifndef APERIODICEVENT_HPP
#define APERIODICEVENT_HPP

/**
 * A class representing an aperiodic event, i.e., a node in the aperiodic event activity network (EAN).
 */
#include "Node.hpp"
#include "EventType.hpp"
#include "../io/CsvWriter.hpp"
#include <cmath>

class AperiodicEvent : public Node{

private:
    int eventId;
    int periodicEventId;
    int stopId;
    EventType type;
    int time;
    double numberOfPassengers;


public:

    /**
     * Creates an aperiodic event, i.e., a node in the aperiodic event activity network.
     *
     * @param eventId            event id
     * @param periodicEventId    id of the corresponding periodic event
     * @param stopId             id of the corresponding stop
     * @param type               type of the event
     * @param time               periodic time at which the event takes place
     * @param numberOfPassengers number of passengers using the event //TODO Check if this is correct
     */
    AperiodicEvent(int eventId, int periodicEventId, int stopId, EventType type, int time, double
    numberOfPassengers){
        this->eventId = eventId;
        this->periodicEventId = periodicEventId;
        this->stopId = stopId;
        this->type = type;
        this->time = time;
        this->numberOfPassengers = numberOfPassengers;
    }


    int getId(){
        return this->eventId;
    }

    void setId(int id){
        this->eventId = id;
    }

    /**
     * Get the id of the corresponding periodic event
     *
     * @return the id of the corresponding periodic event
     */
    int getPeriodicEventId(){
        return this->periodicEventId;
    }

    /**
     * Get the id of the corresponding stop.
     *
     * @return the id of the corresponding stop
     */
    int getStopId(){
        return this->stopId;
    }

    /**
     * Get the type of the event, which is specified in EventType.
     *
     * @return the type of the event
     */
    EventType getType(){
        return this->type;
    }

    /**
     * Get the periodic time at which the event takes place.
     *
     * @return the time of the event
     */
    int getTime(){
        return this->time;
    }

    /**
     * Set the time of the event.
     *
     * @param time the time of the event
     */
    void setTime(int time){
        this->time = time;
    }

    /**
     * Get the number of passengers using the event.
     * //TODO check correctness
     *
     * @return the number of passengers using the event
     */
    double getNumberOfPassengers(){
        return this->numberOfPassengers;
    }


    friend bool operator==(AperiodicEvent a, AperiodicEvent b){
        if(a.getId() != b.getId()) return false;
        if(a.getPeriodicEventId() != b.getPeriodicEventId()) return false;
        if(a.getStopId() != b.getStopId()) return false;
        if(a.getTime() != b.getTime()) return false;
        if(abs(a.getNumberOfPassengers() - b.getNumberOfPassengers()) > 1e-9) return false;
        return true;
    }

    friend bool operator!=(AperiodicEvent a, AperiodicEvent b){
        return !(a == b);
    }

    friend bool operator<(AperiodicEvent a, AperiodicEvent b){
        return a.getId() < b.getId();
    }

    std::vector <std::string> toCsvStrings(){
        return {
            std::to_string(getId()),
            std::to_string(getPeriodicEventId()),
            std::to_string(getType()),
            std::to_string(getTime()),
            CsvWriter::shortenDecimalValueForOutput(getNumberOfPassengers()),
            std::to_string(getStopId())};

    }
    // TODO HashCode



};

#endif
