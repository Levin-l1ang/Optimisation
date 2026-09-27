/**
 * Class to process csv-lines, formatted in the LinTim OD.giv format. Use a {@link CsvReader}
 * with the appropriate processing methods as a {@link java.util.function.BiConsumer} argument to read the files.
 */
#ifndef ODREADER_HPP
#define ODREADER_HPP

#include "../exception/exceptions.hpp"
#include "../model/DemandPoint.hpp"
#include "../model/OD.hpp"
#include "../model/ODPair.hpp"
#include "CsvReader.hpp"
#include <string>
#include <vector>


class ODReader{
private:
    std::string sourceFileName;
    OD* od;

public:
    /**
     * Constructor of an ODReader for a demand collection and a given filename. The given name will not influence
     * the read file but the used name in any error message, so be sure to use the same name in here and in the
     * {@link CsvReader}!
     *
     * @param sourceFileName source file name for exceptions
     * @param od             OD
     */
    ODReader(OD* od, std::string sourceFileName){
        this->sourceFileName = sourceFileName;
        this->od = od;
    }

    /**
     * Process the contents of an od matrix line.
     * @param args the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line contains not exactly 3 or 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     */
    void processODLine(std::vector <std::string> args, int lineNumber){
        if(args.size() != 3){
            throw InputFormatException(sourceFileName, args.size(), 3);
        }

        int origin;
        int destination;
        double passengers;

        try{
            origin = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(sourceFileName, 1, lineNumber, "int", args[0]);
        }
        try{
            destination = std::stoi(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(sourceFileName, 2, lineNumber, "int", args[1]);
        }
        try{
            passengers = std::stod(args[2]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(sourceFileName, 3, lineNumber, "double", args[2]);
        }

        od->setValue(origin, destination, passengers);
    }

    void read(void){
        CsvReader c;
        c.readCsv(sourceFileName, *this, &ODReader::processODLine);
    }
};
#endif
