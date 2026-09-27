/**
 * A reader for config files. To use, initialize a new ConfigReader and use it to call
 * {@link CsvReader#readCsv(std::string, java.util.function.BiConsumer)} with the config file to read.
 */

#ifndef CONFIGREADER_HPP
#define CONFIGREADER_HPP


#include "../exception/exceptions.hpp"
#include "../io/CsvReader.hpp"
#include "../util/config.hpp"
#include "../util/SolverType.hpp"
#include "../util/LogLevel.hpp"
#include <string>
#include <vector>
#include <fstream>


class ConfigReader{
private:
    std::string configFileName;
    bool onlyIfExists;
    Config* config;
    //static Logger logger = Logger.getLogger("net.lintim.io.ConfigReader");
public:
    /**
     * Initialize a new ConfigReader with the source file, that should be read, the onlyIfExists tag, i.e., whether
     * an io error on "include" parameters should abort the reading process, and the config to put the read values in.
     * @param configFileName the relative path to the config file to read
     * @param onlyIfExists whether an io error on "include" parameters should abort the reading process. If set to
     *                     true, io errors will be written to output but ignored
     * @param config the config to write the read parameters to
     */
    ConfigReader(Config* config, std::string configFileName, bool onlyIfExists = false, std::string path = ""){
        this->configFileName = path + configFileName;
        this->onlyIfExists = onlyIfExists;
        this->config = config;
    }

    std::string getConfigFileName(){
        return this->configFileName;
    }

    bool getOnlyIfExists(){
        return this->onlyIfExists;
    }

    Config* getConfig(){
        return this->config;
    }
    //void processConfigLine(std::vector <std::string> , int );

    /**
     * Read the file at the given filename and parse it as a config file. Will follow link to other config files,
     * when marked with "include" or "include_if_exists". Will throw, if any such file can not be found or read,
     * depending on {@code onlyIfExists}.
     *
     * @param fileName       the filename of the file to read
     * @param onlyIfExists If true, no exception will be thrown if a referenced file cannot be found.
     * @throws InputFileException on any io error
     */
    void readConfig(std::string fileName, bool onlyIfExists){
        CsvReader c;
        c.readCsv(fileName, *this, &ConfigReader::processConfigLine);
    }

    /**
     * Read the file at the given filename and parse it as a config file. Will follow link to other config files,
     * when marked with "include" or "include_if_exists". Will throw, if any such file can not be found or read.
     *
     * @param fileName the filename of the file to read
     * @throws InputFileException on any io error
     */
    void readConfig(std::string fileName){
        readConfig(fileName, false);
    }

    void read(void){
        CsvReader c;
        c.readCsv(configFileName, *this, &ConfigReader::processConfigLine);
    }

    /**
     * Process the contents of a config line.
     * @param args the content of the line
     * @param lineNumber the line number, used for error handling
     * @throws InputFormatException if the line has less than two entries
     * @throws UncheckedIOException on any io error while trying to read included config files
     */
    void processConfigLine(std::vector <std::string> args, int lineNumber){
        //We except at least two entries (more than two, if the value contains a ";"

        if(args.size() <= 1){
            throw InputFormatException(configFileName, args.size(), 2);
        }
        std::string key = args[0];
        std::string value = "";
        for(int i = 1; i < (int) args.size() - 1; i++) value += args[i] + ";";
        value += args[args.size() - 1];

        // trim endline
        if (int(value[value.size()-1]) < 32) value = value.substr(0, value.size()-1);

        // trim whitespaces
        while (value[0] == ' ') value = value.substr(1, value.size()-1);
        while (value[value.size()-1] == ' ') value.resize(value.size()-1);

        // trim quotation marks
        if(value.size() >= 2 && value[0] == '"' && value[value.size() - 1] == '"'){
            value = value.substr(1, value.size() - 2);
        }

        // include additional config files
        if(key == "include_if_exists"){
            std::ifstream file((getParentDirectoryName(configFileName) + "/" + value).c_str());
            if(file.good()){
                readConfig(getParentDirectoryName(configFileName) + "/" + value);
                try{
                } catch(std::exception& e){
                    //logger.log(Level.WARNING, "Catched IOException while reading " + Paths.get(configFileName).toAbsolutePath().getParent().toString() + File
                    //.pathSeparator + value + ": " + e.toString());
                }
            }

        } else if(key == "include"){
            try{
                readConfig(getParentDirectoryName(configFileName) + "/" + value);
            } catch(std::exception& e){
                //logger.log(Level.WARNING, "Catched IOException while reading " + Paths.get(configFileName).toAbsolutePath().getParent().toString() + File
                //  .pathSeparator + value + ": " + e.toString());
                if(!onlyIfExists){
                    throw InputFileException(value);
                }
            }
        } else{
            this->config->put(key, value);
        }
    }

private:
    static std::string getParentDirectoryName(std::string filePath){
        size_t found = filePath.find_last_of("/\\");
        return filePath.substr(0, found);
    }
};

#endif
