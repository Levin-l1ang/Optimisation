/**
 * \file Manager.cpp
 */

#include "Manager.h"
#include "PropagationDelayMessage.h"
#include "PTSimulationModel.h"

#include <thread>
#include <chrono>

void Manager::receive_delay(DelayMessage const & msg, int delay_strategy, std::map<int, std::unordered_set<SourceDelayMessage>> & delay_map, PTSimulationModel * model, std::unordered_set<Event> & already_delayed_events){
  //First get the delay and the event out of the message
//  if(repast::RepastProcess::instance()->rank()==1){
//    std::this_thread::sleep_for(std::chrono::seconds(900));
//  }
  if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
    std::cout << "Receive delay called on delay " << msg << "!" << std::endl;
  }
  Event * delayed_event = msg.event();
  int delay = msg.delay();
  int non_delayed_time = delayed_event->time();
  //Now reschedule the event to the delayed time and create a new simulation event with this time
  int delayed_time = non_delayed_time+delay;
  delayed_event->time(delayed_time);
  already_delayed_events.insert(*delayed_event);
  repast::RepastProcess::instance()->getScheduleRunner().scheduleEvent(delayed_time, repast::Schedule::FunctorPtr(new repast::MethodFunctor<PTSimulationModel>(model, &PTSimulationModel::executeEvent)));
  //Are there any source delays connected to this event? If yes, and the received delay was not that source delay, reschedule the
  //reveal of the source delay.
  if(dynamic_cast<const SourceDelayMessage*>(&msg) == NULL ){
    //The msg was not a source delay message, so it was a propagation delay. Therefore there could be a source delay connected with the event.
    std::map<int, std::unordered_set<SourceDelayMessage>>::iterator it = delay_map.find(non_delayed_time);
    if(it!=delay_map.end()){
      std::unordered_set<SourceDelayMessage>::iterator delay_it = (*it).second.begin();
      while(delay_it!=(*it).second.end()){
        if(*delayed_event == *((*delay_it).event())){
          //Found the source delay belonging to the new delayed event.
          SourceDelayMessage source_delay = *delay_it;
          //Delete it from the old delay set
          (*it).second.erase(delay_it++);
          //Check if the new time of the reveal has no source delays yet
          if(delay_map.find(delayed_time)==delay_map.end()){
            delay_map.insert(std::make_pair(delayed_time, std::unordered_set<SourceDelayMessage>()));
            //Need not to schedule a simulation event, this has already been done above
          }
          //Add the source delay to the new timeslot
          delay_map.find(delayed_time)->second.insert(source_delay);
        }
        else{
          if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
            std::cout << *delay_it << std::endl;
          }
          delay_it++;
        }
      }
    }
  }
  if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
    std::cout << "Processing delay..." << std::endl;
  }
  Event * event_to_delay;
  int propagated_delay;
  //Now decide what to do. This is just propagation for now.
  for(std::unordered_set<Activity*>::iterator it = delayed_event->outgoing_activities()->begin(); it != delayed_event->outgoing_activities()->end();){
    //Check if this is a headway that needs to be considered. This is the case, if it is possible to fulfill
    if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
      std::cout << "Looking at edge " << *(*it) << ": " << std::endl;
    }
    if(already_delayed_events.find(*((*it)->target()))!=already_delayed_events.end()){
      if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
        std::cout << "Event was already delayed, no further delay necessary!" << std::endl;
      }
      it++;
      continue;
    }
    int slack = (*it)->target()->time()-non_delayed_time - (*it)->lower_bound();
    if(slack < 0){
      if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
        std::cout << "This is a headway that is not fulfilled!" << std::endl;
      }
      it++;
      continue;
    }
    event_to_delay = (*it)->target();
    //new delay = delay - slack
    if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
    	std::cout << "Next event to delay: " << *event_to_delay << std::endl;
    }
    //the propagated delay is delay - slack
    propagated_delay = delay - slack;
    if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
    	std::cout << "Should have the delay " << propagated_delay << " since the slack is " << slack << "(lb: " << (*it)->lower_bound() << ")" << std::endl;
    }
    if(propagated_delay < 0){
      //Need not consider, there is enough buffer on this edge.
      if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
        std::cout << "No delay to propagate!" << std::endl;
      }
      it++;
      continue;
    }
    //Now decide what to do with the delay, i.e. propagate or not
    if((*it)->type().compare("change")){
      switch(delay_strategy){
        case NO_WAIT:{//Don't wait, no matter how long the delay is
          if(repast::RepastProcess::instance()->rank() == 0 && model->debug_level() > 2){
            std::cout << "Not waiting" << std::endl;
          }
          event_to_delay->incoming_activities()->erase(*it);
          it = delayed_event->outgoing_activities()->erase(it);
          if(repast::RepastProcess::instance()->rank() == 0 && model->debug_level() > 2){
            std::cout << "Done" << std::endl;
          }
          break;
        }
        case WAIT_TIME:{//Wait if the delay is small enough
          if(propagated_delay <= 120){
            if(repast::RepastProcess::instance()->rank() == 0 && model->debug_level() > 2){
              std::cout << "Waiting" << std::endl;
            }
            event_to_delay->manager()->receive_delay(PropagationDelayMessage(event_to_delay, propagated_delay), delay_strategy, delay_map, model, already_delayed_events);
            it++;
          }
          else{
            if(repast::RepastProcess::instance()->rank() == 0 && model->debug_level() > 2){
              std::cout << "Not waiting" << std::endl;
            }
            event_to_delay->incoming_activities()->erase(*it);
            it = delayed_event->outgoing_activities()->erase(it);
          }
          break;
        }
        case WAIT:{//Wait, no matter how long the delay is
          if(repast::RepastProcess::instance()->rank() == 0 && model->debug_level() > 2){
            std::cout << "Waiting" << std::endl;
          }
          event_to_delay->manager()->receive_delay(PropagationDelayMessage(event_to_delay, propagated_delay), delay_strategy, delay_map, model, already_delayed_events);
          it++;
          break;
        }
      }
    }
    //If the activity is no change, there is nothing to decide. The delay needs to be propagated
    else{
      event_to_delay->manager()->receive_delay(PropagationDelayMessage(event_to_delay, propagated_delay), delay_strategy, delay_map, model, already_delayed_events);
      it++;
    }
  }
  if(repast::RepastProcess::instance()->rank()==0 && model->debug_level() > 2){
    std::cout << "------------------------------------------------Considered all events, delay " << msg << " is processed. " << std::endl;
  }
}


