/*
 * \file Passenger.cpp
 */

#include "Passenger.h"
#include "PathCalculator.h"
#include "Activity.h"

void Passenger::advance(){
    if(stranded_){
      return;
    }
    else{
      current_activity_->removeAgent(this);
      if(!current_path_.isOnEndActivity()){
        current_activity_ = current_path_.getNext();
        current_activity_->addAgent(this);
      }
    }
  }

void Passenger::chooseNewPath(std::unordered_set<Event> delayed_events, int debug_level){
  if(stranded_){
    return;
  }
  if(repast::RepastProcess::instance()->rank()==0 && debug_level > 2){
    std::cout << "Choose new path for passenger" << *this << std::endl;
  }
  bool current_activity_reached = false;
  //Iterate through all activities and check for delays. If the target of the last activity is delayed, there
  //is no need for computing a new path, therefore just check till index < current_path_.activities().size()-1
  for(size_t index = 0; index < current_path_.activities().size()-1; index++){
    //Skip all activities till the current activity.
    Activity * activity = current_path_.activities()[index];
    if(!current_activity_reached){
      if(*activity == *current_activity_){
        current_activity_reached = true;
      }
      else{
        continue;
      }
    }
    if(OFFLINE == persona_){
      //Check if the activity at index is still valid, i.e. that if it is a change, it is maintained.
      if(activity->type().compare("change")){
        if(activity->source()->outgoing_activities()->find(activity) == activity->source()->outgoing_activities()->end()){
          //The change is not maintained, therefore the passenger needs to find a new path!
          Path new_path;
          try{
            new_path = Dijkstra::calculatePath(*(current_activity_->target()), target_station_id_);
          }
          catch(std::runtime_error & e){
            stranded_ = true;
            return;
          }
          current_path_.setNewPathFromHere(new_path);
          if(repast::RepastProcess::instance()->rank()==0 && debug_level > 2){
            std::cout << "New path found and set, go to next passenger" << std::endl;
          }
          return;
        }
        else{
        }
      }
    }
    else{//This is the online passenger
      for(Event event : delayed_events){
        if(event == *(activity->target())){
          //There was a delay on the path. Now search a new shortest path and change the current path.
          if(repast::RepastProcess::instance()->rank()==0 && debug_level > 2){
            std::cout << "Found some delayed event, calling Dijkstra" << std::endl;
          }
          Path new_path;
          try{
            new_path = Dijkstra::calculatePath(*(current_activity_->target()), target_station_id_);
          }
          catch(std::runtime_error & e){
            stranded_ = true;
            return;
          }
          current_path_.setNewPathFromHere(new_path);
          if(repast::RepastProcess::instance()->rank()==0 && debug_level > 2){
            std::cout << "New path found and set, go to next passenger" << std::endl;
          }
          return;
        }
      }
    }
  }
}

PassengerPackage::PassengerPackage() : id(-1), starting_rank(-1), type(-1), current_rank(-1), current_path(), current_activity(NULL),
    target_station_id(-1), stranded(), persona(-1){}

PassengerPackage::PassengerPackage(int id, int starting_rank, int type, int current_rank, Path current_path, Activity* current_activity,
    int target_station_id, bool stranded, int persona): id(id), starting_rank(starting_rank), type(type), current_rank(current_rank),
        current_path(current_path), current_activity(current_activity), target_station_id(target_station_id), stranded(stranded), persona(persona){}

PassengerPackageProvider::PassengerPackageProvider(repast::SharedContext<MessagingAgent>* agentPtr) : agents(agentPtr){}

void PassengerPackageProvider::providePackage(Passenger* agent, std::vector<PassengerPackage>& out){
  repast::AgentId id = agent->getId();
  PassengerPackage package(id.id(), id.startingRank(), id.agentType(), id.currentRank(), agent->current_path(), &(agent->current_activity()),
      agent->target_station_id(), agent->stranded(), agent->persona());
  out.push_back(package);
}

void PassengerPackageProvider::provideContent(repast::AgentRequest req, std::vector<PassengerPackage>& out){
  std::vector<repast::AgentId> ids = req.requestedAgents();
  for(repast::AgentId id : ids){
    if(id.agentType() != PASSENGER){
      continue;
    }
    Passenger* agent = dynamic_cast<Passenger*>(agents->getAgent(id));
    providePackage(agent, out);
  }
}

PassengerPackageReceiver::PassengerPackageReceiver(repast::SharedContext<MessagingAgent>* agentPtr) : agents(agentPtr){}

Passenger* PassengerPackageReceiver::createAgent(PassengerPackage package){
  repast::AgentId id(package.id, package.starting_rank, package.type, package.current_rank);
  return new Passenger(id, package.current_path, package.current_activity, package.target_station_id, package.stranded, package.persona);
}

void PassengerPackageReceiver::updateAgent(PassengerPackage package){
  repast::AgentId id(package.id, package.starting_rank, package.type, package.current_rank);
  Passenger* agent = dynamic_cast<Passenger*>(agents->getAgent(id));
  agent->set(package.current_rank, package.current_path, package.current_activity, package.target_station_id, package.stranded, package.persona);
}
