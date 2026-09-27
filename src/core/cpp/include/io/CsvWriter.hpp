#ifndef CSVWRITER_HPP
#define CSVWRITER_HPP

/**
 * Helper class to write csv-Files, used for the formatting of the files. All IOExceptions are
 * raised to the caller
 */

#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <iostream>
#include <iomanip>
#include <stdio.h>
#include <functional>
#include "../exception/exceptions.hpp"
#include <cmath>


class CsvWriter{
private:
    //Logger logger = Logger.getLogger("net.lintim.io.CsvWriter");

    /**
	 * The writer of this class. All writing happens through this class member.
	 */
    std::ofstream ofs;
    static std::string getParentDirectoryName(std::string filePath){
        size_t found = filePath.find_last_of("/\\");
        return filePath.substr(0, found);
    }
public:
    /**
	 * Create a new CsvWriter instance for a specific file. The file is opened with the given relative file name
	 * @param fileName the relative file name
	 * @throws IOException if the file cannot be opened or written to
	 */
    CsvWriter(std::string fileName){
        open(fileName);
    }

    /**
     * Create a new CsvWriter instance for a specific file. The file is opened with the given relative file name
     * @param fileName the relative file name
     * @param header the header to write in the first line
     * @throws IOException if the file cannot be opened or written to
     */
    CsvWriter(std::string fileName, std::string header){
        open(fileName, header);
    }
    CsvWriter(){}

    ~CsvWriter(){
        ofs.close();
    }

    void open(std::string fileName, std::string header){
        std::remove(fileName.c_str());
        ofs.open(fileName.c_str());
        if (!ofs.good()){
            ofs.close();
            std::cout << "Creating path to output" << std::endl;
            std::system(("mkdir -p "+getParentDirectoryName(fileName)).c_str());
            ofs.open(fileName.c_str());
            if (!ofs.good()) std::cout << "not successful" << std::endl;
        }

        if (!header.empty()) ofs << "#" + header << std::endl;
    }

    void open(std::string fileName){
        open(fileName, "");
    }

    /**
	 * Write a line to the csv-file. The given values are csv-formatted and written to the file of this writer
	 * instance.
	 * @param values the values to write
	 * @throws IOException if the file, this instance was created with, cannot be written to
	 */
    void writeLine(std::vector <std::string>& values){
        if(values.size() == 0){
            //There is nothing to print
            return;
        }
        //Now print the first values.length-1 items, each followed by a ";"
        for(int i = 0; i < (int) values.size() - 1; i++){
            ofs << values[i] + "; ";
        }
        //Now print the last item, without ";"
        ofs << values[values.size() - 1] << std::endl;
    }

    /**
	 * Close the file after all writing has been done
	 */
    void close(){
        ofs.close();
    }

    template<typename T>
    void writeList(std::vector <T> list,
                   std::vector<std::string> (T::*outputFunction)(void)){
        for(T object : list){
            auto d = std::bind(outputFunction, object);
            std::vector <std::string> pass = d();
            writeLine(pass);
        }
    }

    template<typename T>
    void full_write(std::string filename, std::vector <T> list,
                    std::vector<std::string> (T::*outputFunction)(void), std::string header = ""){
        open(filename, header);
        for(T object : list){
            auto d = std::bind(outputFunction, object);
            std::vector <std::string> pass = d();
            writeLine(pass);
        }
        close();
    }

    /**
     * Shorten the given double value to a string representation with at most two decimal places.
     * @param value the value to shorten
     * @return the shortened representation
     */
    static std::string shortenDecimalValueForOutput(double value) {
        if(fabs(value - round(value)) < 0.005) {
            return std::to_string((long long) round(value));
        }
        std::stringstream stream;
        stream << std::fixed << std::setprecision(2) << value;
        return stream.str();
    }

    /**
     * Use {@link #shortenDecimalValueForOutput(double)} on numbers, return the input for the rest
     * @param value the value to shorten
     * @return the input value or the shortened representation, if its a number
     */
    static std::string shortenDecimalValueIfItsDecimal(std::string value) {
        try {
            return shortenDecimalValueForOutput(std::stod(value));
        }
        catch(...) {
            return value;
        }
    }
};

#endif
