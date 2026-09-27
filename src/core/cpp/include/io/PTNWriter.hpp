#ifndef PTNWRITER_HPP
#define PTNWRITER_HPP
/**
 * Class implementing the writing of the ptn as a static method. Just call
 * {@link #writePtn(Graph, Config, boolean, boolean)} to write
 * the PTN to the files specified in the provided config
 *
 */
#include "../util/config.hpp"
#include "../model/impl/AdjacencyListGraph.hpp"
#include "../model/Link.hpp"
#include "../model/Stop.hpp"
#include "CsvWriter.hpp"
#include <algorithm>

class PTNWriter{
public:
    /**
     * Write the given ptn graph to the files specified in the config. The format of the outputted files is according
     * to the LinTim standard.
     *
     * @param ptn           the ptn to write
     * @param config        the config to read the filenames from
     * @param writeLoads    whether the current loads on the links should be written
     * @param writeHeadways whether the current headways on the edges should be written
     */

    void writePtn(Graph<Stop, Link> &ptn, Config &config, bool writeLoads, bool writeHeadways) {
        writePtn(ptn, config, true, true, writeLoads, writeHeadways);
    }

    /**
     * Write the given ptn graph to the files specified in the config. The format of the outputted files is according
     * to the LinTim standard.
     *
     * @param ptn           the ptn to write
     * @param config        the config to read the filenames from
     * @param writeStops    whether the current stops should be written
     * @param writeEdges    whether the current links should be written
     * @param writeLoads    whether the current loads on the links should be written
     * @param writeHeadways whether the current headways on the edges should be written
     */
    void writePtn(Graph<Stop, Link> &ptn, Config &config, bool writeStops,
        bool writeEdges, bool writeLoads, bool writeHeadways){

        CsvWriter csvw;
        if (writeStops){
            std::vector <Stop> stops = ptn.getNodes();
            std::sort(stops.begin(), stops.end());
            csvw.full_write(config.getStringValue("default_stops_file"),
                            stops,
                            &Stop::toCsvStrings,
                            config.getStringValue("stops_header"));
        }

        //Sort the links beforehand, we may need to write it three times
        std::vector <Link> links = ptn.getEdges();
        std::sort(links.begin(), links.end());

        if (writeEdges){
            csvw.full_write(
                config.getStringValue("default_edges_file"),
                links, &Link::toCsvStrings,
                config.getStringValue("edges_header"));
        }

        if(writeLoads){
            csvw.full_write(
                config.getStringValue("default_loads_file"),
                links,
                &Link::toCsvLoadStrings,
                config.getStringValue("loads_header")
            );
        }

        if(writeHeadways){
            csvw.full_write(
                config.getStringValue("default_headways_file"),
                links,
                &Link::toCsvHeadwayStrings,
                config.getStringValue("headways_header")
            );
        }
    }
};

#endif
