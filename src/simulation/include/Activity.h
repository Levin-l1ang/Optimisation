/**
 * \file Activity.h
 * The activity class
 * Activities are the edges in the EAN and in the repast::SharedNetwork. Every activity keeps track of the passengers
 * currently travelling over the edge.
 */

#ifndef INCLUDE_ACTIVITY_H_
#define INCLUDE_ACTIVITY_H_

#include <iostream>
#include <boost/shared_ptr.hpp>
#include "AgentId.h"
#include "Edge.h"
#include "MessagingAgent.h"
#include "Event.h"
#include <boost/serialization/access.hpp>
#include <boost/serialization/export.hpp>
#include <boost/serialization/split_member.hpp>
#include "Passenger.h"
#include "Manager.h"
#include <unordered_set>
#include "unordered_set.hpp"

/**
 * The activity class for an activity in the EAN.
 */
class Activity: public repast::RepastEdge<Event> {
private:
  friend class boost::serialization::access;
  template<class Archive> void serialize(Archive & ar, const unsigned int version){
    ar & activity_id_;
    ar & lower_bound_;
    ar & type_;
    ar & weight_;
    ar & _source;
    ar & _target;
    ar & local_agents_;
  }
  int activity_id_;
  int lower_bound_;
  std::string type_;
  double weight_;
  Event * _source;
  Event * _target;
  std::unordered_set<Passenger *> local_agents_;
  //Use unique_ptr to ensure memory freeing at end of program
  static std::unique_ptr<std::map<std::pair<int, int>, Activity *>> act_map_ptr_;
  static std::unique_ptr<std::map<int, Activity *>> act_by_id_map_ptr_;
  static int maxActivityId_;
public:

  Activity();
  /**
   * The standard constructor to create an activity
   * @param activity_id the new activity id
   * @param lower_bound the new lower bound
   * @param type the type of the activity
   * @param weight the weight of the activity. In LinTim this is the expected number of passengers travelling the edge
   * @param source the source event of the activity
   * @param target the target event of the activity
   */
  Activity(int activity_id, int lower_bound, std::string type, double weight, Event* source, Event* target);
  /**
   * The constructor that additionally sets the local agents
   * @param activity_id the new activity id
   * @param lower_bound the new lower bound
   * @param type the type of the activity
   * @param weight the weight of the activity. In LinTim this is the expected number of passengers travelling the edge
   * @param source the source event of the activity
   * @param target the target event of the activity
   * @param local_agents the agents currently travelling on the activity
   */
  Activity(int activity_id, int lower_bound, std::string type, double weight, Event* source, Event* target,
      std::unordered_set<Passenger *> local_agents);
  /**
   * Creates an activity from two events. Therefore gets the activity parsed from the EAN with the same target
   * and source event ids. Is needed for the repast framework.
   * @param tail_event_ptr the source of the activity
   * @param head_event_ptr the target of the activity
   */
  Activity(boost::shared_ptr<MessagingAgent> tail_event_ptr, boost::shared_ptr<MessagingAgent> head_event_ptr);
  /**
   * Creates an activity from two events. Therefore gets the activity parsed from the EAN with the same target
   * and source event ids. Is needed for the repast framework.
   * @param tail_event_ptr the source of the activity
   * @param head_event_ptr the target of the activity
   * @param weight the weight of the activity
   */
  Activity(boost::shared_ptr<MessagingAgent> tail_event_ptr, boost::shared_ptr<MessagingAgent> head_event_ptr,
      double &weight);
  /**
   * Copy constructor of an activity. Is needed for the repast framework
   * @param other the activity to copy
   */
  Activity(Activity const & other);
  ~Activity() {
//    if(repast::RepastProcess::instance()->rank()==0){
//      std::cout << "Activity destructor" << std::endl;
//    }
    local_agents_ = std::unordered_set<Passenger *>();
  }
  ;
  /**
   * Adds an agent to the activity.
   * @param agent the agent to add
   */
  void addAgent(Passenger * agent) {
    local_agents_.insert(agent);
  }
  /**
   * Removes an agent of the activity.
   * @param agent the agent to remove
   * @return whether the agent was present in the Activity.
   */
  bool removeAgent(Passenger * agent) {
    return local_agents_.erase(agent);
  }

