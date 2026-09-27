/**
 * \file Passenger.h
 * The file containing the passenger class
 */

#ifndef INCLUDE_PASSENGER_H_
#define INCLUDE_PASSENGER_H_

#include "SharedNetwork.h"
#include "Event.h"
#include "Path.h"
#include <boost/serialization/export.hpp>
#include <boost/serialization/split_member.hpp>


/**
 * The online persona. A passenger will choose a new path for every delay on his intended path
 */
#define ONLINE 1
/**
 * The offline persona. A passenger only determines a new path if his old path has a change that is not maintained.
 */
#define OFFLINE 2

/**
 * The passenger class.
 */
class Passenger: public MessagingAgent{
private:
  friend class boost::serialization::access;
  template<class Archive> void serialize(Archive & ar, const unsigned int version){
    ar & boost::serialization::base_object<MessagingAgent>(*this);
    ar & current_path_;
    ar & current_activity_;
    ar & target_station_id_;
    ar & stranded_;
    ar & persona_;
  }
  Path current_path_;
  Activity* current_activity_;
  int target_station_id_;
  bool stranded_;
  int persona_;
public:
  /**
   * Advances to the next activity in the path.
   */
  void advance();
  ~Passenger(){

//    if(repast::RepastProcess::instance()->rank()==0){
//      std::cout << "Passenger destructor" << std::endl;
//    }
  }
  /**
   * Standard constructor for a new passenger
   * @param id the agent if of the new passenger
   * @param path the path of the passenger
   * @param activity the current activity of the passenger in the path
   * @param target_station_id the id of the station the passenger wants to reach. Needed for possible rerouting of the passenger later.
   * @param persona The persona of the passenger. Determines the handling of delays
   */
  Passenger(
      repast::AgentId id,
      Path path,
      Activity* activity,
      int target_station_id, int persona) : current_path_(path), current_activity_(activity), target_station_id_(target_station_id), stranded_(false), persona_(persona) { id_ = id;}

  /**
   * Construct a new passenger from the given parameters
   * @param id the new agent id
   * @param path the new path of the passenger
   * @param activity the current activity
   * @param target_station_id the target station id
   * @param stranded whether the passenger is stranded
   * @param persona the persona
   */
  Passenger(repast::AgentId id, Path path, Activity* activity, int target_station_id, bool stranded, int persona):
    current_path_(path), current_activity_(activity), target_station_id_(target_station_id), stranded_(stranded), persona_(persona) {id_ = id;}

  Passenger(){
    id_=repast::AgentId();
    current_path_ = Path();
    current_activity_ = NULL;
    target_station_id_ = -1;
    stranded_ = false;
    persona_ = -1;
  }
  /**
   * Getter for the current path of the passenger.
   * @return the path
   */
  const Path current_path() const{
    return current_path_;
  }
  /**
     * Output method for the activity
     * @param os the stream for the output
     * @param pass the passenger to output
     * @return the input stream concatenated with the passenger
     */
  friend std::ostream& operator<<(std::ostream& os, const Passenger& pass){
    os << "(" << pass.id() << ", " << pass.current_path() << ", dest_id: " << pass.target_station_id() << ")";
    return os;
  }

  /**
   * Return the target station id of the passenger
   * @return the target station id
   */
  int target_station_id() const{
    return target_station_id_;
  }

  /**
   * Set the target station id of the passenger
   * @param target_station_id the new target station id
   */
  void target_station_id(int target_station_id){
    target_station_id_ = target_station_id;
  }

  /**
   * Checks if any event in the path was delayed and if yes, search a new shortest path in the network.
   * @param delayed_events a set of events that were delayed
   * @param debug_level the debug level of the simulation. Controls the amount of output the function produces.
   */
  void chooseNewPath(std::unordered_set<Event> delayed_events, int debug_level);

  /**
   * Checks if any event from the event set is contained in the current path of the passenger
   * @param event_set the set of events to check for
   * @return whether any event of the set is in the path
   */
  bool hasEvent(std::unordered_set<Event>& event_set){
    return current_path_.hasEvent(event_set);
  }

  /**
   * Returns whether the passenger is stranded. A passenger is stranded if there is no way from his current location to his target station. This can
   * happen due to not maintained changes.
   * @return whether the passenger is stranded
   */
  bool stranded() const{
    return stranded_;
  }

  /**
   * Return the persona of the passenger
   * @return the persona of the passenger
   */
  int persona() const{
	  return persona_;
  }

  /**
   * Return the current activity of the passenger, i.e. the activity the passenger is currently travelling on
   * @return the current activity
   */
  Activity & current_activity() const {
    return *current_activity_;
  }

