/*
 * \file Path.cpp
 *
 */

#include "Path.h"
#include "Activity.h"

Path::Path (std::vector<Activity *> activities, size_t current_index){
  activities_ = activities;
  current_index_ = current_index;
  events_.push_back(activities_.front()->source());
  for(Activity * act : activities_){
    events_.push_back(act->target());
  }
}

Path::Path(std::vector<int> eventIds, repast::SharedContext<MessagingAgent>& context, repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN){
  current_index_ = 0;
  for(std::vector<int>::iterator it = eventIds.begin();;){
    int firstIndex = *it;
    repast::AgentId id(firstIndex, repast::RepastProcess::instance()->rank(), EVENT);
    events_.push_back(dynamic_cast<Event*>(context.getAgent(id)));it++;
    if(it == eventIds.end()){
      break;
    }
    int secondIndex = *it;
    try{
      activities_.push_back(Activity::act_map_ptr()->at(std::make_pair(firstIndex, secondIndex)));
      if(Activity::act_map_ptr()->at(std::make_pair(firstIndex, secondIndex))->type() == "\"headway\""){
        throw std::runtime_error("Trying to add a headway activity in a passenger path!");
      }
    }
    catch(std::out_of_range&){
      //This can happen if a wait activity is used, that does not exist in the original network. In this case, just create the activity
      //First get the two events in question
      Event* sourceEvent = dynamic_cast<Event*>(context.getAgent(repast::AgentId(firstIndex, repast::RepastProcess::instance()->rank(), EVENT)));
      Event* targetEvent = dynamic_cast<Event*>(context.getAgent(repast::AgentId(secondIndex, repast::RepastProcess::instance()->rank(), EVENT)));
      //Make a few checks to see if all went fine
      if(sourceEvent->station() != targetEvent->station() || sourceEvent->time() >= targetEvent->time()){
        std::cerr << "Source: " << *sourceEvent << std::endl;
        std::cerr << "Target: " << *targetEvent << std::endl;
        throw std::runtime_error("Could not make a connection between this two events!");
      }
      //Now create the new activity and add them to the network and the path
      Activity* act = new Activity(Activity::maxActivityId()+1, 0, "wait", 0, sourceEvent, targetEvent);
      boost::shared_ptr<Activity> act_pointer(act);
      EAN->addEdge(act_pointer);
      activities_.push_back(act);
    }
  }
}

void Path::addToFront(Activity* act){
  activities_.insert(activities_.begin(), act);
  events_.insert(events_.begin(), act->source());
}

void Path::addToEnd(Activity* act){
  activities_.push_back(act);
  events_.push_back(act->target());
}

int Path::getArrivalTime() const {
  return activities_.back()->target()->time();
}

int Path::getDepartureTime() const {
  return activities_.front()->source()->time();
}

std::ostream& operator<<(std::ostream& os, const Path& path){
    os << "(";
    for(size_t index = 0; index < path.activities().size(); index++){
        os << *(path.activities()[index])<<", ";
    }
    os << ", cur_index: " << path.current_index() << ")";
    return os;
  }


void Path::setNewPathFromHere(Path & new_path){
  //Check if the new path is shorter than the old one, for debugging:
  /*
  int old_arrival_time = this->getArrivalTime();
  int new_arrival_time = new_path.getArrivalTime();
  This case can actually happen, if the passenger has a change that is not possible anymore
  if(repast::RepastProcess::instance()->rank() == 0){
    if(old_arrival_time < new_arrival_time){
      throw std::runtime_error("New path is longer than old path!");
    }
  }
  */
  std::vector<Activity*> new_activities = new_path.activities();
  //First delete everything from here on. That is, all activities after current index and all events after current index + 1
  activities_.erase(std::find(activities_.begin(), activities_.end(), activities_[current_index_])+1, activities_.end());
  events_.erase(std::find(events_.begin(), events_.end(), events_[current_index_])+2, events_.end());
  for(Activity * activity : new_activities){
    this->addToEnd(activity);
  }
}



