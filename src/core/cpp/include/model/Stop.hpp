/*
 * Template implementation of a stop in a PTN.
 */
#ifndef STOP_HPP
#define STOP_HPP

#include "Node.hpp"
#include <string>
#include <vector>

;

class Stop : public Node{
public:
    Stop(int stopId, std::string shortName, std::string longName, double xCoordinate, double yCoordinate){
        this->stopId = stopId;
        this->shortName = shortName;
        this->longName = longName;
        this->xCoordinate = xCoordinate;
        this->yCoordinate = yCoordinate;
        this->station = true;
    }

    int getId(){
        return stopId;
    }

    void setId(int id){
        this->stopId = id;
    }

    std::string getShortName(){
        return shortName;
    }

    std::string getLongName(){
        return longName;
    }

    double getxCoordinate(){
        return xCoordinate;
    }

    double getyCoordinate(){
        return yCoordinate;
    }

    bool isStation(){
        return station;
    }

    void setStation(bool isStation){
        this->station = isStation;
    }

    friend bool operator==(Stop& a, Stop& b){
        if(a.getId() == b.getId()) return true;
        else return false;
    }

    friend bool operator!=(Stop& a, Stop& b){
        return !(a == b);
    }

    friend bool operator<(Stop a, Stop b){
        return a.getId() < b.getId();
    }

    friend bool operator>(Stop& a, Stop& b){
        return !(a < b);
    }


    std::vector <std::string> toCsvStrings(){
        return {
            std::to_string(this->stopId),
            this->shortName,
            this->longName,
            std::to_string(this->xCoordinate),
            std::to_string(this->yCoordinate)
        };
    }

private:
    int stopId;
    std::string shortName;
    std::string longName;
    double xCoordinate;
    double yCoordinate;
    bool station;
};

#endif
