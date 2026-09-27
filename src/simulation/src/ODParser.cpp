/**
 * \file ODParser.cpp
 * The implementation of the OD parser.
 */

#include <fstream>
#include <vector>
#include <boost/algorithm/string.hpp>
#include "StringHelper.h"
#include "ODParser.h"

void ODParser::parse(std::string od_file_name){
  std::ifstream od_file;
  od_file.open(od_file_name.c_str(), std::ifstream::in);
  std::string cur_line;
  std::vector<int> entries;
  int cur_max = 0;
  if (od_file.is_open()) {
    //First go through the file and check for the number of passengers. For that, check every first entry and keep track
    //of the biggest.
    while (getline(od_file, cur_line)) {
      //Strip away any comment
      StringHelper::trimComment(cur_line);
      if (cur_line.empty()) {
        continue;
      }
      entries = StringHelper::splitInt(cur_line);
      if (entries.size() != 3) {
        std::cerr << "There were " << entries.size() << "entries in the line "
            << cur_line << "instead of 3!" << std::endl;
        throw std::runtime_error("Invalid format of the OD-file!");
      }
      if (entries[0] > cur_max) {
        cur_max = entries[0];
      }
    }
    //Now that we found the biggest station id, we can initialize the OD-matrix and set the size
    size_ = cur_max;
	OD_.resize(size_*size_);
    //Now iterate again over the file and fill the OD-matrix
    od_file.clear();
    od_file.seekg(0, std::ios::beg);
    while (getline(od_file, cur_line)) {
      //Strip away any comment
      StringHelper::trimComment(cur_line);
      if (cur_line.empty()) {
        continue;
      }
      entries = StringHelper::splitInt(cur_line);
      //Fill the two dimensional OD-matrix in an one dimensional array
      OD_[(entries[0] - 1) * size_ + entries[1] - 1] = entries[2];
    }
  }
  else {//Could not open the OD-file
    std::cerr << "Could not open OD-file " << od_file_name << std::endl;
    throw std::runtime_error("Could not open OD-file!");
  }
}

