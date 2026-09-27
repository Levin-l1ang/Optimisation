#ifndef PTNREADER_HPP
#define PTNREADER_HPP

#include "../exception/exceptions.hpp"
#include "../model/impl/AdjacencyListGraph.hpp"
#include "../model/Link.hpp"
#include "../model/Stop.hpp"
#include "CsvReader.hpp"
#include <string>
#include <vector>
#include <iostream>


/**
 * Class containing all methods to read a PTN, i.e., methods for reading stops and links.
 */


class PTNReader{
private:

    std::string linkFileName;
    std::string stopFileName;
    std::string loadFileName;
    bool directed;
    AdjacencyListGraph<Stop, Link>* ptn;
public:
    /**
     * Initialize a new PTN reader with the source files that should be read. The names of the files given here have
     * no influence on the read files, but will be used for error handling, so be sure to give the same names as in
     * the {@link CsvReader#readCsv(String, BiConsumer)} method.
     * @param stopFileName the name of the stops file
     * @param linkFileName the name of the link file
     * @param loadFileName the name of the load file. If set to "", no load will be read.
     * @param ptn the ptn to add the stops and links to
     * @param directed whether the ptn should be directed
     */
    PTNReader(AdjacencyListGraph<Stop, Link>* ptn, std::string stopFileName, std::string linkFileName, bool directed = false, std::string loadFileName=""){
        this->ptn = ptn;
        this->stopFileName = stopFileName;
        this->linkFileName = linkFileName;
        this->loadFileName = loadFileName;
        this->directed = directed;
    }


    /**
     * Process the contents of a stop line.
     * @param args the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line contains not exactly 3 or 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     */
     void processStopLine(std::vector <std::string> args, int lineNumber){
        if(args.size() != 5){
            throw InputFormatException(stopFileName, args.size(), 5);
        }

        int stopId;
        std::string shortName;
        std::string longName;
        double xCoordinate;
        double yCoordinate;

        try{
            stopId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(stopFileName, 1, lineNumber, "int", args[0]);
        }
        shortName = args[1];
        longName = args[2];
        try{
            xCoordinate = std::stod(args[3]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(stopFileName, 4, lineNumber, "double", args[3]);
        }
        try{
            yCoordinate = std::stod(args[4]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(stopFileName, 5, lineNumber, "double", args[4]);
        }

        Stop stop = Stop(stopId, shortName, longName, xCoordinate, yCoordinate);
        ptn->addNode(stop);
    }

    /**
     * Process the contents of a link line.
     * @param args the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line contains not exactly 6 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     */
     void processLinkLine(std::vector <std::string> args, int lineNumber){
        if(args.size() != 6){
            throw InputFormatException(linkFileName, args.size(), 6);
        }

        int linkId;
        int leftStopId;
        int rightStopId;
        double length;
        int lowerBound;
        int upperBound;

        try{
            linkId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linkFileName, 1, lineNumber, "int", args[0]);
        }
        try{
            leftStopId = std::stoi(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linkFileName, 2, lineNumber, "int", args[1]);
        }
        try{
            rightStopId = std::stoi(args[2]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linkFileName, 3, lineNumber, "int", args[2]);
        }
        try{
            length = std::stod(args[3]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linkFileName, 4, lineNumber, "double", args[3]);
        }
        try{
            lowerBound = std::stoi(args[4]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linkFileName, 5, lineNumber, "int", args[4]);
        }
        try{
            upperBound = std::stoi(args[5]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linkFileName, 6, lineNumber, "int", args[5]);
        }

        Link link = Link(linkId, ptn->getNode(leftStopId), ptn->getNode(rightStopId), length,
                         lowerBound, upperBound, directed);

        ptn->addEdge(link);
    }

    void processLoadLine(std::vector <std::string> args, int lineNumber){
        if(args.size() != 4){
            throw InputFormatException(loadFileName, args.size(), 4);
        }

        int linkId;
        double load;
        int lowerFreqBound;
        int upperFreqBound;

        try {
            linkId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(loadFileName, 1, lineNumber, "int", args[0]);
        }
        try{
            load = std::stod(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(loadFileName, 2, lineNumber, "double", args[1]);
        }
        try {
            lowerFreqBound = std::stoi(args[2]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(loadFileName, 2, lineNumber, "int", args[2]);
        }
        try {
            upperFreqBound = std::stoi(args[3]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(loadFileName, 3, lineNumber, "int", args[3]);
        }

        ptn->getEdge(linkId)->setLoadInformation(load, lowerFreqBound, upperFreqBound);
    }

    /**
     * Read all information for the ptn. Will only read the load, if some load file name was set in the constructor
     */
    void read(){
        CsvReader c;
        c.readCsv(stopFileName, *this, &PTNReader::processStopLine);
        c.readCsv(linkFileName, *this, &PTNReader::processLinkLine);
        if(loadFileName != ""){
            c.readCsv(loadFileName, *this, &PTNReader::processLoadLine);
        }
    }
};

#endif
