#ifndef PERIODICEANWRITER_HPP
#define PERIODICEANWRITER_HPP

/**
 * Implementation of a periodic ean writer as a static method, just call {@link #writeEan(Graph, Config, boolean)}.
 */

#include "../model/impl/AdjacencyListGraph.hpp"
#include "../model/PeriodicEvent.hpp"
#include "../model/PeriodicActivity.hpp"
#include "CsvWriter.hpp"
#include <algorithm>

class PeriodicEANWriter{

    /**
     * Write the given ean graph to the files specified in the config. The format of the outputted files is according
     * to the LinTim standard.
     *
     * @param ean           the ean to write
     * @param config        the config to read the filenames from
     * @param writeTimetable    whether the current timetable for the events should be written
     */
    void writeEan(AdjacencyListGraph<PeriodicEvent, PeriodicActivity> ean, Config config, bool writeTimetable){
        auto events_list = ean.getNodes();
        sort(events_list.begin(), events_list.end());
        CsvWriter csvw;
        csvw.full_write(
            config.getStringValue("default_events_periodic_file"),
            events_list,
            &PeriodicEvent::toCsvStrings,
            config.getStringValue("events_header_periodic")
        );

        auto activities_list = ean.getEdges();
        sort(activities_list.begin(), activities_list.end());
        csvw.full_write(
            config.getStringValue("default_activities_periodic_file"),
            activities_list,
            &PeriodicActivity::toCsvStrings,
            config.getStringValue("activities_header_periodic")
        );
        if(writeTimetable){
            csvw.full_write(
                config.getStringValue("default_timetable_periodic_file"),
                events_list,
                &PeriodicEvent::toCsvTimetableStrings,
                config.getStringValue("timetable_header_periodic")
            );
        }
    }
};

#endif
