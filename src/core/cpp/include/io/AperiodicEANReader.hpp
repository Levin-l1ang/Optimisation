/**
 * Class to process csv-lines, formatted in the LinTim Activities-expanded.giv, Events-expanded.giv or
 * Timetable-expanded.tim format. Use a {@link CsvReader}
 * with an instance of this class to read a periodic activities, events or timetable file.
 * <p>
 */
#ifndef APERIODICEANREADER_HPP
#define APERIODICEANREADER_HPP

#include "../exception/exceptions.hpp"
#include <string>
#include <algorithm>
#include "../model/AperiodicActivity.hpp"
#include "../model/AperiodicEvent.hpp"
#include "../model/impl/AdjacencyListGraph.hpp"
#include "CsvReader.hpp"

class AperiodicEANReader{
private:
    std::string aperiodicActivitiesFileName;
    std::string aperiodicEventsFileName;
    std::string aperiodicTimetableFileName;
    AdjacencyListGraph<AperiodicEvent, AperiodicActivity>* aperiodicEAN;

public:
    /**
     * Constructor for a PeriodicEANReader for given file names and periodic EAN.  The given names will not influence
     * the read file but the used name in any error message, so be sure to use the same name in here and in the
     * {@link CsvReader}!
     *
     * @param aperiodicActivitiesFileName source file name for aperiodic activities
     * @param aperiodicEventsFileName     source file name for aperiodic events
     * @param aperiodicTimetableFileName  source file name for aperiodic timetable
     * @param aperiodicEAN                aperiodic event activity network
     */
    AperiodicEANReader(AdjacencyListGraph<AperiodicEvent, AperiodicActivity>* aperiodicEAN,
                       std::string aperiodicActivitiesFileName, std::string aperiodicEventsFileName,
                       std::string aperiodicTimetableFileName = ""){
        this->aperiodicActivitiesFileName = aperiodicActivitiesFileName;
        this->aperiodicEventsFileName = aperiodicEventsFileName;
        this->aperiodicTimetableFileName = aperiodicTimetableFileName;
        this->aperiodicEAN = aperiodicEAN;
    }


    /**
     * Process the content of an aperiodic event file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 6 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws DataIllegalEventTypeException        if the event type is not defined
     * @throws GraphNodeIdMultiplyAssignedException if the event cannot be added to the EAN
     */
    void processAperiodicEvent(std::vector <std::string> args, int lineNumber){
        if(args.size() != 6){
            throw InputFormatException(aperiodicEventsFileName, args.size(), 6);
        }

        int eventId;
        int periodicEventId;
        EventType type;
        int time;
        double passengers;
        int stopId;

        try{
            eventId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicEventsFileName, 1, lineNumber, "int", args[0]);
        }

        try{
            periodicEventId = std::stoi(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicEventsFileName, 2, lineNumber, "int", args[1]);
        }
        transform(args[2].begin(), args[2].end(), args[2].begin(), ::tolower);
        if(args[2].find("arrival") != std::string::npos){
            type = ARRIVAL;
        } else if(args[2].find("departure") != std::string::npos){
            type = DEPARTURE;
        } else{
            throw DataIllegalEventTypeException(eventId, args[2]);
        }

        try{
            time = std::stoi(args[3]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicEventsFileName, 4, lineNumber, "int", args[3]);
        }

        try{
            passengers = std::stod(args[4]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicEventsFileName, 5, lineNumber, "double", args[4]);
        }

        try{
            stopId = std::stoi(args[5]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicEventsFileName, 6, lineNumber, "int", args[5]);
        }

        AperiodicEvent aperiodicEvent = AperiodicEvent(eventId, periodicEventId, stopId, type, time, passengers);

