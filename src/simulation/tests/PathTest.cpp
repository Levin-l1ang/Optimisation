/*
 * PathTest.cpp
 */

#include "PathTest.h"
#include "Activity.h"

void PathTest::testConstructor(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	std::vector<Activity*> activities;
	activities.push_back(&act1);
	activities.push_back(&act2);
	Path path(activities, 0);
	ASSERT_EQUAL(1, path.events().at(0)->event_id());
	ASSERT_EQUAL(2, path.events().at(1)->event_id());
	ASSERT_EQUAL(3, path.events().at(2)->event_id());
	ASSERT_EQUAL(1, path.activities().at(0)->activity_id());
	ASSERT_EQUAL(2, path.activities().at(1)->activity_id());
	delete man;
}

void PathTest::testGetNext(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	std::vector<Activity*> activities;
	activities.push_back(&act1);
	activities.push_back(&act2);
	Path path(activities, 0);
	ASSERT_EQUAL(2, path.getNext()->activity_id());
	ASSERT_EQUAL(1, path.current_index());
	delete man;
}


void PathTest::testAddToFront(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	std::vector<Activity*> activities;
	activities.push_back(&act2);
	Path path(activities, 0);
	ASSERT_EQUAL(2, path.events().at(0)->event_id());
	ASSERT_EQUAL(3, path.events().at(1)->event_id());
	ASSERT_EQUAL(2, path.activities().at(0)->activity_id());
	ASSERT_EQUAL(1, path.activities().size());
	ASSERT_EQUAL(2, path.events().size());
	path.addToFront(&act1);
	ASSERT_EQUAL(1, path.events().at(0)->event_id());
	ASSERT_EQUAL(2, path.events().at(1)->event_id());
	ASSERT_EQUAL(3, path.events().at(2)->event_id());
	ASSERT_EQUAL(1, path.activities().at(0)->activity_id());
	ASSERT_EQUAL(2, path.activities().at(1)->activity_id());
	ASSERT_EQUAL(2, path.activities().size());
	ASSERT_EQUAL(3, path.events().size());
	delete man;
}

void PathTest::testAddToEnd(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	std::vector<Activity*> activities;
	activities.push_back(&act1);
	Path path(activities, 0);
	ASSERT_EQUAL(1, path.events().at(0)->event_id());
	ASSERT_EQUAL(2, path.events().at(1)->event_id());
	ASSERT_EQUAL(1, path.activities().at(0)->activity_id());
	ASSERT_EQUAL(1, path.activities().size());
	ASSERT_EQUAL(2, path.events().size());
	path.addToEnd(&act2);
	ASSERT_EQUAL(1, path.events().at(0)->event_id());
	ASSERT_EQUAL(2, path.events().at(1)->event_id());
	ASSERT_EQUAL(3, path.events().at(2)->event_id());
	ASSERT_EQUAL(1, path.activities().at(0)->activity_id());
	ASSERT_EQUAL(2, path.activities().at(1)->activity_id());
	ASSERT_EQUAL(2, path.activities().size());
	ASSERT_EQUAL(3, path.events().size());
	delete man;
}

void PathTest::testSetNewPathFromHere(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Event event4(repast::AgentId(4,0,1), 4, 4, 4, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	Activity act3(3, 0, "drive", 0, &event2, &event4);
	std::vector<Activity*> activities;
	activities.push_back(&act1);
	activities.push_back(&act2);
	std::vector<Activity*> new_activities;
	new_activities.push_back(&act3);
	Path path(activities, 0);
	Path newPath(new_activities, 0);
	path.setNewPathFromHere(newPath);
	ASSERT_EQUAL(3, path.activities().at(1)->activity_id());
	delete man;
}

void PathTest::testHasEvent(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Event event4(repast::AgentId(4,0,1), 4, 4, 4, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	std::vector<Activity*> activities;
	activities.push_back(&act1);
	activities.push_back(&act2);
	Path path(activities, 0);
	std::unordered_set<Event> eventSet1;
	eventSet1.insert(event1);
	eventSet1.insert(event2);
	std::unordered_set<Event> eventSet2;
	eventSet2.insert(event1);
	eventSet2.insert(event4);
	std::unordered_set<Event> eventSet3;
	eventSet3.insert(event4);
	ASSERT_EQUAL(true, path.hasEvent(eventSet1));
	ASSERT_EQUAL(true, path.hasEvent(eventSet2));
	ASSERT_EQUAL(false, path.hasEvent(eventSet3));
	delete man;
}

