/*
 * DelayMessage.h
 *  A message to communicate the delay of an event.
 */

#ifndef INCLUDE_DELAYMESSAGE_H_
#define INCLUDE_DELAYMESSAGE_H_

#include "Event.h"
#include "Message.h"

/**
 * Class for a delay message. Is used to derive the PropagationDelayMessage and the SourceDelayMessage.
 * A delay consists of the event to delay and the amount of delay.
 */
class DelayMessage : public Message {
protected:
  /**
   * A pointer to the event to delay
   */
  Event* event_;
  /**
   * The amount of delay on the event
   */
  int delay_;
public:
  /**
   * Constructor for the delay message.
   * @param event pointer to the event to delay
   * @param delay the time to delay the event
   */
  DelayMessage(Event* event, int delay) : event_(event), delay_(delay){};
  virtual ~DelayMessage(){ }
  /**
   * Returns a pointer to the event of the delay
   * @return the event of the delay
   */
  Event * event() const { return event_;}
  /**
   * Returns the delay of the event
   * @return the delay of the event
   */
  int delay() const{ return delay_;}

  /**
   * Set the new delay for the message
   * @param new_delay the new delay
   */
  void delay(int new_delay) {delay_=new_delay;}
  /**
    * Output method for the delay message
    * @param os the stream for the output
    * @param delay the delay to output
    * @return the input stream concatenated with the delay message
    */
  friend std::ostream& operator<<(std::ostream& os, const DelayMessage& delay) {
    os << "(" << *(delay.event()) << ", " << delay.delay() << ")";
    return os;
  }
};



#endif /* INCLUDE_DELAYMESSAGE_H_ */
