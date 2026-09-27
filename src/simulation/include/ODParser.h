/**
 * \file ODParser.h
 */

#ifndef INCLUDE_ODPARSER_H_
#define INCLUDE_ODPARSER_H_

//DEBUG
#include "RepastProcess.h"

/**
 * A class to parse an OD-matrix given in the LinTim-CSV-file format. For more information on the OD-matrix, see the LinTim-documentation.
 */
class ODParser {
private:
  size_t size_;
  std::vector<int> OD_;
public:
  /**
   * The constructor of the OD-matrix. Does not do anything! Use parse with a file name to fill the matrix
   */
  ODParser(): size_(0), OD_(){}
  /**
   * Destructor of the OD-matrix.
   */
  ~ODParser(){};
  /**
   * Parses the specified OD-file
   * @param od_file_name the file name
   */
  void parse(std::string od_file_name);
  /**
   * Get the parsed OD-file. parse need to be called first!
   * @return Pointer to the OD-matrix.
   */
  std::vector<int> OD(){return OD_;}
  /**
   * Returns the size of the OD-matrix. Remember that the matrix is quadratic.
   * @return the size
   */
  size_t size() {return size_;}

  /**
   * Returns the OD-entry for the given station ids. Note that you should use the actual station ids of the OD-file!
   * @param station_id_1 the first id
   * @param station_id_2 the second id
   * @return the OD-entry
   */
  int getODEntry(int station_id_1, int station_id_2){ return OD_.at((station_id_1-1)*size_ + station_id_2 - 1);}

  /**
   * Calculate the (weighted) number of passengers in the OD-matrix.
   * @return the number of passengers
   */
  long numberOfPassengers(){
    long count = 0;
    for(size_t index = 0; index < size_*size_; index++){
      count += OD_[index];
    }
    return count;
  }
};




#endif /* INCLUDE_ODPARSER_H_ */
