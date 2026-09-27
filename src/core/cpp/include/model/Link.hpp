/**
 * A class representing an edge in a public transportation network (PTN).
 *
 * This class will contain all information that normally associated with a PTN edge in the LinTim context, i.e.,
 * structural information as well as some passenger data.
 */
#ifndef LINK_HPP
#define LINK_HPP

#include "Stop.hpp"
#include "Edge.hpp"
#include "../io/CsvWriter.hpp"
#include <string>
#include <vector>

class Link : public Edge<Stop>{
private:
    int linkId;
    Stop* leftStop;
    Stop* rightStop;
    double length;
    int lowerBound;
    int upperBound;
    double load;
    int lowerFrequencyBound;
    int upperFrequencyBound;
    int headway;
    bool directed;


public:
    /**
     * Create a new Link, i.e., an edge in a Public Transportation Network.
     * @param linkId the id of the link, i.e., the id to reference the link. Needs to be unique for a given graph
     * @param leftStop the left stop of the edge. This is the source of the link, if the edge is directed
     * @param rightStop the right stop of the edge. This is the target of the link if the edge is directed
     * @param length the length of the link, given in kilometers TODO: Unify measurements
     * @param lowerBound the lowerBound of the link, i.e., the minimal time in minutes, a vehicle needs to
     *                      traverse the edge TODO: Unify measurements
     * @param upperBound the upperBound of the link, i.e., the maxmimaö time in minutes, a vehicle needs to
     *                      traverse the edge TODO: Unify measurements
     * @param directed whether the link is directed
     */
    Link(int linkId, Stop* leftStop, Stop* rightStop, double length, int lowerBound, int upperBound, bool
    directed){
        this->linkId = linkId;
        this->leftStop = leftStop;
        this->rightStop = rightStop;
        this->length = length;
        this->lowerBound = lowerBound;
        this->upperBound = upperBound;
        this->directed = directed;
        this->headway = 0;
    }


    int getId(){
        return linkId;
    }

    void setId(int id){
        this->linkId = id;
    }


    Stop* getLeftNode(){
        return this->leftStop;
    }


    Stop* getRightNode(){
        return this->rightStop;
    }


    bool isDirected(){
        return directed;
    }

    /**
	 * Get the length of the link, given in kilometers.
	 * @return the length
	 */
    double getLength(){
        return length;
    }

    /**
	 * Get the lowerBound of the link, i.e., the minimal time in minutes, a vehicle needs to  traverse the edge
	 * TODO: Unify measurements
	 * @return the lower bound
	 */
    int getLowerBound(){
        return lowerBound;
    }

    /**
	 * Get the upperBound of the link, i.e., the maxmimaö time in minutes, a vehicle needs to traverse the edge
	 * TODO: Unify measurements
	 * @return the upper bound
	 */
    int getUpperBound(){
        return upperBound;
    }

    /**
	 * Get the load of the link, i.e., how many passengers traverse this link in the given period
	 * @return the load
	 */
    double getLoad(){
        return load;
    }


    /**
	 * Set the load of the link, i.e., how many passengers traverse this link in the given period
	 * @param load the new load
	 */
    void setLoad(double load){
        this->load = load;
    }

    /**
	 * Get the lower frequency bound on the link, i.e., the minimal number of times a vehicle needs to traverse this
	 * link in a given period to serve the load
	 * @return the lower frequency bound
	 */
    int getLowerFrequencyBound(){
        return lowerFrequencyBound;
    }

    /**
	 * Set the lower frequency bound on the link, i.e., the minimal number of times a vehicle needs to traverse this
	 * link in a given period to serve the load
	 * @param lowerFrequencyBound the lower frequency bound
	 */
    void setLowerFrequencyBound(int lowerFrequencyBound){
        this->lowerFrequencyBound = lowerFrequencyBound;
    }

    /**
	 * Get the upper frequency bound on the link, i.e., the maximal number of times a vehicle may traverse this link
	 * in a given period
	 * @return the upper frequency bound
	 */
    int getUpperFrequencyBound(){
        return upperFrequencyBound;
    }

    /**
	 * Set the upper frequency bound on the link, i.e., the maximal number of times a vehicle may traverse this link
	 * in a given period
	 * @param upperFrequencyBound the upper frequency bound
	 */
    void setUpperFrequencyBound(int upperFrequencyBound){
        this->upperFrequencyBound = upperFrequencyBound;
    }

    /**
	 * Set all information regarding the passenger load for the link
	 * @param load the new load of the link, i.e., how many passengers traverse this link in the given period
	 * @param lowerFrequencyBound the lower frequency bound on the link, i.e., the minimal number of times a vehicle
	 *                            needs to traverse this link in a given period to serve the load
	 * @param upperFrequencyBound the upper frequency bound on the link, i.e., the maximal number of times a vehicle
	 *                            may traverse this link in a given period
	 */
    void setLoadInformation(double load, int lowerFrequencyBound, int upperFrequencyBound){
        setLoad(load);
        setLowerFrequencyBound(lowerFrequencyBound);
        setUpperFrequencyBound(upperFrequencyBound);
    }

    int getHeadway(){
        return headway;
    }

    /**
	 * Set the headway of the stop. This is the minimal time needed between two vehicle that serve this stop. Should
	 * be given in minutes //TODO: Unify measurements
	 * @param headway the new headway
	 */
    void setHeadway(int headway){
        this->headway = headway;
    }

    /*
		Returns vector of output data.
	*/

    std::vector <std::string> toCsvStrings(){
        return {
            std::to_string(getId()),
            std::to_string(getLeftNode()->getId()),
            std::to_string(getRightNode()->getId()),
            CsvWriter::shortenDecimalValueForOutput(getLength()),
            std::to_string(getLowerBound()),
            std::to_string(getUpperBound())
        };
    }

    std::vector <std::string> toCsvLoadStrings(){
        return {
            std::to_string(getId()),
            CsvWriter::shortenDecimalValueForOutput(getLoad()),
            std::to_string(getLowerFrequencyBound()),
            std::to_string(getUpperFrequencyBound())
        };
    }

    /**
	 * Return a string array, representing the link for a LinTim csv headway file
	 * @return the csv headway representation of this link
	 */
    std::vector <std::string> toCsvHeadwayStrings(){
        return {
            std::to_string(getId()),
            std::to_string(getHeadway())
        };
    }

    friend bool operator==(Link& a, Link& b){
        if(a.getId() == b.getId()) return true;
        else return false;
    }

    friend bool operator!=(Link& a, Link& b){
        return !(a == b);
    }

    friend bool operator<(Link a, Link b){
        return a.getId() < b.getId();
    }

    friend bool operator>(Link& a, Link& b){
        return !(a < b);
    }


};

#endif
