/*
 * SerializationTest.cpp
 */

#include "SerializationTest.h"

void SerializationTest::testEventSerialization() {
  Manager * man = new Manager(repast::AgentId(1,0,2));
  Event ev0(repast::AgentId(2,0,0), 0, 0, 0, 0, man);
  Event ev1(repast::AgentId(1, 0, 0), 1, 1, 1, 1, man);
  Event ev3(repast::AgentId(3, 0, 0), 2, 2, 2, 2, man);
  Activity incAct(0, 0, "drive", 0, &ev0, &ev1);
  Activity outAct(1, 1, "drive", 1, &ev1, &ev3);
  {
    std::ofstream ofs("serialization_test_event.ar");
    boost::archive::text_oarchive oa(ofs);
    // write class instance to archive
    oa << ev1;
    // archive and stream closed when destructors are called
  }
  Event ev2;
  {
    // create and open an archive for input
    std::ifstream ifs("serialization_test_event.ar");
    boost::archive::text_iarchive ia(ifs);
    // read class state from archive
    ia >> ev2;
    // archive and stream closed when destructors are called
  }
  ASSERT_EQUAL(ev1.getId(), ev2.getId());
  ASSERT_EQUAL(ev1.event_id(), ev2.event_id());
  ASSERT_EQUAL(ev1.time(), ev2.time());
  ASSERT_EQUAL(incAct.activity_id(), (*ev2.incoming_activities()->begin())->activity_id());
  ASSERT_EQUAL(outAct.activity_id(), (*ev2.outgoing_activities()->begin())->activity_id());
}


void SerializationTest::testActivitySerialization(){
  Manager * man = new Manager(repast::AgentId(1,0,2));
  Event ev1(repast::AgentId(1, 0, 0), 0, 0, 0, 0, man);
  Event ev2(repast::AgentId(2, 0, 0), 1, 1, 1, 1, man);
  Activity act(1, 1, "drive", 0, &ev1, &ev2);
  std::vector<Activity*> activities;
  activities.push_back(&act);
  Path path(activities, 0);
  Passenger pass(repast::AgentId(1,0,1), path, &act, 25, 0);
  act.addAgent(&pass);
  {
    std::ofstream ofs("serialization_test_activity.ar");
    boost::archive::text_oarchive oa(ofs);
    // write class instance to archive
    oa << act;
    // archive and stream closed when destructors are called
  }
  Activity act2;
  {
    // create and open an archive for input
    std::ifstream ifs("serialization_test_activity.ar");
    boost::archive::text_iarchive ia(ifs);
    // read class state from archive
    ia >> act2;
    // archive and stream closed when destructors are called
  }
  ASSERT_EQUAL(act.activity_id(), act2.activity_id());
  ASSERT_EQUAL(*act.source(), *act2.source());
  ASSERT_EQUAL(*act.target(), *act2.target());
  ASSERT_EQUAL(act.type(), act2.type());
  ASSERT_EQUAL(act.weight(), act2.weight());
  ASSERT_EQUAL(act.usesTargetAsMaster(), act2.usesTargetAsMaster());
  ASSERT_EQUAL(**act.local_agents().begin(), **act2.local_agents().begin());
  delete man;
}

void SerializationTest::testPassengerSerialization(){
  Manager * man = new Manager(repast::AgentId(1,0,2));
  Event ev1(repast::AgentId(1, 0, 0), 0, 0, 0, 0, man);
  Event ev2(repast::AgentId(2, 0, 0), 1, 1, 1, 1, man);
  Event ev3(repast::AgentId(3, 0, 0), 2, 2, 2, 2, man);
  Activity act(1, 1, "drive", 0, &ev1, &ev2);
  std::vector<Activity*> activities;
  activities.push_back(&act);
  Path path(activities, 0);
  Passenger pass(repast::AgentId(1,0,1), path, &act, 1, 0);
  act.addAgent(&pass);
  {
    std::ofstream ofs("serialization_test_passenger.ar");
    boost::archive::text_oarchive oa(ofs);
    // write class instance to archive
    oa << pass;
    // archive and stream closed when destructors are called
  }
  Passenger pass2;
  {
    // create and open an archive for input
    std::ifstream ifs("serialization_test_passenger.ar");
    boost::archive::text_iarchive ia(ifs);
    // read class state from archive
    ia >> pass2;
    // archive and stream closed when destructors are called
  }
  ASSERT_EQUAL(pass.current_activity(), pass2.current_activity());
  //ASSERT_EQUAL(pass.current_path(), pass2.current_path());
  ASSERT_EQUAL(pass.getId(), pass2.getId());
  std::unordered_set<Event> event_set;
  event_set.insert(ev1);
  ASSERT_EQUAL(pass.hasEvent(event_set), pass2.hasEvent(event_set));
  event_set.clear();
  event_set.insert(ev2);
  ASSERT_EQUAL(pass.hasEvent(event_set), pass2.hasEvent(event_set));
  event_set.clear();
  event_set.insert(ev3);
  ASSERT_EQUAL(pass.hasEvent(event_set), pass2.hasEvent(event_set));
  ASSERT_EQUAL(pass.persona(), pass2.persona());
  ASSERT_EQUAL(pass.stranded(), pass2.stranded());
  ASSERT_EQUAL(pass.target_station_id(), pass2.target_station_id());
}

void SerializationTest::testPathSerialization(){
  Manager * man = new Manager(repast::AgentId(1,0,2));
  Event ev1(repast::AgentId(1, 0, 0), 0, 0, 0, 0, man);
  Event ev2(repast::AgentId(2, 0, 0), 1, 1, 1, 1, man);
  Event ev3(repast::AgentId(3, 0, 0), 2, 2, 2, 2, man);
  Event ev4(repast::AgentId(4, 0, 0), 3, 3, 3, 3, man);
  Activity act(1, 1, "drive", 0, &ev1, &ev2);
  Activity act2(2, 2, "drive", 0, &ev2, &ev3);
  std::vector<Activity*> activities;
  activities.push_back(&act);
  activities.push_back(&act2);
  Path path(activities, 0);
  {
    std::ofstream ofs("serialization_test_path.ar");
    boost::archive::text_oarchive oa(ofs);
    // write class instance to archive
    oa << path;
    // archive and stream closed when destructors are called
  }
  Path path2;
  {
    // create and open an archive for input
    std::ifstream ifs("serialization_test_path.ar");
    boost::archive::text_iarchive ia(ifs);
    // read class state from archive
    ia >> path2;
    // archive and stream closed when destructors are called
  }
  //Directly testing the activities for equality wont work. Check every list entry seperately
  for(size_t index = 0; index < path.activities().size(); index++){
    ASSERT_EQUAL(*path.activities()[index], *path2.activities()[index]);
  }
  ASSERT_EQUAL(path.current_index(), path2.current_index());
  //Directly testing the events for equality wont work. Check every list entry seperately
  for(size_t index = 0; index < path.events().size(); index++){
    ASSERT_EQUAL(*path.events()[index], *path2.events()[index]);
  }
}
