/*
 * EANetworkParserTest.cpp
 */

#include "EANetworkParserTest.h"

void EANetworkParserTest::testEANByApplyNetwork(){
	repast::SharedContext<MessagingAgent> context(world_);
	std::map<int, std::unordered_set<Event*>> id_to_event_map;
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = EANetworkParser::applyNetwork("tests/testdata/Events-expanded.giv", "tests/testdata/Activities-expanded.giv", &context, new ActivityContentManager, id_to_event_map, new Manager);
	Event* eventWithId1 = dynamic_cast<Event*>(context.getAgent(repast::AgentId(1, 0, EVENT)));
	ASSERT_EQUAL(628,EAN->vertexCount());
	ASSERT_EQUAL(8051,EAN->edgeCount());
	ASSERT_EQUAL(15, eventWithId1->incoming_activities()->size());
}

void EANetworkParserTest::testIdToEventMapByApplyNetwork(){
	repast::SharedContext<MessagingAgent> context(world_);
	std::map<int, std::unordered_set<Event*>> id_to_event_map;
	EANetworkParser::applyNetwork("tests/testdata/Events-expanded.giv", "tests/testdata/Activities-expanded.giv", &context, new ActivityContentManager, id_to_event_map, new Manager);
	ASSERT_EQUAL(16, id_to_event_map.at(2).size());
	ASSERT_EQUAL(true, id_to_event_map.at(2).find(dynamic_cast<Event*>(context.getAgent(repast::AgentId(1,0,EVENT))))!=id_to_event_map.at(1).end());
	ASSERT_EQUAL(false, id_to_event_map.at(2).find(dynamic_cast<Event*>(context.getAgent(repast::AgentId(13,0,EVENT))))!=id_to_event_map.at(1).end());
}

void EANetworkParserTest::testContextByApplyNetwork(){
	repast::SharedContext<MessagingAgent> context(world_);
	std::map<int, std::unordered_set<Event*>> id_to_event_map;
	EANetworkParser::applyNetwork("tests/testdata/Events-expanded.giv", "tests/testdata/Activities-expanded.giv", &context, new ActivityContentManager, id_to_event_map, new Manager);
	ASSERT_EQUAL(628,context.size());
	ASSERT_EQUAL(1, (*dynamic_cast<Event*>(context.getAgent(repast::AgentId(1, 0, EVENT)))).event_id());
	ASSERT_EQUAL(628, (*dynamic_cast<Event*>(context.getAgent(repast::AgentId(628, 0, EVENT)))).event_id());
	ASSERT_EQUAL(false, context.contains(repast::AgentId(629, 0, EVENT)));
	ASSERT_EQUAL(false, context.contains(repast::AgentId(1, 0, PASSENGER)));
}

void EANetworkParserTest::testApplyPassengersByODMatrixOffline(){
	repast::SharedContext<MessagingAgent> context(world_);
	std::map<int, std::unordered_set<Event*>> id_to_event_map;
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = EANetworkParser::applyNetwork("tests/testdata/Events-expanded.giv", "tests/testdata/Activities-expanded.giv", &context, new ActivityContentManager, id_to_event_map, new Manager);
	ODParser OD;
	OD.parse("tests/testdata/OD.giv");
	repast::Properties props;
	props.putProperty("debug_level", 0);
	props.putProperty("random_seed", 0);
	props.putProperty("offline_passenger_share", 1);
	props.putProperty("distribution_method", GREEDY_DISTRIBUTION);
  props.putProperty("sp_algo", 0);
	EANetworkParser::applyPassengersByODMatrix(OD, &context, EAN, id_to_event_map, &props);
	//Count the offline passengers
	int count_offline = 0;
	for(repast::SharedContext<MessagingAgent>::const_bytype_iterator it = context.byTypeBegin(PASSENGER); it != context.byTypeEnd(PASSENGER); it++){
		if(dynamic_cast<Passenger*>(&**it)->persona()==OFFLINE){
			count_offline++;
		}
	}
	ASSERT_EQUAL(2622, count_offline);
}

