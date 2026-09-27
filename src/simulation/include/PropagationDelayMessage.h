/**
 * \file PropagationDelayMessage.h
 */

#ifndef INCLUDE_PROPAGATIONDELAYMESSAGE_H_
#define INCLUDE_PROPAGATIONDELAYMESSAGE_H_

#include "DelayMessage.h"

/**
 * A message to communicate the propagation of a delay through the EAN.
 */
class PropagationDelayMessage : public DelayMessage {

public:
  /**
   * Construct a new propagation delay message from an event to delay and an amount of delay.
   * @param event the event to delay
   * @param delay the amount of delay
   */
  PropagationDelayMessage(Event* event, int delay) : DelayMessage(event, delay){};

};



#endif /* INCLUDE_PROPAGATIONDELAYMESSAGE_H_ */
