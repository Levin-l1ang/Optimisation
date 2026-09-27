/**
 * \file SourceDelayMessage.h
 */

#ifndef INCLUDE_SOURCEDELAYMESSAGE_H_
#define INCLUDE_SOURCEDELAYMESSAGE_H_

#include "DelayMessage.h"

/**
 * A message to communicate a new delay in the EAN.
 */
class SourceDelayMessage : public DelayMessage {
public:
  /**
   * Constructor for a source delay.
   * @param event the event to delay
   * @param delay the delay of the event
   */
  SourceDelayMessage(Event* event, int delay) : DelayMessage(event, delay){};
};


/**
 * Check if two delays are equal by comparing their events
 * @param lhs the first delay
 * @param rhs the second delay
 * @return whether the two delays are equal
 */
inline bool operator==(const SourceDelayMessage& lhs, const SourceDelayMessage& rhs){return (lhs.event()) == (rhs.event());}
/**
 * Check if two delays are unequal by comparing their events
 * @param lhs the first delay
 * @param rhs the second delay
 * @return whether the two delays are unequal
 */
inline bool operator!=(const SourceDelayMessage& lhs, const SourceDelayMessage& rhs){return !operator==(lhs,rhs);}
/**
 * Check if the first delay is smaller than the second by comparing the events
 * @param lhs the first delay
 * @param rhs the second delay
 * @return whether the first delay is smaller than the second
 */
inline bool operator< (const SourceDelayMessage& lhs, const SourceDelayMessage& rhs){return (lhs.event())<(lhs.event());}
/**
 * Check if the first delay is bigger than the second by comparing the events
 * @param lhs the first delay
 * @param rhs the second delay
 * @return whether the first delay is bigger than the second
 */
inline bool operator> (const SourceDelayMessage& lhs, const SourceDelayMessage& rhs){return  operator< (rhs,lhs);}
/**
 * Check if the first delay is smaller or equal than the second by comparing the events
 * @param lhs the first delay
 * @param rhs the second delay
 * @return whether the first delay is smaller or equal than the second
 */
inline bool operator<=(const SourceDelayMessage& lhs, const SourceDelayMessage& rhs){return !operator> (lhs,rhs);}
/**
 * Check if the first delay is bigger or equal than the second by comparing the events
 * @param lhs the first delay
 * @param rhs the second delay
 * @return whether the first delay is bigger or equal than the second
 */
inline bool operator>=(const SourceDelayMessage& lhs, const SourceDelayMessage& rhs){return !operator< (lhs,rhs);}

namespace std {
/**
 * Hash function for the delays. Needed for using SourceDelayMessages in an std::unsorted_set.
 */
template<>
struct hash<SourceDelayMessage> : public __hash_base<size_t, SourceDelayMessage> {
  /**
   * Uses the hash of the event to provide a hash for the delay
   * @param delay the delay to hash
   * @return a hash for the delay
   */
  std::size_t operator()(const SourceDelayMessage& delay) const {
    return delay.event()->id().hashcode();
  }
};
}



#endif /* INCLUDE_SOURCEDELAYMESSAGE_H_ */
