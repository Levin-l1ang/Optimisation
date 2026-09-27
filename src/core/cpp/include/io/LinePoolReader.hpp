#ifndef LINEPOOLREADER_HPP
#define LINEPOOLREADER_HPP

/**
 * Class to process csv-lines, formatted in the LinTim Line-Pool.giv, Line-Concept.lin  or Line-Pool-Cost.giv format.
 * Use a {@link CsvReader}
 * with the appropriate processing methods as a {@link java.util.function.BiConsumer} argument to read the files.
 */


#include "../exception/exceptions.hpp"
#include "../model/impl/AdjacencyListGraph.hpp"
#include "../model/Link.hpp"
#include "../model/Stop.hpp"
#include "../model/LinePool.hpp"
#include "CsvReader.hpp"
#include <string>
#include <vector>
#include <iostream>

class LinePoolReader{
private:
    std::string lineCollectionFileName;
    std::string linePoolCostFileName;
    LinePool* linePool;
    AdjacencyListGraph<Stop, Link>* ptn;
    bool directed;
    bool readFrequencies;

public:
    /**
	 * Constructor of a LinePoolReader for a line pool or line concept (depending on readFrequencies) and a given
	 * filename. The given name will not influence
	 * the read file but the used name in any error message, so be sure to use the same name in here and in the
	 * {@link CsvReader}!
	 *
	 * @param lineCollectionFileName source file name for exceptions
	 * @param linePoolCostFileName   source file name for exceptions
	 * @param linePool               line pool
	 * @param directed               whether the links are directed
	 * @param readFrequencies        whether a line pool or a line concept with frequencies is read
	 */
    LinePoolReader(LinePool* linePool, AdjacencyListGraph<Stop, Link>* ptn, std::string lineCollectionFileName,
                   std::string linePoolCostFileName, bool directed, bool readFrequencies){
        this->lineCollectionFileName = lineCollectionFileName;
        this->linePoolCostFileName = linePoolCostFileName;
        this->linePool = linePool;
        this->ptn = ptn;
        this->directed = directed;
        this->readFrequencies = readFrequencies;
    }
    /**
     * Process the contents of a line collection line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 3 or 4 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if a line or link can not be found by their index
     * @throws LineLinkNotAddableException     if a link cannot be added to a line
     */
    void processLinePoolLine(std::vector <std::string> args, int lineNumber){

        if(!readFrequencies && args.size() != 3){
            throw InputFormatException(lineCollectionFileName, args.size(), 3);
        }
        if(readFrequencies && args.size() != 4){
            throw InputFormatException(lineCollectionFileName, args.size(), 4);
        }
        int lineId;
        int linkNumber;
        int linkId;
        int frequency;
        try{
            lineId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(lineCollectionFileName, 1, lineNumber, "int", args[0]);
        }
        try{
            linkNumber = std::stoi(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(lineCollectionFileName, 2, lineNumber, "int", args[1]);
        }
        try{
            linkId = std::stoi(args[2]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(lineCollectionFileName, 3, lineNumber, "int", args[2]);
        }
        if(linkNumber == 1){
            Line l = Line(lineId, directed);
            linePool->addLine(l);
        }

        Line* line =linePool->getLine(lineId);
        if(line == nullptr){
            throw DataIndexNotFoundException("Line", lineId);
        }

        Link* link = ptn->getEdge(linkId);
        if(link == nullptr){
            throw DataIndexNotFoundException("Edge", linkId);
        }

        bool linkAdded = line->addLink(*link);
        if(!linkAdded){
            throw LineLinkNotAddableException(linkId, lineId);
        }

        if(readFrequencies){
            try{
                frequency = std::stoi(args[3]);
            } catch(std::exception& e){
                throw InputTypeInconsistencyException(lineCollectionFileName, 4, lineNumber, "int", args[3]);
            }
            line->setFrequency(frequency);
        }

    }

    /**
     * Process the costs of a linepool line.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 3 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if a line cannot be found by its index
     */
    void processLineCostLine(std::vector <std::string> args, int lineNumber){

        if(args.size() != 3){
            throw InputFormatException(linePoolCostFileName, args.size(), 3);
        }

        int lineId;
        double length;
        double cost;

        try{
            lineId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linePoolCostFileName, 1, lineNumber, "int", args[0]);
        }
        try{
            length = std::stod(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linePoolCostFileName, 2, lineNumber, "double", args[1]);
        }
        try{
            cost = std::stod(args[2]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(linePoolCostFileName, 3, lineNumber, "double", args[2]);
        }

        Line* line = linePool->getLine(lineId);
        if(line == nullptr){
            throw DataIndexNotFoundException("Line", lineId);
        }

        line->setLength(length);
        line->setCost(cost);
    }

    void read(void){
        CsvReader c;
        c.readCsv(lineCollectionFileName, *this, &LinePoolReader::processLinePoolLine);
        if (!linePoolCostFileName.empty()) {
            c.readCsv(linePoolCostFileName, *this, &LinePoolReader::processLineCostLine);
        }
    }
};
#endif
