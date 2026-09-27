#ifndef ODPAIR_HPP
#define ODPAIR_HPP

/**
 * Class implementing a od pair, i.e., a triple of origin, destination and value.
 */
#include <string>
#include <cmath>
#include "../io/CsvWriter.hpp"

class ODPair{
private:
    int origin;
    int destination;
    double value = 0;
public:
    /**
     * Create a new od pair with the given attributes.
     * @param origin the origin of the od pair. This is the id of the node in the PTN, where the passengers start.
     * @param destination the destination of the od pair. This is the id of the node in the PTN, where the
     *                    passengers want to arrive at.
     * @param value the value of the od pair, i.e., how many passengers want to travel from origin to destination in
     *              the planning period.
     */
    ODPair(int origin, int destination, double value){
        this->origin = origin;
        this->destination = destination;
        this->value = value;
    }

    /**
     * Get the origin of the od pair. This is the id of the node in the PTN, where the passengers start.
     * @return the id of the origin
     */
    int getOrigin(){
        return this->origin;
    }

    /**
     * Get the destination of the od pair. This is the  id of the node in the PTN, where the passengers want to
     * arrive at.
     * @return the id of the destination
     */
    int getDestination(){
        return this->destination;
    }

    /**
     * Get the value of the od pair, i.e., how many passengers want to travel from origin to destination in the
     * planning period.
     * @return the value of the od pair
     */
    double getValue(){
        return this->value;
    }

    /**
     * Set the value of the od pair, i.e., how many passengers want to travel from origin to destination in the
     * planning period. The old value will be overwritten.
     * @param value the new value of the od pair
     */
    void setValue(double value){
        this->value = value;
    }

    std::string toString(){
        return "OD(" + std::to_string(this->origin) + ", " + std::to_string(this->destination)
               + ", " + std::to_string(this->value) + ")";
    }



    friend bool operator==(ODPair a, ODPair b){

        if(a.getOrigin() != b.getOrigin()) return false;
        if(a.getDestination() != b.getDestination()) return false;
        return (std::abs(a.getValue() - b.getValue()) < 1e-9);
    }

    friend bool operator!=(ODPair a, ODPair b){ return !(a == b); }

    int hashCode(){
        int result = getOrigin();
        result = 31 * result + getDestination();
        return result;
    }

    /**
     * Return a string array, representing the od pair for a LinTim csv file
     * @return the csv representation of this od pair
     */
    std::vector <std::string> toCsvStrings(){
        return {std::to_string(this->origin),
                std::to_string(this->destination),
                CsvWriter::shortenDecimalValueForOutput(this->value)};
    }
};

#endif
