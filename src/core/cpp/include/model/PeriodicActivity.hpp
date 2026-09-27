#ifndef PERIODICACTIVITY_HPP
#define PERIODICACTIVITY_HPP

#include "Edge.hpp"
#include "PeriodicEvent.hpp"
#include "ActivityType.hpp"
#include "../io/CsvWriter.hpp"


/**
 * A class representing a periodic activity, i.e., an arc in the periodic event activity network (EAN).
 */
class PeriodicActivity : public Edge<PeriodicEvent>{
protected:
    int activityId;
    ActivityType type;
    PeriodicEvent* sourceEvent;
    PeriodicEvent* targetEvent;
    int lowerBound;
    int upperBound;
    double numberOfPassengers;
public:
    PeriodicActivity(){}
    /**
     * Create a new activity, i.e., an arc in the event activity network.
     *
     * @param activityId         id of the activity
     * @param type               type of the activity
     * @param sourceEvent        source event
     * @param targetEvent        target event
     * @param lowerBound         lower bound on the time the activity is allowed to take
     * @param upperBound         upper bound on the time the activity is allowed to take
     * @param numberOfPassengers number of passengers using the activity
     */
    PeriodicActivity(int activityId, ActivityType type, PeriodicEvent* sourceEvent,
         PeriodicEvent* targetEvent, int lowerBound, int upperBound, double numberOfPassengers){
        this->activityId = activityId;
        this->type = type;
        this->sourceEvent = sourceEvent;
        this->targetEvent = targetEvent;
        this->lowerBound = lowerBound;
        this->upperBound = upperBound;
        this->numberOfPassengers = numberOfPassengers;
    }

    int getId(){
        return this->activityId;
    }

    void setId(int id){
        this->activityId = id;
    }


    PeriodicEvent* getLeftNode(){
        return this->sourceEvent;
    }

    PeriodicEvent* getRightNode(){
        return this->targetEvent;
    }

    bool isDirected(){
        // An activity is always directed
        return true;
    }

    /**
     * Get the type of an activity, which is specified in ActivityType.
     *
     * @return the type of the activity
     */
    ActivityType getType(){
        return this->type;
    }

    /**
     * Get the lower bound of the time the activity is allowed to take.
     *
     * @return the lower bound of the activity
     */
    int getLowerBound(){
        return this->lowerBound;
    }

    /**
     * Get the upper bound of the time the activity is allowed to take.
     *
     * @return the upper bound of the activity
     */
    int getUpperBound(){
        return this->upperBound;
    }

    /**
     * Get the number of passengers using the activity.
     *
     * @return the number of passengers using the activity
     */
    double getNumberOfPassengers(){
        return this->numberOfPassengers;
    }

    /**
     * Check whether the periodic duration of the activity, i.e., the difference between its start and end time mod
     * periodLength, is feasible,
     * i.e., between the lower and the upper bound of the activity.
     *
     * @param periodLength the length of the period
     * @return whether the duration of the activity is feasible
     */
    bool checkFeasibilityDuration(int periodLength){
        int startTime = this->getLeftNode()->getTime();
        int endTime = this->getRightNode()->getTime();
        return ((endTime - startTime) % periodLength + periodLength) % periodLength >= this->getLowerBound()
               && ((endTime - startTime) % periodLength + periodLength) % periodLength <= this->getUpperBound();
    }

    friend bool operator==(PeriodicActivity& a, PeriodicActivity& b){
        if(a.getId() != b.getId()) return false;
        if(a.getLowerBound() != b.getLowerBound()) return false;
        if(a.getUpperBound() != b.getUpperBound()) return false;
        if(abs(a.getNumberOfPassengers() - b.getNumberOfPassengers()) > 1e-9) return false;
        if(a.getType() != b.getType()) return false;
        if(a.getLeftNode() != b.getLeftNode()) return false;
        return (a.getRightNode() == b.getRightNode());
    }

    friend bool operator!=(PeriodicActivity& a, PeriodicActivity& b){
        return !(a == b);
    }

    friend bool operator<(PeriodicActivity a, PeriodicActivity b){
        return a.getId() < b.getId();
    }

    std::vector<std::string> toCsvStrings(){
        return {
            std::to_string(getId()),
            std::to_string(getType()),
            std::to_string(getLeftNode()->getId()),
            std::to_string(getRightNode()->getId()),
            std::to_string(getLowerBound()),
            std::to_string(getUpperBound()),
            CsvWriter::shortenDecimalValueForOutput(getNumberOfPassengers())
        };
    }


    // TODO HashCode
};

#endif
