/**
 * \file PathCalculator.h
 * A brief description of the file
 * More details about this file
 */

#ifndef INCLUDE_PATHCALCULATOR_H_
#define INCLUDE_PATHCALCULATOR_H_

#include <boost/heap/fibonacci_heap.hpp>
#include "SharedNetwork.h"
#include "Properties.h"
#include "Event.h"
#include "Path.h"
#include "shortest_pathsv.h"

/**
 * Can calculate a path in the EAN. Is the superclass for the different calculators
 */
class PathCalculator {
public:
  /**
   * Return a path in the EAN.
   * @param source the source event
   * @param target_station_id the id of the station to reach
   * @return The new path.
   */
  static Path calculatePath(Event & source, int target_station_id);

  virtual ~PathCalculator(){};
};

/**
 * Dijkstra implementation for calculating shortest paths. Uses the fibonacci heaps of the boost library.
 */
class Dijkstra : public PathCalculator {
private:
  static Path reconstructPath(Event & source, Event & target, std::map<Event, Event> & predecessors);
  struct Node{
    Event event;
    int distance;
    Node(Event event, int distance) : event(event), distance(distance){};
    Node() : event(), distance(0){};
  };
  struct Compare_node{
    //This will give a min-heap!
    bool operator()(const Node& n1, const Node& n2) const{
      return n1.distance > n2.distance;
    }
  };
public:
  /**
     * Return a path in the EAN.
     * @param source the source event
     * @param target_station_id the id of the station to reach
     * @return The new path.
     */
  static Path calculatePath(Event & source, int target_station_id);
  ~Dijkstra(){

//    if(repast::RepastProcess::instance()->rank()==0){
//      std::cout << "Dijkstra destructor" << std::endl;
//    }
  }
};

/**
 * The label for the Dijkstra algorithm.
 */
enum class Label { TEMP, PERM };

/**
 * Representation of the labels by printing the names
 * @param os the stream
 * @param label the label
 * @return the stream concatenated with the name of the label
 */
inline std::ostream& operator<<(std::ostream& os, const Label& label) {
    if(label == Label::TEMP){
      os << "TEMP";
    }
    else{
      os << "PERM";
    }
    return os;
}

/*class DijkstraVibhor : public PathCalculator {
private:
  shortest_pathsv sp;
public:
  DijkstraVibhor(repast::Properties * props){
    std::string data_folder_location = props->getProperty("data_folder_location");
    sp.read_events_periodic(data_folder_location+"Events-periodic.giv");
    sp.read_activities_periodic(data_folder_location + "Activities-periodic.giv");
    sp.read_events_expanded(data_folder_location + "Events-expanded.giv");
    sp.read_activities_expanded(data_folder_location + "Activities-expanded.giv");
  }
  Path calculatePath(Event & source, int target_station_id){
    std::cout << "Calling VibhorDijkstra on " << source << " and " << target_station_id << std::endl;
    int lengthOfPath = sp.dijkstra(source.station(), target_station_id, source.time());
    if(lengthOfPath == -1){
      std::stringstream es;
      es << "No path between " << source << " and station " << target_station_id;
      throw std::runtime_error(es.str());
    }
    return sp.reconstructPath(source.station(), target_station_id);
  }
  ~DijkstraVibhor(){}
};*/




#endif /* INCLUDE_PATHCALCULATOR_H_ */
