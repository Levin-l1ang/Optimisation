/**
 * \file Manager.h
 * Contains the declaration of a manager
 */

#ifndef INCLUDE_MANAGER_H_
#define INCLUDE_MANAGER_H_

#include "MessagingAgent.h"
#include "SourceDelayMessage.h"
#include <boost/serialization/export.hpp>
#include <boost/serialization/split_member.hpp>


class PTSimulationModel;

//List of the possible delay strategys
/**
 * The manager will never delay future events if a change can not be mantained.
 */
#define NO_WAIT 0
/**
 * The manager will wait for a delayed event on a change activity, if the necessary waiting time is smaller than a given amount.
 */
#define WAIT_TIME 1
/**
 * The manager will always wait for a delayed event on a change activity. Therefore all change activities will be maintained.
 */
#define WAIT 2

/**
 * Implementation of a manager in the PTSimulation project.
 * A manager decides what to do with delays in the network. He is called with the receive_delay function and processes the given delay.
 */
class Manager : public MessagingAgent {

private:
  friend class boost::serialization::access;
  template<class Archive> void serialize(Archive & ar, const unsigned int version){
    ar & boost::serialization::base_object<MessagingAgent>(*this);
  }
public:
  /**
   * Creates an empty manager without an AgentId
   */
  Manager(){};
  /**
   * Create a Manager from an AgentId
   * @param id the new AgentId of the manager
   */
  Manager(repast::AgentId id) : MessagingAgent(id) {};
  ~Manager(){};
  /**
   * Process the given delay. Decides on how to propagate the delay through the network.
   * @param msg The delay message, containing the event and the amount of the delay
   * @param delay_strategy The strategy to delay events on change activities. See the constants in this class for possibilities.
   * @param delay_map A map of all source delay messages in the network. Is needed to check, whether a source delay for a future event needs to be
   *    rescheduled because of a propagation delay
   * @param model the simulation model
   * @param already_delayed_events a map of already delayed events. Already delayed events are not delayed again (otherwise this would be a problem with cycle headways!)
   */
  void receive_delay(DelayMessage const & msg, int delay_strategy, std::map<int, std::unordered_set<SourceDelayMessage>> & delay_map, PTSimulationModel * model, std::unordered_set<Event> & already_delayed_events);


};

#endif /* INCLUDE_MANAGER_H_ */
