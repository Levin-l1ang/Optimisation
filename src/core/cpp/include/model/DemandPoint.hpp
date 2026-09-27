#ifndef DEMANDPOINT_HPP
#define DEMANDPOINT_HPP

/**
 * A class to represent demand point, i.e., the location and the number of people in a city that serves
 * as demand.
 */
#include <string>

class DemandPoint{
private:
    int id;
    std::string shortName;
    std::string longName;
    double xCoordinate;
    double yCoordinate;
    int demand;

    /**
     * Constructor of a demand point.
     *
     * @param id          id of the demand point
     * @param shortName   short name of the demand point
     * @param longName    long name of the demand point
     * @param xCoordinate x coordinate of the demand point
     * @param yCoordinate y coordinate of the demand point
     * @param demand      demand at the demand point
     */
public:
    DemandPoint(int id, std::string shortName, std::string longName, double xCoordinate, double yCoordinate, int demand){
        this->id = id;
        this->shortName = shortName;
        this->longName = longName;
        this->xCoordinate = xCoordinate;
        this->yCoordinate = yCoordinate;
        this->demand = demand;
    }

    /**
     * Gets the id of the demand point.
     *
     * @return id of the demand point
     */
    int getId(){
        return this->id;
    }

    /**
     * Gets the short name of the demand point.
     *
     * @return the short name of the demand point
     */
    std::string getShortName(){
        return this->shortName;
    }

    /**
     * Gets the long name of the demand point.
     *
     * @return the long name of the demand point
     */
    std::string getLongName(){
        return this->longName;
    }

    /**
     * Gets the x coordinate of the demand point.
     *
     * @return the x coordinate of the demand point.
     */
    double getxCoordinate(){
        return this->xCoordinate;
    }

    /**
     * Gets the y coordinate of the demand point.
     *
     * @return the y coordinate of the demand point
     */
    double getyCoordinate(){
        return this->yCoordinate;
    }

    /**
     * Gets the demand at the demand point.
     *
     * @return the demand
     */
    int getDemand(){
        return this->demand;
    }

    friend bool operator==(DemandPoint a, DemandPoint b){
        if(a.getId() != b.getId()) return false;
        if(abs(a.getxCoordinate() - b.getxCoordinate()) > 1e-9) return false;
        if(abs(a.getyCoordinate() - b.getyCoordinate()) > 1e-9) return false;
        if(a.getDemand() != b.getDemand()) return false;
        if(a.getShortName() != b.getShortName()) return false;
        if(a.getLongName() != b.getLongName()) return false;
        return true;
    }

    friend bool operator!=(DemandPoint a, DemandPoint b){ return !(a == b); }
};

#endif
