/*
 * DijkstraTest.cpp
 */

#include "DijkstraTest.h"
#include "Activity.h"


void DijkstraTest::testCalculatePath(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Event event4(repast::AgentId(4,0,1), 4, 3, 2, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	Activity act3(3, 0, "drive", 0, &event1, &event4);
	Path sp = Dijkstra::calculatePath(event1, 3);
	ASSERT_EQUAL(2, sp.getArrivalTime());
	ASSERT_EQUAL(1, sp.activities().size());
	ASSERT_EQUAL(4, sp.activities().at(0)->target()->event_id());
	delete man;
}

void DijkstraTest::testCalculatePathOnCircle(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Event event4(repast::AgentId(4,0,1), 4, 4, 4, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	Activity act3(3, 0, "drive", 0, &event3, &event4);
	Activity act4(4, 0, "drive", 0, &event3, &event1);
	Path sp = Dijkstra::calculatePath(event1, 4);
	ASSERT_EQUAL(4, sp.getArrivalTime());
	ASSERT_EQUAL(3, sp.activities().size());
	ASSERT_EQUAL(4, sp.activities().at(2)->target()->event_id());
	delete man;
}

void DijkstraTest::testCalculatePathWithoutPath(){
	Manager * man = new Manager();
	Event event1(repast::AgentId(1,0,1), 1, 1, 1, 0, man);
	Event event2(repast::AgentId(2,0,1), 2, 2, 2, 0, man);
	Event event3(repast::AgentId(3,0,1), 3, 3, 3, 0, man);
	Event event4(repast::AgentId(4,0,1), 4, 4, 4, 0, man);
	Activity act1(1, 0, "drive", 0, &event1, &event2);
	Activity act2(2, 0, "drive", 0, &event2, &event3);
	ASSERT_THROWS(Path sp = Dijkstra::calculatePath(event1, 4), std::runtime_error);
	delete man;
}
