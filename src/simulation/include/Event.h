/**
 * \file Event.h
 * The event class of the simulation, used as nodes in the EAN and the repast::SharedNetwork.
 * For a description of the EAN, see the LinTim-documentation
 */

#ifndef INCLUDE_EVENT_H_
#define INCLUDE_EVENT_H_

#include <iostream>
#include <unordered_set>
#include "AgentId.h"
#include "DirectedVertex.h"
#include "MessagingAgent.h"
#include "unordered_set.hpp"
#include <boost/serialization/export.hpp>
#include <boost/serialization/split_member.hpp>
#include "AgentRequest.h"
class Manager;
class Activity;


/**
 * An event in an EAN. Furthermore this is an agent in the simulation.
 */
class Event: public MessagingAgent {
private:
  friend class boost::serialization::access;
  template <class Archive> void serialize(Archive & ar, const unsigned int version){
    ar & boost::serialization::base_object<MessagingAgent>(*this);
    ar & event_id_;
    ar & station_;
    ar & time_;
    ar & weight_;
    ar & incoming_activities_;
    ar & outgoing_activities_;
    ar & manager_;
  }
  int event_id_;
  int station_;
  int time_;
  double weight_;
  std::unordered_set<Activity *> incoming_activities_;
  std::unordered_set<Activity *> outgoing_activities_;
  Manager * manager_;
public:
  /**
   * A map containing all events mapped to there station_id and the time of the event. event_map_ptr.get().at(station_id).at(time)
   */
  static std::unique_ptr<std::map<int, std::map<int, Event*>>> event_map_ptr;
  /**
   * Standard constructor for an event.
   * @param agent_id The repast::AgentId of the event
   * @param new_ID the event index
   * @param new_station the station id
   * @param new_time the time for the event
   * @param new_weight the weight of the event
   * @param manager the manager to manage the events of the delay
   */
  Event(repast::AgentId agent_id, int new_ID, int new_station, int new_time, double new_weight, Manager* manager);

  /**
   * Create a new event
   * @param agent_id the agent id for the event
   * @param new_ID the event id
   * @param new_station the station id
   * @param new_time the time of the event in the simulation
   * @param new_weight the weight
   * @param incoming_activities all incoming activities
   * @param outgoing_activities all outgoing activities
   * @param manager the manager
   */
  Event(repast::AgentId agent_id, int new_ID, int new_station, int new_time, double new_weight, std::unordered_set<Activity *> incoming_activities,
      std::unordered_set<Activity *> outgoing_activities, Manager* manager);
  /**
   * Empty constructor. Is needed for the repast framework.
   */
  Event();
  /**
   * Destructor
   */
  ~Event();
  /**
   * Getter for the event index
   * @return the event index
   */
  int event_id() const {
    return event_id_;
  }

  /**
   * Set a new event id for the event. Not that the agent id is not changed!
   * @param event_id the new id to set
   */
  void event_id(int event_id){
    event_id_ = event_id;
  }
  /**
   * Getter for the incoming activities. Needs to return a pointer for adding incoming activities from outside the class
   * @return a pointer to the incoming activities
   */
  std::unordered_set<Activity *>* incoming_activities() {
    return &incoming_activities_;
  }

  /**
   * Set a new set for the incoming activities
   * @param incoming_activities the set of acitivities to set
   */
  void incoming_activities(std::unordered_set<Activity*> incoming_activities){
    incoming_activities_ = incoming_activities;
  }
  /**
   * Getter for the outgoing activities. Needs to return a pointer for adding incoming activities from
   * outside the class
   * @return a pointer to the incoming activities
   */
  std::unordered_set<Activity *>* outgoing_activities() {
    return &outgoing_activities_;
  }

  /**
   * Set a new set for the outgoing activities
   * @param outgoing_activities the outgoing activities to set
   */
  void outgoing_activities(std::unordered_set<Activity*> outgoing_activities){
    outgoing_activities_ = outgoing_activities;
  }
  /**
   * Getter for the station id. Not used so far.
   * @return the station id.
   */
  int station() const {
    return station_;
  }

