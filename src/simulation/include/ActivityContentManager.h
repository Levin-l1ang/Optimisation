/**
 * \file ActivityContentManager.h
 * Contentmanager for an activity
 * Needed for the repast framework
 */

#ifndef INCLUDE_ACTIVITYCONTENTMANAGER_H_
#define INCLUDE_ACTIVITYCONTENTMANAGER_H_

#include "Context.h"
#include "Edge.h"
#include "ActivityContent.h"

/**
 * Class to create an activity out of an activity content and vice versa. Is needed for the repast framework
 */
class ActivityContentManager : public repast::RepastEdgeContentManager<Event> {
public:
  /**
   * Creates an activity from an activity content
   * @param content the activity content to fit in the activity
   * @param context is not needed here
   * @return a new activity with the specified content
   */
  Activity* createEdge(ActivityContent& content, repast::Context<MessagingAgent>* context){
    return new Activity(content.activity_id, content.lower_bound, content.type, content.weight, content.source, content.target, content.local_agents);
  }

  /**
   * Creates an activity content from an activity
   * @param act the activity to pack into the content
   * @return the new content
   */
  ActivityContent* provideEdgeContent(Activity* act){
    return new ActivityContent(act);
  }

};

#endif /* INCLUDE_ACTIVITYCONTENTMANAGER_H_ */
