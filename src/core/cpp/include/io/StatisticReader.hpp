/**
 * Class to process csv-lines, formatted in the LinTim statistic format. Use a {@link CsvReader}
 * with the appropriate processing methods as a {@link java.util.function.BiConsumer} argument to read the files.
 */
#ifndef STATISTICREADER_HPP
#define STATISTICREADER_HPP

#include "../exception/exceptions.hpp"
#include "../io/CsvReader.hpp"
#include "../util/Statistic.hpp"
#include <string>
class StatisticReader{
private:
    std::string sourceFileName;
    Statistic* stat;
    void trim(std::string &s){
        // trim endline
        while (int(s[s.size()-1]) < 32) s = s.substr(0, s.size()-1);
        //trim whitespace
        while (s[0] == ' ') s = s.substr(1, s.size()-1);
        while (s[s.size()-1] == ' ') s = s.substr(0, s.size()-1);
    }

public:

    /**
     * Initialize a new statistic reader for the given filename. The given name will not influence the read file but
     * the used name in any error message, so be sure to use the same name in here and in the {@link CsvReader}!
     * @param sourceFileName the file to read from
     */
    StatisticReader(Statistic* stat, std::string sourceFileName){
        this->stat = stat;
        this->sourceFileName = sourceFileName;
    }

    /**
    * Process the contents of a statistic line.
    * @param args the content of the line
    * @param lineNumber the line number, used for error handling
    * @throws InputFormatException if the line contains not exactly 3 or 4 entries
    */
    void processStatisticLine(std::vector <std::string> args, int lineNumber){
        if(args.size() != 2){
            throw new InputFormatException(sourceFileName, args.size(), 2);
        }
        std::string key = args[0];
        std::string value = args[1];
        trim(key);
        trim(value);
        //Remove quotation marks
        if(value.size() >= 2 && value[0] == '"' && value[value.size() - 1] == '"'){
            value = value.substr(1, value.size() - 2);
        }
        stat->setValue(key, value);
    }


    void read(void){
        CsvReader c;
        c.readCsv(sourceFileName, *this, &StatisticReader::processStatisticLine);
    }
};


#endif
