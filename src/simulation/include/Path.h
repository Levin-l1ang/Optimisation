/**
 * \file Path.h
 * Class of a path of a passenger
 */

#ifndef INCLUDE_PATH_H_
#define INCLUDE_PATH_H_

#include <algorithm>
#include <boost/serialization/vector.hpp>
#include <boost/serialization/split_member.hpp>
#include "SharedNetwork.h"
#include "Event.h"
class ActivityContentManager;
struct ActivityContent;

/**
 * A path in the EAN.
 */
class Path{
private:
  friend class boost::serialization::access;
  template<class Archive> void serialize(Archive & ar, const unsigned int version){
    ar & activities_;
    ar & current_index_;
    ar & events_;
  }
  std::vector<Activity *> activities_;
  size_t current_index_;
  std::vector<Event *> events_;
public:
  /**
   * Standard constructor of a path in the EAN.
   * @param activities the activities to put in the path
   * @param current_index the current index in the path
   */
  Path (std::vector<Activity *> activities, size_t current_index);

  /**
   * Create a new path out of the given event ids. The corresponding events are pulled from the context given. Note that additional wait edges will be created and added to the network if they are not already present
   * @param eventIds the vector of event ids the path should be constructed of
   * @param context the context of the simulation
   * @param EAN the EAN to add new edges to
   */
  Path (std::vector<int> eventIds, repast::SharedContext<MessagingAgent>& context,
      repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN);

  Path (){
    activities_ = std::vector<Activity *>();
    current_index_ = 0;
    events_ = std::vector<Event *>();
  }
  /**
   * Get the next activity in the path.
   * @return the next activity
   */
  Activity* getNext(){
    return activities_.at(++current_index_);
  }

  /**
   * Get the first activity in the path
   * @return the first activity
   */
  Activity* getFirst(){
    return activities_.at(0);
  }

  ~Path(){

//    if(repast::RepastProcess::instance()->rank()==0){
//      std::cout << "Path destructor" << std::endl;
//    }
    std::vector<Activity *>().swap(activities_);
    std::vector<Event *>().swap(events_);
  }

  /**
   * Adds an activity to the front of the path
   * @param act the activity to add
   */
  void addToFront(Activity* act);

  /**
   * Adds an activity to the end of the path
   * @param act the activity to add
   */
  void addToEnd(Activity* act);

  /**
   * Getter for the activities of the path
   * @return the activities
   */
  std::vector<Activity *> activities() const{ return activities_; }

  /**
   * Getter for the events of the path
   * @return the events
   */
  std::vector<Event *> events() const { return events_;}

  /**
   * Getter for the current index in the path
   * @return the current index
   */
  size_t current_index() const { return current_index_; }

  /**
   * Get the arrival time of the path, i.e. the current time of the last event in the path.
   * @return the arrival time of the path
   */
  int getArrivalTime() const;

  /**
   * Get the departure time of the path, i.e. the current time of the first event in the path.
   * @return the departure time of the path
   */
  int getDepartureTime() const;

  /**
   * Change the subpath from the current location to the target to the new path provided
   * @param new_path the new end of the path
   */
  void setNewPathFromHere(Path & new_path);

  /**
   * Check if any of the events provided in the set is in the path.
   * @param event_set the events for which to check
   * @return whether any of the events is in the path
   */
  bool hasEvent(std::unordered_set<Event> &event_set){
    for(Event event : event_set){
      for(Event * event_ptr : events_){
        if(event == *event_ptr){
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Output method for the activity
   * @param os the stream for the output
   * @param path the path to output
   * @return the input stream concatenated with the path
   */
  friend std::ostream& operator<<(std::ostream& os, const Path& path);

  /**
   * Checks whether the path is currently at the end
   * @return whether the path has another activity after the current.
   */
  bool isOnEndActivity(){
    return current_index_==activities_.size()-1;
  }


};

/**
 * Determine if two paths are equal by their activities
 * @param lhs the first passenger
 * @param rhs the second passenger
 * @return whether the activities of the two paths are equal
 */
inline bool operator==(const Path & lhs, const Path & rhs){
  if(lhs.activities().size() != rhs.activities().size() || lhs.current_index() != rhs.current_index()){
    return false;
  }
  for(size_t index = 0; index < lhs.activities().size(); index++){
    if(lhs.activities()[index] != rhs.activities()[index]){
      return false;
    }
  }
  return true;
}

/**
 * Determine if two paths are unequal by their activities
 * @param lhs the first passenger
 * @param rhs the second passenger
 * @return whether the activities of the two paths are unequal
 */
inline bool operator!=(const Path & lhs, const Path & rhs){
  return !(lhs == rhs);
}





#endif /* INCLUDE_PATH_H_ */
