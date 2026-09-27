/*
 * PTSimulationModelTest.cpp
 */

#include "PTSimulationModelTest.h"


void PTSimulationModelTest::testInit(){
	model.get()->init();
	ASSERT_EQUAL(true, model.get()->context()->contains(repast::AgentId(1,0,0,0)));
	ASSERT_EQUAL(true, model.get()->context()->contains(repast::AgentId(1,0,1,0)));
}

void PTSimulationModelTest::testInitSchedule(){
	model.get()->init();
	repast::ScheduleRunner& runner = repast::RepastProcess::instance()->getScheduleRunner();
	model.get()->initSchedule(runner);
	ASSERT_EQUAL(28800, runner.schedule().getNextTick());
}

void PTSimulationModelTest::testExecuteEvent(){
	model.get()->init();
	repast::ScheduleRunner& runner = repast::RepastProcess::instance()->getScheduleRunner();
	model.get()->initSchedule(runner);
	model.get()->executeEvent();
}