  /**
   * Set a new station for the event
   * @param station the new station id to set
   */
  void station(int station){
    station_ = station;
  }

  /**
   * Getter for the time of the event.
   * @return the time
   */
  int time() const {
    return time_;
  }

  /**
   * Set the time of the event.
   * @param time the new time of the event
   */
  void time(int time){
    Event::event_map_ptr.get()->at(station_).erase(time_);
    time_ = time;
    Event::event_map_ptr.get()->at(station_).insert(std::map<int, Event*>::value_type(time_, this));
  }
  /**
   * Getter for the weight of the event. Not used so far.
   * @return the weight
   */
  double weight() const {
    return weight_;
  }

  /**
   * Set a new weight for the event
   * @param weight the new weight to set
   */
  void weight(double weight){
    weight_ = weight;
  }

  /**
   * Get the manager of the event.
   * @return the manager
   */
  Manager* manager(){
    return manager_;
  }

  /**
   * Set a new manager for the event
   * @param manager the manager to set
   */
  void manager(Manager* manager){
    manager_ = manager;
  }

  /**
   * Set every entry of the event according to the given data
   * @param current_rank the new current_rank of the agent id
   * @param event_id the new event id
   * @param station the new station id
   * @param time the new time
   * @param weight the new weight
   * @param incoming_activities the new incoming activities
   * @param outgoing_activities the new outgoing activities
   * @param manager the new manager
   */
  void set(int current_rank, int event_id, int station, int time, double weight, std::unordered_set<Activity*> incoming_activities,
      std::unordered_set<Activity*> outgoing_activities, Manager* manager){
    id_.currentRank(current_rank);
    event_id_ = event_id;
    station_ = station;
    time_ = time;
    weight_ = weight;
    incoming_activities_ = incoming_activities;
    outgoing_activities_ = outgoing_activities;
    manager_ = manager;
  }

  /**
  * Output method for the event
  * @param os the stream for the output
  * @param event the event to output
  * @return the input stream concatenated with the event
  */
  friend std::ostream& operator<<(std::ostream& os, const Event& event) {
    os << "(" << event.event_id() << ", " << event.time() << ", " << event.weight() << ", " << event.station() << ")";
    return os;
  }



};

namespace std {
/**
 * Hash function for the agents. Needed for using agents in an std::unsorted_set.
 */
template<>
struct hash<Event> : public __hash_base<size_t, Event> {
  /**
   * Uses the hash of the agent id to provide a hash for the event
   * @param event the event to hash
   * @return a hash for the event
   */
  std::size_t operator()(const Event& event) const {
    return event.id().hashcode();
  }
};
}

/**
 * Check if two events are equal by comparing their event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the two events are equal
 */
inline bool operator==(const Event& lhs, const Event& rhs){return lhs.event_id() == rhs.event_id();}
/**
 * Check if two events are unequal by comparing their event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the two events are unequal
 */
inline bool operator!=(const Event& lhs, const Event& rhs){return !operator==(lhs,rhs);}
/**
 * Check if the first event is smaller than the second by comparing the event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the first event is smaller than the second
 */
inline bool operator< (const Event& lhs, const Event& rhs){return lhs.event_id()<rhs.event_id();}
/**
 * Check if the first event is bigger than the second by comparing the event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the first event is bigger than the second
 */
inline bool operator> (const Event& lhs, const Event& rhs){return  operator< (rhs,lhs);}
/**
 * Check if the first event is smaller or equal than the second by comparing the event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the first event is smaller or equal than the second
 */
inline bool operator<=(const Event& lhs, const Event& rhs){return !operator> (lhs,rhs);}
/**
 * Check if the first event is bigger or equal than the second by comparing the event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the first event is bigger or equal than the second
 */
inline bool operator>=(const Event& lhs, const Event& rhs){return !operator< (lhs,rhs);}

