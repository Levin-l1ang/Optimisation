#ifndef ODWRITER_HPP
#define ODWRITER_HPP

#include "CsvWriter.hpp"
#include "../model/impl/AdjacencyListGraph.hpp"
#include "../model/Stop.hpp"
#include "../model/Link.hpp"
#include "../model/OD.hpp"
#include "../model/ODPair.hpp"

/**
 * Class implementing the writing of an od matrix as a static method. Just call {@link #writeOD(OD, Config)} to write
 * the od matrix to the file specified in the config
 */

class ODWriter{
    /**
     * Write the given OD matrix of a ptn to the files specified in the config. The format of the outputted files is according
     * to the LinTim standard.
     *
     * @param ptn           the ptn to write
     * @param od            the od implementation where we get the values from
     * @param config        the config to read the filenames from
     */
    void writeOD(AdjacencyListGraph<Stop, Link>& ptn, OD& od, Config config){
        std::vector <ODPair> odPairs;
        for(Stop& origin : ptn.getNodes()){
            for(Stop& destination : ptn.getNodes()){
                odPairs.push_back(ODPair(origin.getId(), destination.getId(),
                                         od.getValue(origin.getId(), destination.getId())));
            }
        }
        CsvWriter csvw;
        csvw.full_write(
            config.getStringValue("default_od_file"),
            odPairs,
            &ODPair::toCsvStrings,
            config.getStringValue("od_header")
        );
    }
};

#endif