  /**
   * Set all the parameters of the passenger to the new given values
   * @param current_rank set the current rank of the agent id of the passenger
   * @param current_path the new path of the passenger
   * @param current_activity the new current activity
   * @param target_station_id the new target station id
   * @param stranded the new stranded value
   * @param persona the new persona
   */
  void set(int current_rank, Path current_path, Activity* current_activity, int target_station_id, bool stranded, int persona){
    id_.currentRank(current_rank);
    current_path_ = current_path;
    current_activity_ = current_activity;
    target_station_id_ = target_station_id;
    stranded_ = stranded;
    persona_ = persona;
  }

};

/**
 * Determine if two passengers are equal by their AgentId
 * @param lhs the first passenger
 * @param rhs the second passenger
 * @return whether the AgentIds of the two passengers are equal
 */
inline bool operator==(const Passenger & lhs, const Passenger & rhs){
  return lhs.getId() == rhs.getId();
}

/**
 * Determine if two passengers are unequal by their AgentId
 * @param lhs the first passenger
 * @param rhs the second passenger
 * @return whether the AgentIds of the two passengers are unequal
 */
inline bool operator!=(const Passenger & lhs, const Passenger & rhs){
  return !(lhs == rhs);
}
/**
 * A package used for representing a passenger. The passenger packages are used by repast to interchange passengers between processes
 */
struct PassengerPackage {
  /**
   * Serialize the package to change processes
   * @param ar the archive to add the data to
   * @param version the version of the package
   */
  template<class Archive> void serialize(Archive & ar, const unsigned int version){
    ar & id;
    ar & starting_rank;
    ar & type;
    ar & current_rank;
    ar & current_path;
    ar & current_activity;
    ar & target_station_id;
    ar & stranded;
    ar & persona;
  }
  /**
   * the id of the corresponding agent
   */
  int id;
  /**
   * The starting rank of the agent id
   */
  int starting_rank;
  /**
   * The type of the agent id
   */
  int type;
  /**
   * The current rank of the agent id
   */
  int current_rank;
  /**
   * The current path of the passenger
   */
  Path current_path;
  /**
   * The current activty of the passenger
   */
  Activity* current_activity;
  /**
   * The target station id of the passenger
   */
  int target_station_id;
  /**
   * Whether the passenger is stranded
   */
  bool stranded;
  /**
   * The persona of the passenger
   */
  int persona;

  /**
   * Return the agent Id of the passenger
   * @return the agent id
   */
  repast::AgentId getId() const{
    return repast::AgentId(id, starting_rank, type);
  }

  PassengerPackage();

  /**
   * Construct a new passenger package.
   * @param id the id of the agent id of the passenger
   * @param starting_rank the starting rank of the agent id of the passenger
   * @param type the type of the agent id of the passenger
   * @param current_rank the current rank of the agent id of the passenger
   * @param current_path the current path of the passenger
   * @param current_activity the current activity of the passenger
   * @param target_station_id the target station id of the passenger
   * @param stranded whether the passenger is stranded
   * @param persona the persona of the passenger
   */
  PassengerPackage(int id, int starting_rank, int type, int current_rank, Path current_path, Activity* current_activity, int target_station_id, bool stranded, int persona);
};

/**
 * Class for packing passengers and processing agent requests. These packages are then serialized and set to another process
 */
class PassengerPackageProvider{
private:
  repast::SharedContext<MessagingAgent>* agents;
public:
  /**
   * Create a new provider and store the pointer to the context. This is used to process agent requests later.
   * @param agentPtr a pointer to the context
   */
  PassengerPackageProvider(repast::SharedContext<MessagingAgent>* agentPtr);
  /**
   * Create a new package from the given passenger and add it to the vector of packages. The package is added at the end of the vector.
   * @param agent the agent to add
   * @param out the vector to add the new package to
   */
  void providePackage(Passenger* agent, std::vector<PassengerPackage>& out);
  /**
   * Create a new package for each agent in the agent request and add all of them to the end of the given vector
   * @param req the agent request to process
   * @param out the vector to add the new packages to
   */
  void provideContent(repast::AgentRequest req, std::vector<PassengerPackage>& out);
};

/**
 * Class for processing packages and create new agents or update existing agents from the package.
 */
class PassengerPackageReceiver{
private:
  repast::SharedContext<MessagingAgent>* agents;
public:
  /**
   * Create a new receiver by storing a pointer to the shared context. Every package processed by
   * updateAgent needs to have a corresponding agent present in the context
   * @param agentPtr a pointer to the context
   */

  PassengerPackageReceiver(repast::SharedContext<MessagingAgent>* agentPtr);
  /**
   * Create a new agent from the given package
   * @param package the package to process
   * @return a new passenger created from the package
   */
  Passenger* createAgent(PassengerPackage package);
  /**
   * Update an existing agent with the information of the package. The agent is pulled from the context by the agent id and then all information are updated
   * @param package the package to process
   */
  void updateAgent(PassengerPackage package);
};


#endif /* INCLUDE_PASSENGER_H_ */
