/**
 * \file StringHelper.h
 * A class for a few helpers to work with strings
 */

#ifndef INCLUDE_STRINGHELPER_H_
#define INCLUDE_STRINGHELPER_H_

#include <vector>
#include <boost/algorithm/string.hpp>

/**
 * Contains a few methods to handle strings in the LinTim file format
 */
class StringHelper{
public:
  /**
   * Trims comment and whitespaces.
   * @param s The string to trim. Trimming is happening inline.
   */
  static void trimComment(std::string & s){
    uint index = s.find("#");
    if (index != std::string::npos) {
      s = s.substr(0, index);
    }
    boost::trim(s);
  }

  /**
   * Splits a csv string into the single parts.
   * @param s the string to split. Needs to be trimmed and comment-freed first!
   * @return a vector of the entries
   */
  static std::vector<std::string> split(std::string & s){
    std::vector<std::string> entries;
    boost::split(entries, s, boost::is_any_of(";"));
    for(size_t i=0; i < entries.size(); i++){
      boost::trim(entries[i]);
    }
    return entries;
  }

  /**
   * Splits a csv string into the single ints.
   * @param s the string to split. Needs to be trimmed and comment-freed first!
   * @return a vector of the integer entries
   */
  static std::vector<int> splitInt(std::string & s){
    std::vector<std::string> string_entries = split(s);
    std::vector<int> entries(string_entries.size());
    for(size_t index=0;index<string_entries.size();index++){
      entries[index] = std::stoi(string_entries[index]);
    }
    return entries;
  }




};



#endif /* INCLUDE_STRINGHELPER_H_ */
