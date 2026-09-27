/**
 * \file Event.cpp
 * Implementation of the event class
 */

#include "Event.h"
#include "Manager.h"

std::unique_ptr<std::map<int, std::map<int, Event*>>> Event::event_map_ptr = std::unique_ptr<std::map<int, std::map<int, Event*>>>(new std::map<int, std::map<int, Event*>>);

Event::Event(repast::AgentId agent_id, int new_ID, int new_station, int new_time, double new_weight, Manager* manager) :
        event_id_(new_ID),
        station_(new_station),
        time_(new_time),
        weight_(new_weight),
        manager_(manager){
  id_ = agent_id;
  incoming_activities_ = std::unordered_set<Activity*>();
  outgoing_activities_ = std::unordered_set<Activity*>();
  if(Event::event_map_ptr.get()->find(new_station) == Event::event_map_ptr.get()->end()){
    Event::event_map_ptr.get()->insert(std::map<int, std::map<int, Event*>>::value_type(new_station, std::map<int, Event*>()));
  }
  Event::event_map_ptr.get()->at(new_station).insert(std::map<int, Event*>::value_type(new_time, this));
}

Event::Event(repast::AgentId agent_id, int new_ID, int new_station, int new_time, double new_weight, std::unordered_set<Activity*> incoming_activities,
    std::unordered_set<Activity*> outgoing_activities, Manager* manager):
        event_id_(new_ID),
        station_(new_station),
        time_(new_time),
        weight_(new_weight),
        incoming_activities_(incoming_activities),
        outgoing_activities_(outgoing_activities),
        manager_(manager){
  id_ = agent_id;
}

Event::Event() :
    event_id_(), station_(), time_(), weight_(), manager_() {
  id_ = repast::AgentId();
  incoming_activities_ = std::unordered_set<Activity*>();
  outgoing_activities_ = std::unordered_set<Activity*>();
}

Event::~Event() {
//    if(repast::RepastProcess::instance()->rank()==0){
//      std::cout << "Event destructor" << std::endl;
//    }
    incoming_activities_ = std::unordered_set<Activity *>();
    outgoing_activities_ = std::unordered_set<Activity *>();
  }

bool compByTime(Event* e1, Event* e2){
   return e1->time() < e2->time();
}

EventPackage::EventPackage() : id(-1), starting_rank(-1), type(-1), current_rank(-1), event_id(-1), station(-1), time(-1), weight(-1), incoming_activities(), outgoing_activities(), manager(NULL){}

EventPackage::EventPackage(int id, int starting_rank, int type, int current_rank, int event_id, int station, int time, double weight, std::unordered_set<Activity *> incoming_activities,
      std::unordered_set<Activity *> outgoing_activities, Manager * manager) :
          id(id), starting_rank(starting_rank), type(type), current_rank(current_rank), event_id(event_id), station(station), time(time), weight(weight), incoming_activities(incoming_activities),
          outgoing_activities(outgoing_activities), manager(manager){}

EventPackageProvider::EventPackageProvider(repast::SharedContext<MessagingAgent>* agentPtr) : agents(agentPtr){}

void EventPackageProvider::providePackage(Event* agent, std::vector<EventPackage>& out){
  repast::AgentId id = agent->getId();
  EventPackage package(id.id(), id.startingRank(), id.agentType(), id.currentRank(), agent->event_id(), agent->station(), agent->time(), agent->weight(), *agent->incoming_activities(), *agent->outgoing_activities(), agent->manager());
  out.push_back(package);
}

void EventPackageProvider::provideContent(repast::AgentRequest req, std::vector<EventPackage>& out){
  std::vector<repast::AgentId> ids = req.requestedAgents();
  for(repast::AgentId id : ids){
    if(id.agentType() != EVENT){
      continue;
    }
    Event* agent = dynamic_cast<Event*>(agents->getAgent(id));
    providePackage(agent, out);
  }
}

EventPackageReceiver::EventPackageReceiver(repast::SharedContext<MessagingAgent>* agentPtr) : agents(agentPtr){}

Event* EventPackageReceiver::createAgent(EventPackage package){
  repast::AgentId id(package.id, package.starting_rank, package.type, package.current_rank);
  return new Event(id, package.event_id, package.station, package.time, package.weight, package.incoming_activities, package.outgoing_activities, package.manager);
}

void EventPackageReceiver::updateAgent(EventPackage package){
  repast::AgentId id(package.id, package.starting_rank, package.type, package.current_rank);
  Event* agent = dynamic_cast<Event*>(agents->getAgent(id));
  agent->set(package.current_rank, package.event_id, package.station, package.time, package.weight, package.incoming_activities, package.outgoing_activities, package.manager);
}
