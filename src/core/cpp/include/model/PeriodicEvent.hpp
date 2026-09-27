#ifndef PERIODICEVENT_HPP
#define PERIODICEVENT_HPP


#include "Node.hpp"
#include "EventType.hpp"
#include "LineDirection.hpp"
#include "../io/CsvWriter.hpp"
#include <cmath>
#include <vector>
#include <string>

/**
 * A class representing a periodic event, i.e., a node in the periodic event activity network (EAN).
 */
class PeriodicEvent : public Node{
protected:
    int eventId;
    int stopId;
    EventType type;
    int lineId;
    int time;
    LineDirection ldir;
    double numberOfPassengers;
    int lineFrequencyRepetition;
public:
    /**
     * Creates a periodic event, i.e., a node in the periodic event activity network.
     *
     * @param eventId            event id
     * @param stopId             id of the corresponding stop
     * @param type               type of the event
     * @param lineId             id of the corresponding line
     * @param time               periodic time at which the event takes place
     * @param numberOfPassengers number of passengers using the event //TODO Check if this is correct
     */
    PeriodicEvent(int eventId, int stopId, EventType type, int lineId, int time,
        double numberOfPassengers, LineDirection dir, int lfr){
        this->eventId = eventId;
        this->stopId = stopId;
        this->type = type;
        this->lineId = lineId;
        this->time = time;
        this->numberOfPassengers = numberOfPassengers;
        this->ldir = dir;
        this->lineFrequencyRepetition = lfr;
    }

    int getId(void){
        return this->eventId;
    }

    void setId(int id){
        this->eventId = id;
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
     * Get the id of the corresponding line.
     *
     * @return the corresponding line id
     */
    int getLineId(){
        return this->lineId;
    }

    /**
     * Get the periodic time at which the event takes place.
     *
     * @return the periodic time of the event
     */
    int getTime(){
        return this->time;
    }

    /**
     * Set the time of the event.
     *
     * @param time the periodic time of the event
     */
    void setTime(int time){
        this->time = time;
    }

    /**
     *
     * @return direction of the line
     */

    LineDirection getLineDirection(){
        return this->ldir;
    }

    /**
     * sets linedirection to ld
     * @param ld
     */
    void setLineDirection(LineDirection ld){
        this->ldir = ld;
    }

    /**
     * getter for lineFrequencyRepetition
     * @return lineFrequencyRepetition
     */

    int getLineFrequencyRepetition(){
        return this->lineFrequencyRepetition;
    }

    /**
     *  sets lfr
     * @param i value to be set
     */

    void setLineFrequencyRepetition(int i){
        this->lineFrequencyRepetition = i;
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

    friend bool operator==(PeriodicEvent a, PeriodicEvent b){
        if(a.getId() != b.getId()) return false;
        if(a.getStopId() != b.getStopId()) return false;
        if(a.getLineId() != b.getLineId()) return false;
        if(a.getTime() != b.getTime()) return false;
        if(abs(a.getNumberOfPassengers() - b.getNumberOfPassengers()) > 1e-9) return false;
        return true;
    }

    friend bool operator!=(PeriodicEvent a, PeriodicEvent b){
        return !(a == b);
    }

    friend bool operator<(PeriodicEvent a, PeriodicEvent b){
        return a.getId() < b.getId();
    }

    std::vector<std::string> toCsvStrings(){
        std::string dir;
        if (ldir == FORWARDS) dir = ">";
        else dir = "<";
        return {
            std::to_string(getId()),
            std::to_string(getType()),
            std::to_string(getStopId()),
            std::to_string(getLineId()),
            CsvWriter::shortenDecimalValueForOutput(getNumberOfPassengers()),
            dir,
            std::to_string(lineFrequencyRepetition)
        };
    }

    std::vector<std::string> toCsvTimetableStrings(){
        return {
            std::to_string(getId()),
            std::to_string(getTime())
        };
    }

    // TODO HashCode

};

#endif
