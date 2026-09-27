/**
 * \file MessagingAgent.h
 */

#ifndef INCLUDE_MESSAGINGAGENT_H_
#define INCLUDE_MESSAGINGAGENT_H_

/**
 * Indicator that this Messaging agent is an event. Needed for the agent type of the agent id.
 */
#define EVENT 0
/**
 * Indicator that this messaging agent is a passenger. Needed for the agent type of the agent id.
 */
#define PASSENGER 1
/**
 * Indicator that this messaging agent is a manager. Needed for the agent type of the agent id.
 */
#define MANAGER 2

#include "AgentId.h"
#include "Message.h"
//DEBUG
#include "RepastProcess.h"
#include <boost/serialization/export.hpp>
#include <boost/serialization/split_member.hpp>


/**
 * The superclass of every agent in this simulation.
 */
class MessagingAgent: public repast::Agent {

private:
  friend class boost::serialization::access;
  template<class Archive> void serialize(Archive & ar, const unsigned int version){
    ar & id_;
  }
protected:
  /**
   * The id of the agent. Inherited to the subclasses
   */
  repast::AgentId id_;
public:
  /**
   * Checks whether two agents are the same by comparing the agent id. Needed for the repast framework
   * @param rhs the other agent to check
   * @return whether the two agents are the same
   */
  inline bool operator==(const MessagingAgent & rhs) const {
    return id_ == rhs.getId();
  }
  /**
   * Send a message to the agent with this specific AgentId
   */
  virtual void send(repast::AgentId, Message) {
  }
  ;
  /**
   * React on the message
   */
  virtual void recv(Message) {
  }
  ;
  /**
   * Getter for the id of the agent. Needed for the repast framework.
   * @return the agent id
   */
  virtual repast::AgentId& getId() {
    return id_;
  }
  /**
   * Getter for the id of the agent. Needed for the repast framework.
   * @return the agent id
   */
  virtual const repast::AgentId& getId() const {
    return id_;
  }
  ~MessagingAgent() {

//    if(repast::RepastProcess::instance()->rank()==0){
//      std::cout << "MessagingAgent destructor" << std::endl;
//    }
  }
  /**
   * Empty constructor. Needed for the repast framework
   */
  MessagingAgent() {
  }
  /**
   * Create a MessagingAgent with the given AgentId
   * @param id the new AgentId
   */
  MessagingAgent(repast::AgentId id) : id_(id) {}
  /**
   * Copy constructor. Needed for the repast framework
   * @param agent the agent to copy
   */
  MessagingAgent(MessagingAgent const & agent) :
      id_(agent.id()) {
  }
  ;
  /**
   * Getter for the agent id
   * @return the agent id
   */
  repast::AgentId id() const {
    return id_;
  }

  /**
   * Output function of the MessagingAgent, appends the AgentId to the stream
   * @param os the stream to append the AgentId to
   * @param agent the MessagingAgent to output
   * @return the new stream
   */
  friend std::ostream& operator<<(std::ostream& os, const MessagingAgent& agent) {
    os << "Agent " << agent.getId();
    return os;
  }
};

namespace std {
/**
 * Hash function for the agents. Needed for using agents in an std::unsorted_set.
 */
template<>
struct hash<MessagingAgent> : public __hash_base<size_t, MessagingAgent> {
  /**
   * Uses the hash of the agent id to provide a hash for the agent
   * @param agent the agent to hash
   * @return a hash for the agent
   */
  std::size_t operator()(const MessagingAgent& agent) const {
    return agent.id().hashcode();
  }
};
}

#endif /* INCLUDE_MESSAGINGAGENT_H_ */
