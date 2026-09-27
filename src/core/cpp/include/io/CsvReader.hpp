#ifndef CSVREADER_HPP
#define CSVREADER_HPP

#include "../exception/exceptions.hpp"
#include <string>
#include <sstream>
#include <fstream>
#include <iostream>
#include <vector>
#include <functional>

/**
 * File to process a csv file. Comments in the form of "#..." are trimmed from the end of the lines. Empty lines are
 * ignored. Other lines will be split by ";" and processed by a given Builder class.
 */
class CsvReader{
public:
    /**
     * Read the file with the given name line by line and process each line with the given processor. The lines are
     * trimmed for comments ("#...") and whitespaces before splitted by ";". The tokens are then given to a
     * function to process the content of the line
     * @param fileName the name of the file to read
     * @param processor the processor to process every line. First argument are the tokens of the line, second
     *                  argument the line number (used for error messages)
     */
     template<typename O>
     void readCsv(std::string file_name, O reader_object, void (O::*processor)(std::vector<std::string>, int)){
         std::ifstream file;

         try{
             file.open(file_name.c_str());
             if (!file) {
                 std::cout << "File " << file_name << " does not exist" << std::endl << std::flush;
                 throw InputFileException(file_name);
             }
             std::string line;
             int lineIndex = 0;
             while(std::getline(file, line, '\n')){
                 lineIndex++;
                 //if (line.empty()) break;
                 //First trim the comments, if there are any
                 size_t it = line.find('#');
                 if(it != std::string::npos){
                     line = line.substr(0, it);
                 }
                 //Empty lines can be ignored
                 if(line.empty()){
                     continue;
                 }

                 size_t pos = 0;
                 std::vector <std::string> tokens;
                 std::string delimiter = ";";
                 while((pos = line.find(delimiter)) != std::string::npos){
                     tokens.push_back(line.substr(0, pos));
                     line.erase(0, pos + delimiter.length());
                 }
                 tokens.push_back(line);
                 //Let the processor process the line
                 auto writeIt = std::bind(processor, reader_object, tokens, lineIndex);
                 writeIt();
             }


         }
         catch(const std::ifstream::failure& e){
             std::cout << "Exception opening/reading " + file_name;
         }
         file.close();
     }

    /**
	 * Read the given file and determine the number of lines, without empty lines,
   * lines only containing whitespaces and lines only containing whitespaces and comments
	 * @param file_name the name of the file
	 * @return the number of lines with content of this file
	 */
    int getNumberOfLines(std::string file_name){
        std::ifstream file;
        try{
            file.open(file_name.c_str());
            std::string line;
            int number_of_lines = 0;
            while(std::getline(file, line)){
                if(line[0] != '#' && !line.empty()){
                    number_of_lines++;
                }
            }
            file.close();
            return number_of_lines;
        }
        catch(const std::ifstream::failure& e){
            std::cout << "Exception opening/reading " + file_name;
            file.close();
            return -1;
        }

    }
};

#endif