/**
 * Compare two event pointers by their time in the EAN
 * @param e1 the first event pointer
 * @param e2 the second event pointer
 * @return whether e1 is earlier than e2
 */
bool compByTime(Event* e1, Event* e2);

/**
 * A package used for representing the event. The event packages are used by repast to interchange events between processes
 */
struct EventPackage {
  /**
   * Serialize the event package and add it to the given archive
   * @param ar the archive to add the package to
   * @param version the version of the package
   */
  template<class Archive> void serialize(Archive & ar, const unsigned int version){
    ar & id;
    ar & starting_rank;
    ar & type;
    ar & current_rank;
    ar & event_id;
    ar & station;
    ar & time;
    ar & weight;
    ar & incoming_activities;
    ar & outgoing_activities;
    ar & manager;
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
   * The event id
   */
  int event_id;
  /**
   * The station
   */
  int station;
  /**
   * The time
   */
  int time;
  /**
   * The weight
   */
  double weight;
  /**
   * The incoming activities
   */
  std::unordered_set<Activity *> incoming_activities;
  /**
   * The outgoing activities
   */
  std::unordered_set<Activity *> outgoing_activities;
  /**
   * The manager
   */
  Manager * manager;

  /**
   * Return the agent id of the event corresponding to the package
   * @return the agent id
   */
  repast::AgentId getId() const {
    return repast::AgentId(id, starting_rank, type);
  }

  EventPackage();
  /**
   * Create a new package from the given parameter
   * @param id the id of the agent id
   * @param starting_rank the starting rank of the agent id
   * @param type the type of the agent id
   * @param current_rank the current rank of the agent id
   * @param event_id the event id
   * @param station the station id
   * @param time the time
   * @param weight the weight
   * @param incoming_activities the incoming activities
   * @param outgoing_activities the outgoing activities
   * @param manager the manager
   */
  EventPackage(int id, int starting_rank, int type, int current_rank, int event_id, int station, int time, double weight, std::unordered_set<Activity *> incoming_activities,
      std::unordered_set<Activity *> outgoing_activities, Manager * manager);
};

/**
 * Class for packing events into packages and processing agent requests. These packages are then serialized and sent to a new process
 */
class EventPackageProvider {
private:
  repast::SharedContext<MessagingAgent>* agents;
public:
  /**
   * Create a new provider and store the pointer to the context. This is used to process agent requests later
   * @param agentPtr a pointer to the context. Every agent processed in an agent request later needs to be in the context by the time of processing.
   */
  EventPackageProvider(repast::SharedContext<MessagingAgent>* agentPtr);
  /**
   * Create a new package from the given event and add it to the vector of packages. The package is added at the end of the vector
   * @param agent the agent to add
   * @param out the vector to add the new package to
   */
  void providePackage(Event* agent, std::vector<EventPackage>& out);
  /**
   * Create a new package for each agent in the agent request and add all of them to the end of the given vector
   * @param req the agent request to process
   * @param out the vector to add the new packages to
   */
  void provideContent(repast::AgentRequest req, std::vector<EventPackage>& out);
};

/**
 * Class for processing packages and create new agents or update existing agents from the package.
 */
class EventPackageReceiver {
private:
  repast::SharedContext<MessagingAgent>* agents;
public:
  /**
   * Create a new receiver by storing a pointer to the shared context. Every package processed by
   * updateAgent needs to have a corresponding agent present in the context
   * @param agentPtr a pointer to the context
   */
  EventPackageReceiver(repast::SharedContext<MessagingAgent>* agentPtr);
  /**
   * Create a new agent from the given package
   * @param package the package to process
   * @return a new event created from the package
   */
  Event* createAgent(EventPackage package);
  /**
   * Update an existing agent with the information of the package. The agent is pulled from the context by the agent id and then all information are updated
   * @param package the package to process
   */
  void updateAgent(EventPackage package);
};



#endif /* INCLUDE_EVENT_H_ */
