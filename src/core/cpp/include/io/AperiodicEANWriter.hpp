#ifndef APERIODICEANWRITER_HPP
#define APERIODICEANWRITER_HPP

/**
 * Implementation of an aperiodic ean writer as a static method, just call {@link #writeEan(Graph, Config)}.
 */

#include "../model/impl/AdjacencyListGraph.hpp"
#include "../model/AperiodicEvent.hpp"
#include "../model/AperiodicActivity.hpp"
#include "CsvWriter.hpp"
#include <algorithm>

class AperiodicEANWriter{
public:
    /**
     * Write the given ean graph to the files specified in the config. The format of the outputted files is according
     * to the LinTim standard.
     *
     * @param ean           the ean to write
     * @param config        the config to read the filenames from
     */
    void writeEan(AdjacencyListGraph<AperiodicEvent, AperiodicActivity> ean, Config config){
        CsvWriter csvw;

        auto events_list = ean.getNodes();
        sort(events_list.begin(), events_list.end());

        csvw.full_write(
            config.getStringValue("default_events_expanded_file"),
            events_list,
            &AperiodicEvent::toCsvStrings,
            config.getStringValue("events_header")
        );
        auto activities_list = ean.getEdges();
        sort(activities_list.begin(), activities_list.end());
        csvw.full_write(
            config.getStringValue("default_activities_expanded_file"),
            activities_list,
            &AperiodicActivity::toCsvStrings,
            config.getStringValue("activities_header")
        );
    }
};

#endif
