/*
 * EventTest.cpp
 */

#include "EventTest.h"

void EventTest::testComparator(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,0,0), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,0,0), 2, 2, 2, 0, man);
	std::vector<Event*> eventVector;
	eventVector.push_back(&event2);
	eventVector.push_back(&event1);
	std::sort(eventVector.begin(), eventVector.end(), compByTime);
	ASSERT_EQUAL(event1, **eventVector.begin());
	delete man;
}
