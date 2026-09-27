/*
 * ActivityTest.cpp
 */

#include "ActivityTest.h"

void ActivityTest::testInsertAgent(){
	Manager * man = new Manager();
	Event source(repast::AgentId(1,0,0,0), 1, 1, 1, 0, man);
	Event target(repast::AgentId(2,0,0,0), 2, 2, 2, 0, man);
	Path path;
	Activity act(1, 0, "drive", 0, &source, &target);
  Passenger agent(repast::AgentId(1,0,1,0), path, &act, 2, 0);
	act.addAgent(&agent);
	ASSERT_EQUAL(agent, **act.local_agents().begin());
	ASSERT_EQUAL(1, act.local_agents().size());
}

void ActivityTest::testRemoveAgent(){
	Manager * man = new Manager();
	Event source(repast::AgentId(1,0,0,0), 1, 1, 1, 0, man);
	Event target(repast::AgentId(2,0,0,0), 2, 2, 2, 0, man);
	Path path;
	Activity act(1, 0, "drive", 0, &source, &target);
  Passenger agent(repast::AgentId(1,0,1,0), path, &act, 2, 0);
	act.addAgent(&agent);
	ASSERT_EQUAL(true, act.removeAgent(&agent));
	ASSERT_EQUAL(0, act.local_agents().size());
}

void ActivityTest::testRemoveAgentFromEmptySet(){
	Manager * man = new Manager();
	Event source(repast::AgentId(1,0,0,0), 1, 1, 1, 0, man);
	Event target(repast::AgentId(2,0,0,0), 2, 2, 2, 0, man);
  Path path;
  Activity act(1, 0, "drive", 0, &source, &target);
  Passenger agent(repast::AgentId(1,0,1,0), path, &act, 2, 0);
	ASSERT_EQUAL(false, act.removeAgent(&agent));
}
