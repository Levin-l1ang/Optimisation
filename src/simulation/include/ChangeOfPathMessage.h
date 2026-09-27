/**
 * \file ChangeOfPathMessage.h
 * Message send from a passenger to a Manager to say that the passengers path has changed. This could influence wait/nowait decisions.
 * Is not used yet.
 */

#ifndef INCLUDE_CHANGEOFPATHMESSAGE_H_
#define INCLUDE_CHANGEOFPATHMESSAGE_H_

#include "Message.h"
#include "Path.h"

/**
 * The message to communicate the change of the path of a passenger.
 */
class ChangeOfPathMessage: Message {
private:
  Path newPath;
};




#endif /* INCLUDE_CHANGEOFPATHMESSAGE_H_ */
