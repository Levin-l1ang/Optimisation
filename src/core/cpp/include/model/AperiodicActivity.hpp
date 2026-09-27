#ifndef APERIODICACTIVITY_HPP
#define APERIODICACTIVITY_HPP
/**
 * A class representing an aperiodic activity, i.e., an arc in the aperiodic event activity network (EAN).
 */
#include "Edge.hpp"
#include "AperiodicEvent.hpp"
#include "ActivityType.hpp"
#include "../io/CsvWriter.hpp"

class AperiodicActivity : public Edge<AperiodicEvent>{
private:
    int activityId;
    int periodicActivityId;
    ActivityType type;
    AperiodicEvent* sourceEvent;
    AperiodicEvent* targetEvent;
    int lowerBound;
    int upperBound;
    double numberOfPassengers;


public:

    /**
     * Create a new aperiodic activity, i.e., an arc in the aperiodic event activity network.
     *
     * @param activityId         id of the activity
     * @param periodicActivityId id of the corresponding periodic activity
     * @param type               type of the activity
     * @param sourceEvent        source event
     * @param targetEvent        target event
     * @param lowerBound         lower bound on the time the activity is allowed to take
     * @param upperBound         upper bound on the time the activity is allowed to take
     * @param numberOfPassengers number of passengers using the activity
     */
    AperiodicActivity(int activityId, int periodicActivityId, ActivityType type, AperiodicEvent* sourceEvent,
                      AperiodicEvent* targetEvent, int lowerBound, int upperBound, double numberOfPassengers){
        this->activityId = activityId;
        this->periodicActivityId = periodicActivityId;
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

    AperiodicEvent* getLeftNode(){
        return this->sourceEvent;
    }

    AperiodicEvent* getRightNode(){
        return this->targetEvent;
    }

    bool isDirected(){
        // An activity is always directed.
        return true;
    }

    /**
     * Get the id of the corresponding periodic activity.
     *
     * @return the id of the corresponding activity
     */
    int getPeriodicActivityId(){
        return this->periodicActivityId;
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
     * Check whether the duration of the activity, i.e., the difference between its start and end time, is feasible,
     * i.e., between the lower and the upper bound of the activity.
     *
     * @return whether the duration of the activity is feasible
     */
    bool checkFeasibilityDuration(){
        int startTime = this->getLeftNode()->getTime();
        int endTime = this->getRightNode()->getTime();
        return endTime - startTime >= this->getLowerBound() && endTime - startTime <= this->getUpperBound();
    }


    friend bool operator==(AperiodicActivity& a, AperiodicActivity& b){

        if(a.getId() != b.getId()) return false;
        if(a.getPeriodicActivityId() != b.getPeriodicActivityId()) return false;
        if(a.getLowerBound() != b.getLowerBound()) return false;
        if(a.getUpperBound() != b.getUpperBound()) return false;
        if(abs(a.getNumberOfPassengers() - b.getNumberOfPassengers()) > 1e-9) return false;
        if(a.getType() != b.getType()) return false;
        if(a.getLeftNode() != b.getLeftNode()) return false;
        return (a.getRightNode() == b.getRightNode());
    }

    friend bool operator!=(AperiodicActivity& a, AperiodicActivity& b){
        return !(a == b);
    }

    friend bool operator<(AperiodicActivity a, AperiodicActivity b){
        return a.getId() < b.getId();
    }

    std::vector <std::string> toCsvStrings(){
        return {
            std::to_string(getId()),
            std::to_string(getPeriodicActivityId()),
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