void EANetworkParserTest::testApplyPassengersByODMatrixOnline(){
	repast::SharedContext<MessagingAgent> context(world_);
	std::map<int, std::unordered_set<Event*>> id_to_event_map;
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = EANetworkParser::applyNetwork("tests/testdata/Events-expanded.giv", "tests/testdata/Activities-expanded.giv", &context, new ActivityContentManager, id_to_event_map, new Manager);
	ODParser OD;
	OD.parse("tests/testdata/OD.giv");
	repast::Properties props;
	props.putProperty("debug_level", 0);
	props.putProperty("random_seed", 0);
	props.putProperty("offline_passenger_share", 0);
	props.putProperty("distribution_method", GREEDY_DISTRIBUTION);
  props.putProperty("sp_algo", 0);
	EANetworkParser::applyPassengersByODMatrix(OD, &context, EAN, id_to_event_map, &props);
	//Count the online passengers
	int count_online = 0;
	for(repast::SharedContext<MessagingAgent>::const_bytype_iterator it = context.byTypeBegin(PASSENGER); it != context.byTypeEnd(PASSENGER); it++){
		if(dynamic_cast<Passenger*>(&**it)->persona()==ONLINE){
			count_online++;
		}
	}
	ASSERT_EQUAL(2622, count_online);
}

void EANetworkParserTest::testApplyPassengersByODMatrixMixed(){
	repast::SharedContext<MessagingAgent> context(world_);
	std::map<int, std::unordered_set<Event*>> id_to_event_map;
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = EANetworkParser::applyNetwork("tests/testdata/Events-expanded.giv", "tests/testdata/Activities-expanded.giv", &context, new ActivityContentManager, id_to_event_map, new Manager);
	ODParser OD;
	OD.parse("tests/testdata/OD.giv");
	repast::Properties props;
	props.putProperty("debug_level", 0);
	props.putProperty("random_seed", 0);
	props.putProperty("offline_passenger_share", 0.5);
	props.putProperty("distribution_method", GREEDY_DISTRIBUTION);
  props.putProperty("sp_algo", 0);
	EANetworkParser::applyPassengersByODMatrix(OD, &context, EAN, id_to_event_map, &props);
	//Count the offline passengers
	int count_offline = 0;
	int count_online = 0;
	for(repast::SharedContext<MessagingAgent>::const_bytype_iterator it = context.byTypeBegin(PASSENGER); it != context.byTypeEnd(PASSENGER); it++){
		if(dynamic_cast<Passenger*>(&**it)->persona()==OFFLINE){
			count_offline++;
		}
		else if(dynamic_cast<Passenger*>(&**it)->persona()==ONLINE){
			count_online++;
		}
	}
	ASSERT_EQUAL(1344, count_online);
	ASSERT_EQUAL(1278, count_offline);
}

void EANetworkParserTest::testReadDelays(){
	repast::SharedContext<MessagingAgent> context(world_);
	std::map<int, std::unordered_set<Event*>> id_to_event_map;
	repast::SharedNetwork<MessagingAgent, Activity, ActivityContent, ActivityContentManager> * EAN = EANetworkParser::applyNetwork("tests/testdata/Events-expanded.giv", "tests/testdata/Activities-expanded.giv", &context, new ActivityContentManager(), id_to_event_map, new Manager());
	ODParser OD;
	OD.parse("tests/testdata/OD.giv");
	repast::Properties props;
	props.putProperty("debug_level", 0);
	props.putProperty("random_seed", 0);
	props.putProperty("offline_passenger_share", 0.5);
	props.putProperty("distribution_method", GREEDY_DISTRIBUTION);
	props.putProperty("sp_algo", 0);
	EANetworkParser::applyPassengersByODMatrix(OD, &context, EAN, id_to_event_map, &props);
	std::map<int, std::unordered_set<SourceDelayMessage>> delay_map = std::map<int, std::unordered_set<SourceDelayMessage>>();
	EANetworkParser::readDelays(delay_map, "tests/testdata/Delays.giv", 0);
	ASSERT_EQUAL(22, delay_map.size());
	ASSERT_EQUAL(669, (*delay_map.at(40860).begin()).delay());
	ASSERT_EQUAL(33, (*delay_map.at(40860).begin()).event()->event_id());
}

void EANetworkParserTest::testReadEmptyDelays(){
	repast::SharedContext<MessagingAgent> context(world_);
	std::map<int, std::unordered_set<Event*>> id_to_event_map;
	EANetworkParser::applyNetwork("tests/testdata/Events-expanded.giv", "tests/testdata/Activities-expanded.giv", &context, new ActivityContentManager, id_to_event_map, new Manager);
	std::map<int, std::unordered_set<SourceDelayMessage>> delay_map = std::map<int, std::unordered_set<SourceDelayMessage>>();
	ASSERT_THROWS(EANetworkParser::readDelays(delay_map, "", 0), std::runtime_error);
}
