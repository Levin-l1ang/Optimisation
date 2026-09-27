/**
 * Class to process csv-lines, formatted in the LinTim Activities-periodic.giv, Events-periodic.giv or
 * Timetable-periodic.tim format. Use a {@link CsvReader}
 * with an instance of this class to read a periodic activities, events or timetable file.
 * <p>
 */

#ifndef PERIODICEANREADER_HPP
#define PERIODICEANREADER_HPP

#include "../exception/exceptions.hpp"
#include <string>
#include <algorithm>
#include "../model/PeriodicActivity.hpp"
#include "../model/PeriodicEvent.hpp"
#include "../model/impl/AdjacencyListGraph.hpp"
#include "CsvReader.hpp"

class PeriodicEANReader{
private:
    std::string periodicActivitiesFileName;
    std::string periodicEventsFileName;
    std::string periodicTimetableFileName;
    AdjacencyListGraph<PeriodicEvent, PeriodicActivity>* periodicEAN;


public:
    /**
     * Constructor for a PeriodicEANReader for given file names and periodic EAN.  The given names will not influence
     * the read file but the used name in any error message, so be sure to use the same name in here and in the
     * {@link CsvReader}!
     *
     * @param periodicActivitiesFileName source file name for periodic activities
     * @param periodicEventsFileName     source file name for periodic events
     * @param periodicTimetableFileName  source file name for periodic timetable
     * @param periodicEAN                periodic event activity network
     */
    PeriodicEANReader(AdjacencyListGraph<PeriodicEvent, PeriodicActivity>* periodicEAN,
                      std::string periodicActivitiesFileName, std::string periodicEventsFileName,
                      std::string periodicTimetableFileName = ""){
        this->periodicActivitiesFileName = periodicActivitiesFileName;
        this->periodicEventsFileName = periodicEventsFileName;
        this->periodicTimetableFileName = periodicTimetableFileName;
        this->periodicEAN = periodicEAN;
    }

    /**
     * Process the content of a periodic event file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 5 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws DataIllegalEventTypeException        if the event type is not defined
     * @throws GraphNodeIdMultiplyAssignedException if the event cannot be added to the EAN
     */

    void processPeriodicEvent(std::vector <std::string> args, int lineNumber){
        if(args.size() != 7){
            throw new InputFormatException(periodicEventsFileName, args.size(), 5);
        }

        int eventId;
        EventType type;
        int stopId;
        int lineId;
        double passengers;
        LineDirection ldir;
        int lineFrequencyRepetition;

        try{
            eventId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicEventsFileName, 1, lineNumber, "int", args[0]);
        }
        std::transform(args[1].begin(), args[1].end(), args[1].begin(), ::tolower);
        if(args[1].find("arrival") != std::string::npos){
            type = ARRIVAL;
        } else if(args[1].find("departure") != std::string::npos){
            type = DEPARTURE;
        } else if(args[1].find("fix") != std::string::npos){
            type = FIX;
        } else if(args[1].find("virtual") != std::string::npos){
            type = EXT;
        } else{
            throw DataIllegalEventTypeException(eventId, args[1]);
        }

        try{
            stopId = std::stoi(args[2]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicEventsFileName, 3, lineNumber, "int", args[2]);
        }

        try{
            lineId = std::stoi(args[3]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicEventsFileName, 4, lineNumber, "int", args[3]);
        }

        try{
            passengers = std::stod(args[4]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicEventsFileName, 5, lineNumber, "double", args[4]);
        }

        try{
            std::string tmp = args[5];
            if (tmp.find("<") != std::string::npos) ldir = BACKWARDS;
            else if (tmp.find(">") != std::string::npos) ldir = FORWARDS;
            else throw DataIllegalLineDirectionException(eventId, args[5]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicEventsFileName, 5, lineNumber, "string", args[5]);
        }

        try{
            lineFrequencyRepetition = std::stoi(args[6]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicEventsFileName, 6, lineNumber, "double", args[6]);
        }


        PeriodicEvent periodicEvent = PeriodicEvent(eventId, stopId, type, lineId, 0, passengers, ldir, lineFrequencyRepetition);