  /**
   * Get a pointer to the activity map, i.e. a map matching the source- and target event indices to the activity.
   * @return a pointer to the activity map
   */
  static std::map<std::pair<int, int>, Activity *> * act_map_ptr() {
    return act_map_ptr_.get();
  }

  /**
   * Returns a pointer a map with key activity id and value activity pointer
   * @return the activity by id map
   */
  static std::map<int, Activity *> * act_by_id_map_ptr() {
    return act_by_id_map_ptr_.get();
  }

  /**
   * Returns the current maximal activity id, i.e. the maximal id allocated by the constructors by now
   * @return the maximal activity id
   */
  static int maxActivityId(){
    return maxActivityId_;
  }

  /**
   * Set a new maximal acitivity id.
   * @param newId the id to set
   */
  static void maxActivityId(int newId){
    maxActivityId_ = newId;
  }
  /**
   * Getter for the activity id
   * @return the activity id
   */
  int activity_id() const {
    return activity_id_;
  }
  /**
   * Getter for the lower bound. Not used so far.
   * @return the lower bound
   */
  int lower_bound() const {
    return lower_bound_;
  }
  /**
   * Getter for the activity type. Not used so far.
   * @return the type of the activity
   */
  std::string type() const {
    return type_;
  }
  /**
   * Getter for the weight. Not used so far.
   * @return the weight of the activity
   */
  double weight() const {
    return weight_;
  }
  /**
   * Getter for the local agents
   * @return the local agents
   */
  std::unordered_set<Passenger *> local_agents() const {
    return local_agents_;
  }
  /**
   * Getter for the source
   * @return the source
   */
  Event * source() const {
    return _source;
  }
  /**
   * Getter for the target
   * @return the target
   */
  Event * target() const {
    return _target;
  }
  /**
   * Output method for the activity
   * @param os the stream for the output
   * @param act the activity to output
   * @return the input stream concatenated with the activity
   */
  friend std::ostream& operator<<(std::ostream& os, const Activity& act) {
    os << "(" << act.activity_id() << ", " << *act.source() << ", " << *act.target() << ")";
    return os;
  }
  /**
   * Getter for a pointer of the local agents. Is needed to access the local agents from out out the class
   * @return
   */
  std::unordered_set<Passenger *> * local_agent_pointer() {
    return &local_agents_;
  }
};

/**
 * Check if two events are equal by comparing their event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the two events are equal
 */
inline bool operator==(const Activity& lhs, const Activity& rhs){return lhs.activity_id() == rhs.activity_id();}
/**
 * Check if two events are unequal by comparing their event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the two events are unequal
 */
inline bool operator!=(const Activity& lhs, const Activity& rhs){return !operator==(lhs,rhs);}
/**
 * Check if the first event is smaller than the second by comparing the event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the first event is smaller than the second
 */
inline bool operator< (const Activity& lhs, const Activity& rhs){return lhs.activity_id()<rhs.activity_id();}
/**
 * Check if the first event is bigger than the second by comparing the event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the first event is bigger than the second
 */
inline bool operator> (const Activity& lhs, const Activity& rhs){return  operator< (rhs,lhs);}
/**
 * Check if the first event is smaller or equal than the second by comparing the event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the first event is smaller or equal than the second
 */
inline bool operator<=(const Activity& lhs, const Activity& rhs){return !operator> (lhs,rhs);}
/**
 * Check if the first event is bigger or equal than the second by comparing the event id
 * @param lhs the first event
 * @param rhs the second event
 * @return whether the first event is bigger or equal than the second
 */
inline bool operator>=(const Activity& lhs, const Activity& rhs){return !operator< (lhs,rhs);}


#endif /* INCLUDE_ACTIVITY_H_ */
