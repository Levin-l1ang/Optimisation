/**
 * \file ActivityContent.h
 * The content of an activity.
 * Is needed for the repast framework. Don't see exactly what to do here... This is basically the repast::EdgeContent
 * class copied from repast.
 */

#ifndef INCLUDE_ACTIVITYCONTENT_H_
#define INCLUDE_ACTIVITYCONTENT_H_

#include "Edge.h"
#include "Activity.h"

/**
 * The content of an activity
 */
struct ActivityContent : public repast::RepastEdgeContent<Event> {
  /**
   * Needed for the repast framework
   * @param ar the archive
   * @param version the version
   */
  template<class Archive>
    void serialize(Archive& ar, const unsigned int version) {
      ar & activity_id;
      ar & lower_bound;
      ar & type;
      ar & local_agents;
      ar & source;
      ar & target;
    }
  /**
   * The activity id
   */
  int activity_id;
  /**
   * The lower bound
   */
  int lower_bound;
  /**
   * The type
   */
  std::string type;
  /**
   * The source
   */
  Event * source;
  /**
   * The target
   */
  Event * target;
  /**
   * The local agents
   */
  std::unordered_set<Passenger *> local_agents;

  /**
   * Constructor of an activity content out of an activity
   * @param act the activity
   */
  ActivityContent(Activity * act):
    activity_id(act->activity_id()),
    lower_bound(act->lower_bound()),
    type(act->type()),
    source(act->source()),
    target(act->target()),
    local_agents(act->local_agents()){}

  ~ActivityContent(){
    std::unordered_set<Passenger *>().swap(local_agents);
  }
};





#endif /* INCLUDE_ACTIVITYCONTENT_H_ */