        if(!aperiodicEAN->addNode(aperiodicEvent)){
            throw GraphNodeIdMultiplyAssignedException(eventId);
        }
    }

    /**
     * Process the content of a periodic activity file.
     *
     * @param args       the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException                 if the line contains not exactly 8 entries
     * @throws InputTypeInconsistencyException      if the specific types of the entries do not match the expectations
     * @throws DataIllegalActivityTypeException     if the activity type is not defined
     * @throws GraphIncidentNodeNotFoundException   if an event incident to the activity is not found
     * @throws GraphEdgeIdMultiplyAssignedException if the activity cannot be added to the EAN
     */
    void processAperiodicActivity(std::vector <std::string> args, int lineNumber){
        if(args.size() != 8){
            throw InputFormatException(aperiodicActivitiesFileName, args.size(), 8);
        }

        int activityId;
        int periodicActivityId;
        ActivityType type;
        int sourceEventId;
        int targetEventId;
        int lowerBound;
        int upperBound;
        double passengers;

        try{
            activityId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicActivitiesFileName, 1, lineNumber, "int", args[0]);
        }

        try{
            periodicActivityId = std::stoi(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicActivitiesFileName, 2, lineNumber, "int", args[1]);
        }
        std::transform(args[2].begin(), args[2].end(), args[2].begin(), ::tolower);
        if(args[2].find("drive") != std::string::npos){
            type = DRIVE;
        } else if(args[2].find("wait") != std::string::npos){
            type = WAIT;
        } else if(args[2].find("change") != std::string::npos){
            type = CHANGE;
        } else if(args[2].find("headway") != std::string::npos){
            type = HEADWAY;
        } else if(args[2].find("turnaround") != std::string::npos){
            type = TURNAROUND;
        } else if(args[2].find("sync") != std::string::npos){
            type = SYNC;
        } else{
            throw DataIllegalActivityTypeException(activityId, args[2]);
        }

        try{
            sourceEventId = std::stoi(args[3]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicActivitiesFileName, 4, lineNumber, "int", args[3]);
        }

        try{
            targetEventId = std::stoi(args[4]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicActivitiesFileName, 5, lineNumber, "int", args[4]);
        }

        try{
            lowerBound = std::stoi(args[5]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicActivitiesFileName, 6, lineNumber, "int", args[5]);
        }

        try{
            upperBound = std::stoi(args[6]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicActivitiesFileName, 7, lineNumber, "int", args[6]);
        }

        try{
            passengers = std::stod(args[7]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicActivitiesFileName, 8, lineNumber, "double", args[7]);
        }

        AperiodicEvent* sourceEvent = aperiodicEAN->getNode(sourceEventId);

        if(sourceEvent == nullptr){
            throw GraphIncidentNodeNotFoundException(activityId, sourceEventId);
        }

        AperiodicEvent* targetEvent = aperiodicEAN->getNode(targetEventId);

        if(targetEvent == nullptr){
            throw GraphIncidentNodeNotFoundException(activityId, targetEventId);
        }

        bool activityAdded = aperiodicEAN->addEdge(AperiodicActivity(activityId, periodicActivityId, type,
                                                                                sourceEvent, targetEvent, lowerBound, upperBound, passengers));

        if(!activityAdded){
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
    void processAperiodicTimetable(std::vector <std::string> args, int lineNumber){
        if(args.size() != 2){
            throw InputFormatException(aperiodicTimetableFileName, args.size(), 2);
        }

        int eventId;
        int time;

        try{
            eventId = std::stoi(args[0]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicTimetableFileName, 1, lineNumber, "int", args[0]);
        }

        try{
            time = std::stoi(args[1]);
        } catch(std::exception& e){
            throw InputTypeInconsistencyException(aperiodicTimetableFileName, 2, lineNumber, "int", args[1]);
        }

        AperiodicEvent* event = aperiodicEAN->getNode(eventId);

        if(event == nullptr){
            throw DataIndexNotFoundException("Aperiodic event", eventId);
        }

        event->setTime(time);
    }
    /**
    *   Reads Everything.
    */
    void read(){
        CsvReader c;
        c.readCsv(aperiodicEventsFileName, *this, &AperiodicEANReader::processAperiodicEvent);
        c.readCsv(aperiodicActivitiesFileName, *this, &AperiodicEANReader::processAperiodicActivity);
        if(!aperiodicTimetableFileName.empty()){
            c.readCsv(aperiodicTimetableFileName, *this, &AperiodicEANReader::processAperiodicTimetable);
        }
    }
};
#endif