        if(!periodicEAN->addNode(periodicEvent)){
            throw GraphNodeIdMultiplyAssignedException(eventId);
        }
    }

    /**
     * Process the content of a periodic activity file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 7 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws DataIllegalActivityTypeException     if the activity type is not defined
     * @throws GraphIncidentNodeNotFoundException   if an event incident to the activity is not found
     * @throws GraphEdgeIdMultiplyAssignedException if the activity cannot be added to the EAN
     */
     void processPeriodicActivity(std::vector <std::string> args, int lineNumber){
        if(args.size() != 7){
            throw InputFormatException(periodicActivitiesFileName, args.size(), 7);
        }

        int activityId;
        ActivityType type;
        int sourceEventId;
        int targetEventId;
        int lowerBound;
        int upperBound;
        double passengers;

        try{
            activityId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicActivitiesFileName, 1, lineNumber, "int", args[0]);
        }
        std::transform(args[1].begin(), args[1].end(), args[1].begin(), ::tolower);
        if(args[1].find("drive") != std::string::npos){
            type = DRIVE;
        } else if(args[1].find("wait") != std::string::npos){
            type = WAIT;
        } else if(args[1].find("change") != std::string::npos){
            type = CHANGE;
        } else if(args[1].find("headway") != std::string::npos){
            type = HEADWAY;
        } else if(args[1].find("turnaround") != std::string::npos){
            type = TURNAROUND;
        } else if(args[1].find("sync") != std::string::npos){
            type = SYNC;
        } else if(args[1].find("virtual") != std::string::npos){
            type = VIRTUAL;
        } else if(args[1].find("nonscheduled") != std::string::npos){
            type = NONSCHEDULED;
        } else{
            throw DataIllegalActivityTypeException(activityId, args[1]);
        }

        try{
            sourceEventId = std::stoi(args[2]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicActivitiesFileName, 3, lineNumber, "int", args[2]);
        }

        try{
            targetEventId = std::stoi(args[3]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicActivitiesFileName, 4, lineNumber, "int", args[3]);
        }

        try{
            lowerBound = std::stoi(args[4]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicActivitiesFileName, 5, lineNumber, "int", args[4]);
        }

        try{
            upperBound = std::stoi(args[5]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicActivitiesFileName, 6, lineNumber, "int", args[5]);
        }

        try{
            passengers = std::stod(args[6]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicActivitiesFileName, 7, lineNumber, "double", args[6]);
        }

        PeriodicEvent* sourceEvent = periodicEAN->getNode(sourceEventId);

        if(sourceEvent == nullptr){
            throw GraphIncidentNodeNotFoundException(activityId, sourceEventId);
        }

        PeriodicEvent* targetEvent = periodicEAN->getNode(targetEventId);

        if(targetEvent == nullptr){
            throw GraphIncidentNodeNotFoundException(activityId, targetEventId);
        }

        if(!periodicEAN->addEdge(PeriodicActivity(activityId, type, sourceEvent, targetEvent,
                                                            lowerBound, upperBound, passengers))){
            throw GraphEdgeIdMultiplyAssignedException(activityId);
        }

    }

    /**
     * Process the content of a timetable file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException            if the line contains not exactly 2 entries
     * @throws InputTypeInconsistencyException if the specific types of the entries do not match the expectations
     * @throws DataIndexNotFoundException      if the event does not exist
     */
     void processPeriodicTimetable(std::vector <std::string> args, int lineNumber){
        if(args.size() != 2){
            throw InputFormatException(periodicTimetableFileName, args.size(), 2);
        }

        int eventId;
        int time;

        try{
            eventId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicTimetableFileName, 1, lineNumber, "int", args[0]);
        }

        try{
            time = std::stoi(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(periodicTimetableFileName, 2, lineNumber, "int", args[1]);
        }

        PeriodicEvent* event = periodicEAN->getNode(eventId);

        if(event == nullptr){
            throw DataIndexNotFoundException("Periodic event", eventId);
        }

        event->setTime(time);
    }

    /**
    *   Reads Everything.
    */
    void read(){
        CsvReader c;
        c.readCsv(periodicEventsFileName, *this, &PeriodicEANReader::processPeriodicEvent);
        c.readCsv(periodicActivitiesFileName, *this, &PeriodicEANReader::processPeriodicActivity);
        if(!periodicTimetableFileName.empty()){
            c.readCsv(periodicTimetableFileName, *this, &PeriodicEANReader::processPeriodicTimetable);
        }
    }
};
#endif
