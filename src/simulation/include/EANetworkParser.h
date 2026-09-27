/**
 * \file EANetworkParser.h
 * Parses the EAN out of the corresponding LinTim files
 * For a description of an EAN, see the LinTim-documentation
 */

#ifndef INCLUDE_EANETWORKPARSER_H_
#define INCLUDE_EANETWORKPARSER_H_

#include "Properties.h"
#include "SharedNetwork.h"
#include "SharedContext.h"
#include "ActivityContentManager.h"
#include "Event.h"
#include "ODParser.h"
#include "SourceDelayMessage.h"
#include "Manager.h"
#include "Path.h"
#include "shortest_pathsv.h"

/**
 * Method for assigning passengers to different ranks. Here, the od-pairs are iterated and the
 * first ranks are filled first.
 */
#define FIRST_OD_FIRST_RANK 0
/**
 * Method for assigning passengers to different ranks. Here, the od-pairs are sorted by weight
 * and afterwards are alternatingly assigned to the different ranks.
 */
#define GREEDY_DISTRIBUTION 1

/**
 * A simple heap dijkstra implementation for determining the initial paths of the passengers. Can add more SP-algo methods here
 */
#define HEAP_DIJKSTRA 0

/**
 * Vibjors Dijkstra implementation using the periodic structure of the EAN to calculate shortest paths efficiently
 */
#define VIBHOR_DIJKSTRA 1

/**
 * The class to parse the CSV-files of the EAN.
 */
class EANetworkParser {
private:
  /**
   * An entry in the OD matrix, used for distributing the passengers on the different ranks
   */
  struct ODEntry{
    size_t origin_id;
    size_t destination_id;
    int weight;
  };
  /**
   *  Try to distribute the load of the passengers on the different ranks. An actual distribution method is given in the props file
   *  @param OD the OD matrix to distribute
   *  @param passenger_distribution the resulting passenger distributuin is saved here. passenger_distribution[rank] is the list of all
   *  ODEntries that should be created on rank.
   *  @param actual_number_of_passengers_per_rank the actual passengers that are assigned to each rank in the passenger_distribution.
   *  @param props a pointer to the property file to read all necessary parameters
   *
   */
  static void distributeLoadOnRanks(ODParser & OD, std::vector<std::list<ODEntry>>& passenger_distribution,
      std::vector<long>& actual_number_of_passengers_per_rank, repast::Properties* props);

  /**
   * Calculate all possible shortest paths in the EAN for the given OD path, using a simple heap dijkstra implementation
   * @param shortest_paths a vector of the shortest paths in the EAN
   * @param origin_id the origin id
   * @param destination_id the destination id
   * @param id_to_event_map A map mapping the ptn stop ids to all corresponding departure events in the EAN
   * @return the number of shortest paths found
   */
  static int calculatePaths(std::vector<Path>& shortest_paths, int origin_id, int destination_id,
      std::map<int, std::unordered_set<Event*>>& id_to_event_map);

  /**
   * Calculate all possible shortest paths in the EAN for the given OD path, using a dijkstra implementation on the periodic EAN
   * @param shortest_paths a vector of the shortest paths in the EAN
   * @param origin_id the origin id
   * @param destination_id the destination id
   * @param id_to_event_map A map mapping the ptn stop ids to all corresponding departure events in the EAN
   * @param sp the shortest path algorithm
   * @param context the context
   * @param EAN the EAN
   * @return the number of shortest paths found
   */
  static int calculatePaths(std::vector<Path>& shortest_paths, int origin_id, int destination_id,
      std::map<int, std::unordered_set<Event*>>& id_to_event_map, shortest_pathsv& sp, repast::SharedContext<MessagingAgent>& context,
      repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN);



  /**
   * Compare the two given ODEntrys by their weight
   * @param e1 the first ODEntry
   * @param e2 the second ODEntry
   * @return whether e1 is smaller than e2
   */
  static bool compByWeight(ODEntry e1, ODEntry e2);
public:



  /**
   * Delivers an EAN from the input files. Need to be parsed first!
   * @param event_file_name the file name of the expanded events
   * @param act_file_name the file name of the expanded activities
   * @param context the context to which the network should be projected
   * @param edgeContentManager the edge content manager of the network
   * @param id_to_event_map a map to fill with the events
   * @param manager the manager to magage delays on the generated events
   * @return the EAN, already applied to the context
   */
  static repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager>* applyNetwork(
      std::string event_file_name, std::string act_file_name, repast::SharedContext<MessagingAgent> * context,
      ActivityContentManager * edgeContentManager,
      std::map<int, std::unordered_set<Event*>>& id_to_event_map, Manager* manager);

  /**
   * Apply passengers regarding the OD-matrix. The OD-pairs are splitted equally between the possible departure events.
   * @param OD the OD-Parser to get the OD-matrix
   * @param context the context to add the passengers
   * @param EAN the EAN
   * @param id_to_event_map a map to get the events per station id
   * @param props the property file to read all necessary parameters
   */
  static void applyPassengersByODMatrix(ODParser & OD, repast::SharedContext<MessagingAgent> * context,
      repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN,
      std::map<int, std::unordered_set<Event*>>& id_to_event_map, repast::Properties* props);

  /**
   * Read in source delays from the given file and add them to the delay map.
   * @param delay_map the map the add the delays. The key is the time the delay is revealed
   *  (here: the time of the target event of the delayed activity), the value is the source delay
   * @param delay_file_name the name of the file of the delays. This file should be a LinTim-formated delay file of activities.
   * @param debug_level the debug level of the output. 0 for none, 1 for normal, 2 for all
   */
  static void readDelays(std::map<int, std::unordered_set<SourceDelayMessage>> & delay_map, std::string delay_file_name, int debug_level);



};



#endif /* INCLUDE_EANETWORKPARSER_H_ */
