/**
 * \file Activity.cpp
 * The implementation of the activity class
 */

#include "Activity.h"
#include "Event.h"
#include "Passenger.h"

//Initialize the static activity map
std::unique_ptr<std::map<std::pair<int, int>, Activity *> > Activity::act_map_ptr_ = std::unique_ptr<
    std::map<std::pair<int, int>, Activity *> >(new std::map<std::pair<int, int>, Activity *>);
std::unique_ptr<std::map<int, Activity *>> Activity::act_by_id_map_ptr_ = std::unique_ptr<std::map<int, Activity *>>(new std::map<int, Activity*>);
int Activity::maxActivityId_ = 0;
Activity::Activity(): activity_id_(0), lower_bound_(0), type_(""), weight_(0), _source(NULL), _target(NULL){
}

Activity::Activity(int activity_id, int lower_bound, std::string type, double weight, Event* source, Event* target) :
    activity_id_(activity_id), lower_bound_(lower_bound), type_(type), weight_(weight), _source(source), _target(target) {
  local_agents_ = std::unordered_set<Passenger*>();
  source->outgoing_activities()->insert(this);
  target->incoming_activities()->insert(this);
  act_map_ptr_.get()->insert(std::map<std::pair<int, int>, Activity *>::value_type(std::make_pair(source->event_id(), target->event_id()),this));
  act_by_id_map_ptr_.get()->insert(std::map<int, Activity *>::value_type(activity_id, this));
  if(activity_id > Activity::maxActivityId()){
    Activity::maxActivityId(activity_id);
  }
}

Activity::Activity(int activity_id, int lower_bound, std::string type, double weight, Event* source, Event* target,
    std::unordered_set<Passenger *> local_agents) :
        activity_id_(activity_id),
        lower_bound_(lower_bound),
        type_(type),
        weight_(weight),
        _source(source),
        _target(target),
        local_agents_(local_agents){
  source->outgoing_activities()->insert(this);
  target->incoming_activities()->insert(this);
  act_map_ptr_.get()->insert(std::map<std::pair<int, int>, Activity *>::value_type(std::make_pair(source->event_id(), target->event_id()),this));
  act_by_id_map_ptr_.get()->insert(std::map<int, Activity *>::value_type(activity_id, this));
  if(activity_id > Activity::maxActivityId()){
    Activity::maxActivityId(activity_id);
  }
}

Activity::Activity(boost::shared_ptr<MessagingAgent> source_ptr, boost::shared_ptr<MessagingAgent> target_ptr) {
  Event * source = dynamic_cast<Event*>(source_ptr.get());
  Event * target = dynamic_cast<Event*>(target_ptr.get());
  //If the source or target is not an event but any other messaging agent, the result of this cast is NULL
  if (source == NULL || target == NULL) {
    //Create some error edge. Should not happen but need to catch this case for repast
    Activity(0, 0, "error", 0, NULL, NULL);
  }
  else {
    Activity(*(act_map_ptr()->at(std::make_pair(source->event_id(), target->event_id()))));
  }
}

Activity::Activity(boost::shared_ptr<MessagingAgent> source_ptr, boost::shared_ptr<MessagingAgent> target_ptr,
    double &weight) {
  Event * source = dynamic_cast<Event*>(source_ptr.get());
  Event * target = dynamic_cast<Event*>(target_ptr.get());
  //If the source or target is not an event but any other messaging agent, the result of this cast is NULL
  if (source == NULL || target == NULL) {
    //Create some error edge. Should not happen but need to catch this case for repast
    Activity(0, 0, "error", 0, NULL, NULL);
  }
  else {
    Activity(*(act_map_ptr()->at(std::make_pair(source->event_id(), target->event_id()))));
    weight_ = weight;
  }
}

Activity::Activity(Activity const & other) :
        activity_id_(other.activity_id()),
        lower_bound_(other.lower_bound()),
        type_(other.type()),
        weight_(other.weight()),
        _source(new Event(*(other.source()))),
        _target(new Event(*(other.target()))) {
  local_agents_ = other.local_agents();
}


