/**
 * Class to process csv-lines, formatted in the LinTim Demand.giv format. Use a {@link CsvReader}
 * with the appropriate processing methods as a {@link java.util.function.BiConsumer} argument to read the files.
 */
#ifndef DEMANDREADER_HPP
#define DEMANDREADER_HPP

#include "../exception/exceptions.hpp"
#include "../model/DemandPoint.hpp"
#include "CsvReader.hpp"
#include <string>
#include <vector>

class DemandReader{
private:
    std::string sourceFileName;
    std::vector <DemandPoint>* demand;

public:
    /**
     * Constructor of a DemandReader for a demand collection and a given filename. The given name will not influence
     * the read file but the used name in any error message, so be sure to use the same name in here and in the
     * {@link CsvReader}!
     *
     * @param sourceFileName source file name for exceptions
     * @param demand         collection of demand points
     */
    DemandReader(std::vector <DemandPoint>* demand, std::string sourceFileName){
        this->sourceFileName = sourceFileName;
        this->demand = demand;
    }


    /**
     * Process the contents of a demand line.
     * @param args the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line contains not exactly 6 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     */
    void processDemandLine(std::vector <std::string> args, int lineNumber){
        if(args.size() != 6){
            throw InputFormatException(sourceFileName, args.size(), 6);
        }
        int stopId;
        std::string shortName;
        std::string longName;
        double xCoordinate;
        double yCoordinate;
        int demandAtDemandPoint;
        try{
            stopId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(sourceFileName, 1, lineNumber, "int", args[0]);
        }
        shortName = args[1];
        longName = args[2];
        try{
            xCoordinate = std::stod(args[3]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(sourceFileName, 4, lineNumber, "double", args[3]);
        }
        try{
            yCoordinate = std::stod(args[4]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(sourceFileName, 5, lineNumber, "double", args[4]);
        }
        try{
            demandAtDemandPoint = std::stoi(args[5]);
        }
        catch(std::exception& e){
            throw InputTypeInconsistencyException(sourceFileName, 6, lineNumber, "int", args[5]);
        }
        DemandPoint demandPoint = DemandPoint(stopId, shortName, longName, xCoordinate, yCoordinate,
                                              demandAtDemandPoint);
        demand->push_back(demandPoint);
    }

    void read(void){
        CsvReader c;
        c.readCsv(sourceFileName, *this, &DemandReader::processDemandLine);
    }
};

#endif
