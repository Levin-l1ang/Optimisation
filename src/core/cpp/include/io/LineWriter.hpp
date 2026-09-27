#ifndef LINEWRITER_HPP
#define LINEWRITER_HPP
/**
 * Class implementing writing line pools and concepts as static methods. Just call
 * {@link #writeLinePool(LinePool, Config, boolean)} or {@link #writeLineConcept(LinePool, Config)} to write the data
 * to the files specified in the config
 */


#include "CsvWriter.hpp"
#include "../util/config.hpp"
#include "../model/LinePool.hpp"
#include "../model/Link.hpp"
#include "../model/Line.hpp"
#include <algorithm>

class LineWriter{
public:
    static void writeLinePool(LinePool pool, Config config, bool withCost, bool writeFrequencies = false){
        std::vector <Line> line_list = pool.getLines();
        sort(line_list.begin(), line_list.end());
        CsvWriter csvw;

        try{
            if (writeFrequencies){
                csvw.open(config.getStringValue("default_lines_file"),
                         config.getStringValue("lines_header"));
            }
            else{
                csvw.open(config.getStringValue("default_pool_file"), config.getStringValue("lpool_header"));
            }

            for(Line line : line_list){
                int edgeIndex = 1;
                for(Link link : line.getLinePath().getEdges()){
                    std::vector <std::string> content = {
                        std::to_string(line.getId()),
                        std::to_string(edgeIndex),
                        std::to_string(link.getId())};
                    if (writeFrequencies){
                        content.push_back(std::to_string(line.getFrequency()));
                    }
                    csvw.writeLine(content);
                    edgeIndex++;
                }
            }
            csvw.close();
        }
        catch(std::exception& e){
            throw OutputFileException(config.getStringValue("default_pool_file"));
        }
        if(withCost){
            csvw.full_write(
                config.getStringValue("default_pool_cost_file"),
                line_list,
                &Line::toLineCostCsvStrings,
                config.getStringValue("lpool_cost_header")
            );
        }
    }

    static void writeLineConcept(LinePool pool, Config config){
        writeLinePool(pool, config, false, true);
    }
};

#endif
