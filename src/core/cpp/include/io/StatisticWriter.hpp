#ifndef STATISTICWRITER_HPP
#define STATISTICWRITER_HPP
/**
 * Class implementing a static method to write a statistic. Just call {@link #writeStatistic(Config)}.
 */

#include <fstream>
#include "CsvWriter.hpp"
#include "../util/config.hpp"
#include "../util/Statistic.hpp"

class StatisticWriter{
    //private static Logger logger = Logger.getLogger("net.lintim.io.StatisticWriter");
public:
    StatisticWriter(){

    }
    /**
     * Write the statistic to the file specified in the config
     * @param config the config to read the file name from
     */
    void writeStatistic(Config config, Statistic sta){
        try{
            CsvWriter csvw;
            csvw.open(config.getStringValue("default_statistic_file"));
            for(auto entry : *sta.getData()){
                std::vector <std::string> content = {entry.first, CsvWriter::shortenDecimalValueIfItsDecimal(entry.second)};
                csvw.writeLine(content);
            }
            csvw.close();
        }
        catch(std::exception& e){
            //logger.log(Level.WARNING, e.toString());
            throw OutputFileException(config.getStringValue("default_statistic_file"));
        }
    }
};

#endif
