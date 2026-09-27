#ifndef LINE_HPP
#define LINE_HPP
/**
 * A class for representing a line as path of Stop and Link .
 *
 */
#include "../model/impl/DoubleLinkedListPath.hpp"
#include "Stop.hpp"
#include "Link.hpp"
#include "../exception/exceptions.hpp"
#include "../io/CsvWriter.hpp"

class Line{
private:
    int id;
    double length;
    double cost;
    int frequency;
    DoubleLinkedListPath<Stop, Link> linePath = 0;

    /**
	 * Private function to add a new link to a line.
	 *
	 * @param link                 link to add
	 * @param computeCostAndLength whether the cost and length of the line should be adapted
	 * @param factorCostLength     factor of the cost depending on the length of the line
	 * @param factorCostLink       factor of the cost depending on the number of links in the line
	 * @return whether the link could be added to the line
	 * @throws LineCircleException if the new link forms a circle in the line
	 */
    bool addLink(Link link, bool computeCostAndLength, double factorCostLength, double factorCostLink){
        if(linePath.containsNode(link.getLeftNode()->getId()) &&
           linePath.containsNode(link.getRightNode()->getId())){
            std::cout << "Line " << getId() << " now contains a loop, closed by link " << link.getId()
                      << ". This may create problems in the LinTim algorithms!" << std::endl;
        }
        bool returnValue = linePath.addLast(link);
        if(computeCostAndLength && returnValue){
            length += link.getLength();
            cost += link.getLength() * factorCostLength + factorCostLink;
        }
        return returnValue;
    }

public:
    /**
     * Constructor for a new, empty line.
     *
     * @param id       id of the line
     * @param directed whether the links are directed
     */
    Line(int id, bool directed){
        this->id = id;
        this->length = 0;
        this->cost = 0;
        this->frequency = 0;
        this->linePath = DoubleLinkedListPath<Stop, Link>(directed);
    }

    /**
     * Constructor for a new line with fixed costs. Use this constructor to create a line and compute its length
     * during the process.
     *
     * @param id        line id
     * @param fixedCost fixed costs of creating a line
     * @param directed  whether the links are directed
     */
    Line(int id, double fixedCost, bool directed){
        this->id = id;
        this->cost = fixedCost;
        this->linePath = DoubleLinkedListPath<Stop, Link>(directed);
    }

    /**
     * Constructor for a line with given information.
     *
     * @param id        line id
     * @param length    length of the line
     * @param cost      cost of the line
     * @param frequency frequency of the line
     * @param linePath  line path belonging to the line
     */
    Line(int id, double length, double cost, int frequency, DoubleLinkedListPath<Stop, Link> linePath){
        this->id = id;
        this->length = length;
        this->cost = cost;
        this->frequency = frequency;
        this->linePath = linePath;
    }


    /**
     * Method to add a new link to the line without changing the length or the cost of the line.
     *
     * @param link link to add
     * @return whether the link could be added to the line
     * @throws LineCircleException if the new link forms a circle in the line
     */
    bool addLink(Link link){
        return addLink(link, false, 0, 0);
    }

    /**
     * Method to add a new link to the line and factorCostLink and factorCostLength*length to the line cost.
     *
     * @param link             link to add
     * @param factorCostLength factor of the cost depending on the length of the line
     * @param factorCostLink   factor of the cost depending on the number of links in the line
     * @return whether the link could be added to the line
     * @throws LineCircleException if the new link forms a circle in the line
     */
    bool addLink(Link link, double factorCostLength, double factorCostLink){
        return addLink(link, true, factorCostLength, factorCostLink);
    }

    /**
     * Gets the id of the line
     *
     * @return line id
     */
    int getId(){
        return this->id;
    }

    /**
     * Gets the length of the line.
     *
     * @return length of the line
     */
    double getLength(){
        return this->length;
    }

    /**
     * Gets the cost of the line.
     *
     * @return line cost
     */
    double getCost(){
        return cost;
    }

    /**
     * Gets the frequency of the line.
     *
     * @return frequency of the line
     */
    int getFrequency(){
        return frequency;
    }

    /**
     * Gets the path belonging to the line.
     *
     * @return the path belonging to the line
     */
    DoubleLinkedListPath<Stop, Link> getLinePath(){
        return linePath;
    }

    /**
     * Sets the frequency of the line.
     *
     * @param frequency line frequency
     */
    void setFrequency(int frequency){
        this->frequency = frequency;
    }

    /**
     * Sets the length of the line.
     *
     * @param length length of the line
     */
    void setLength(double length){
        this->length = length;
    }

    /**
     * Sets the cost of a line
     *
     * @param cost cost of the line
     */
    void setCost(double cost){
        this->cost = cost;
    }

    std::vector <std::string> toLineCostCsvStrings(){
        return {
            std::to_string(getId()),
            CsvWriter::shortenDecimalValueForOutput(getLength()),
            CsvWriter::shortenDecimalValueForOutput(getCost())
        };
    }

    friend bool operator<(Line a, Line b){
        return a.getId() < b.getId();
    }
};

#endif
