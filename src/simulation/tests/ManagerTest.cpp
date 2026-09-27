/*
 * ManagerTest.cpp
 */

#include "ManagerTest.h"

void ManagerTest::testReceiveDelayNoWait(){
	PTSimulationModel model("tests/testdata/model.props", 0, NULL, world_);
	model.init();
	std::map<int, std::unordered_set<SourceDelayMessage>> * delay_map = model.delay_map();
	SourceDelayMessage msgToProcess = *delay_map->at(37080).begin();
	Event* eventToDelay = msgToProcess.event();
	Manager * man = eventToDelay->manager();
	std::unordered_set<Event> alreadyDelayedEvents;
	man->receive_delay(msgToProcess, NO_WAIT, *delay_map, &model, alreadyDelayedEvents);
	ASSERT_EQUAL(1, alreadyDelayedEvents.size());
	ASSERT_EQUAL(37425, eventToDelay->time());
}

void ManagerTest::testReceiveDelayWait(){
	PTSimulationModel model("tests/testdata/model.props", 0, NULL, world_);
	model.init();
	std::map<int, std::unordered_set<SourceDelayMessage>> * delay_map = model.delay_map();
	SourceDelayMessage msgToProcess = *delay_map->at(37080).begin();
	Event* eventToDelay = msgToProcess.event();
	Manager * man = eventToDelay->manager();
	std::unordered_set<Event> alreadyDelayedEvents;
	man->receive_delay(msgToProcess, WAIT, *delay_map, &model, alreadyDelayedEvents);
	ASSERT_EQUAL(10, alreadyDelayedEvents.size());
}

void ManagerTest::testReceiveDelayWaitTime(){
	PTSimulationModel model("tests/testdata/model.props", 0, NULL, world_);
	model.init();
	std::map<int, std::unordered_set<SourceDelayMessage>> * delay_map = model.delay_map();
	SourceDelayMessage msgToProcess = *delay_map->at(37080).begin();
	Event* eventToDelay = msgToProcess.event();
	Manager * man = eventToDelay->manager();
	std::unordered_set<Event> alreadyDelayedEvents;
	man->receive_delay(msgToProcess, WAIT_TIME, *delay_map, &model, alreadyDelayedEvents);
	ASSERT_EQUAL(1, alreadyDelayedEvents.size());
}



