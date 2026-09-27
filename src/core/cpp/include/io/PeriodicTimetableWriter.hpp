#ifndef PERIODICTIMETABLEWRITER_HPP
#define PERIODICTIMETABLEWRITER_HPP

/**
 * Implementation of a periodic timetable writer as a static method, just call {@link #writeEan(Graph, Config, boolean)}.
 */

#include "../model/impl/AdjacencyListGraph.hpp"
#include "../model/PeriodicEvent.hpp"
#include "../model/PeriodicActivity.hpp"
#include "../util/config.hpp"
#include "CsvWriter.hpp"
#include <algorithm>

class PeriodicTimetableWriter{
public:
    /**
     * Write the given ean graph to the files specified in the config. The format of the outputted files is according
     * to the LinTim standard.
     *
     * @param ean           the ean to write
     * @param config        the config to read the filenames from
     */
    void writeTimetable(AdjacencyListGraph<PeriodicEvent, PeriodicActivity> ean, Config config){
        auto events_list = ean.getNodes();
        sort(events_list.begin(), events_list.end());
        CsvWriter csvw;

        csvw.full_write(
            config.getStringValue("default_timetable_periodic_file"),
            events_list,
            &PeriodicEvent::toCsvTimetableStrings,
            config.getStringValue("timetable_header_periodic")
        );

    }
};

#endif
