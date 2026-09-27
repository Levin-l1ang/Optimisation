/**
 * \file Message.h
 * A message to communicate between agents in the simulation. All messages are derived from this class
 */

#ifndef INCLUDE_MESSAGE_H_
#define INCLUDE_MESSAGE_H_

#include "AgentId.h"

/**
 * The superclass of every message in this simulation.
 */
class Message{
  repast::AgentId destId;
  repast::AgentId sourceId;
};





#endif /* INCLUDE_MESSAGE_H_ */
