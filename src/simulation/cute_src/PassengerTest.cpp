/*
 * PassengerTest.cpp
 */

#include "PassengerTest.h"
#include "Manager.h"

void PassengerTest::testAdvance(){
	repast::SharedContext<MessagingAgent> context(world_);
	//First build the EAN
	Manager * man = new Manager;
	Event * event1 = new Event(repast::AgentId(1,0,0,0), 1, 1, 1, 0, man);
	context.addAgent(event1);
	Event * event2 = new Event(repast::AgentId(2,0,0,0), 2, 2, 2, 0, man);
	context.addAgent(event2);
	Event * event3 = new Event(repast::AgentId(3,0,0,0), 3, 3, 3, 0, man);
	context.addAgent(event3);
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = new repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager>("EAN", true, new ActivityContentManager);
	context.addProjection(EAN);
	Activity * act1 = new Activity(1,0,"drive",0,event1, event2);
	boost::shared_ptr<Activity> act1Pointer(act1);
	EAN->addEdge(act1Pointer);
	Activity * act2 = new Activity(2,0,"drive",0,event2, event3);
	boost::shared_ptr<Activity> act2Pointer(act2);
	EAN->addEdge(act2Pointer);
	//Initialize the path for the passenger
	std::vector<Activity*> act;
	act.push_back(act1);
	act.push_back(act2);
	Path path(act, 0);
	Passenger * pass = new Passenger(repast::AgentId(1,0,1,0),EAN, path,act1,&context,3,ONLINE);
	context.addAgent(pass);
	act1->addAgent(pass);
	pass->advance();
	std::unordered_set<MessagingAgent*> agentsOnAct2 = act2->local_agents();
	ASSERT_EQUAL(0, act1->local_agents().size());
	ASSERT_EQUAL(1, act2->local_agents().size());
	Passenger * same_pass = dynamic_cast<Passenger*>(*agentsOnAct2.begin());
	ASSERT_EQUAL(*pass, *same_pass);
	delete man;
}

void PassengerTest::testHasNotEvent(){
	repast::SharedContext<MessagingAgent> context(world_);
	//First build the EAN
	Manager * man = new Manager;
	Event * event1 = new Event(repast::AgentId(1,0,0,0), 1, 1, 1, 0, man);
	context.addAgent(event1);
	Event * event2 = new Event(repast::AgentId(2,0,0,0), 2, 2, 2, 0, man);
	context.addAgent(event2);
	Event * event3 = new Event(repast::AgentId(3,0,0,0), 3, 3, 3, 0, man);
	context.addAgent(event3);
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = new repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager>("EAN", true, new ActivityContentManager);
	context.addProjection(EAN);
	Activity * act1 = new Activity(1,0,"drive",0,event1, event2);
	boost::shared_ptr<Activity> act1Pointer(act1);
	EAN->addEdge(act1Pointer);
	//Then build the passenger and its path
	std::vector<Activity*> act;
	act.push_back(act1);
	Path path(act, 0);
	Passenger * pass = new Passenger(repast::AgentId(1,0,1,0),EAN, path,act1,&context,3,ONLINE);
	context.addAgent(pass);
	act1->addAgent(pass);
	//Now start the test
	std::unordered_set<Event> eventSet;
	eventSet.insert(*event3);
	ASSERT_EQUAL(false, pass->hasEvent(eventSet));
	delete man;
}
void PassengerTest::testHasEvent(){
	repast::SharedContext<MessagingAgent> context(world_);
	//First build the EAN
	Manager * man = new Manager;
	Event * event1 = new Event(repast::AgentId(1,0,0,0), 1, 1, 1, 0, man);
	context.addAgent(event1);
	Event * event2 = new Event(repast::AgentId(2,0,0,0), 2, 2, 2, 0, man);
	context.addAgent(event2);
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = new repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager>("EAN", true, new ActivityContentManager);
	context.addProjection(EAN);
	Activity * act1 = new Activity(1,0,"drive",0,event1, event2);
	boost::shared_ptr<Activity> act1Pointer(act1);
	EAN->addEdge(act1Pointer);
	//Then build the passenger and its path
	std::vector<Activity*> act;
	act.push_back(act1);
	Path path(act, 0);
	Passenger * pass = new Passenger(repast::AgentId(1,0,1,0),EAN, path,act1,&context,3,ONLINE);
	context.addAgent(pass);
	act1->addAgent(pass);
	//Now start the test
	std::unordered_set<Event> eventSet;
	eventSet.insert(*event1);
	ASSERT_EQUAL(true, pass->hasEvent(eventSet));
	delete man;
}
void PassengerTest::testChooseNewPath(){
	repast::SharedContext<MessagingAgent> context(world_);
	Manager * man = new Manager;
	Event * event1 = new Event(repast::AgentId(1,0,0,0), 1, 1, 1, 0, man);
	context.addAgent(event1);
	Event * event2 = new Event(repast::AgentId(2,0,0,0), 2, 2, 2, 0, man);
	context.addAgent(event2);
	Event * event3 = new Event(repast::AgentId(3,0,0,0), 3, 3, 3, 0, man);
	context.addAgent(event3);
	Event * event4 = new Event(repast::AgentId(4,0,0,0), 4, 3, 4, 0, man);
	context.addAgent(event4);
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = new repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager>("EAN", true, new ActivityContentManager);
	context.addProjection(EAN);
	Activity * act1 = new Activity(1,0,"drive",0,event1, event2);
	boost::shared_ptr<Activity> act1Pointer(act1);
	EAN->addEdge(act1Pointer);
	Activity * act2 = new Activity(2,0,"drive",0,event2, event3);
	boost::shared_ptr<Activity> act2Pointer(act2);
	EAN->addEdge(act2Pointer);
	Activity * act3 = new Activity(3,0,"drive",0,event2, event4);
	boost::shared_ptr<Activity> act3Pointer(act3);
	EAN->addEdge(act3Pointer);
	//Initialize the path for the passenger
	std::vector<Activity*> act;
	act.push_back(act1);
	act.push_back(act3);
	Path path(act, 0);
	Passenger * pass = new Passenger(repast::AgentId(1,0,1,0),EAN, path,act1,&context,3,ONLINE);
	context.addAgent(pass);
	act1->addAgent(pass);
	//Now start the test
	std::unordered_set<Event> event_set;
	event_set.insert(*event2);
	pass->chooseNewPath(event_set, 0);
	ASSERT_EQUAL(3, pass->current_path().getArrivalTime());
	delete man;
}
