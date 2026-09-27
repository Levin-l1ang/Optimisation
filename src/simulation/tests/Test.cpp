#include "cute.h"
#include "ide_listener.h"
#include "xml_listener.h"
#include "cute_runner.h"
#include "ODParserTest.h"
#include "PassengerTest.h"
#include "EventTest.h"
#include "ActivityTest.h"
#include "EANetworkParserTest.h"
#include "PathTest.h"
#include "DijkstraTest.h"
#include "PTSimulationModelTest.h"
#include "ManagerTest.h"
#include "SerializationTest.h"

void runSuite(int argc, char const *argv[]){
	cute::xml_file_opener xmlfile(argc,argv);
	cute::xml_listener<cute::ide_listener<> >  lis(xmlfile.out);
	boost::mpi::environment env;
	boost::mpi::communicator world;
	repast::RepastProcess::init("tests/testdata/config.props");
	cute::suite ODParserSuite;
	ODParserSuite.push_back(CUTE(ODParserTest::testODSize));
	ODParserSuite.push_back(CUTE(ODParserTest::testODEntry));
	ODParserSuite.push_back(CUTE(ODParserTest::testEmptyODSize));
	ODParserSuite.push_back(CUTE(ODParserTest::testNumberOfPassengers));
	ODParserSuite.push_back(CUTE(ODParserTest::testEmptyODNumberOfPassengers));
	cute::makeRunner(lis, argc, argv)(ODParserSuite, "ODParserTests");
	cute::suite passengerSuite;
	passengerSuite.push_back(CUTE_CONTEXT_MEMFUN(&world,PassengerTest,testAdvance));
	passengerSuite.push_back(CUTE_CONTEXT_MEMFUN(&world,PassengerTest,testChooseNewPath));
	passengerSuite.push_back(CUTE_CONTEXT_MEMFUN(&world,PassengerTest,testHasEvent));
	passengerSuite.push_back(CUTE_CONTEXT_MEMFUN(&world,PassengerTest,testHasNotEvent));
	cute::makeRunner(lis, argc, argv)(passengerSuite, "PassengerTests");
	cute::suite eventSuite;
	eventSuite.push_back(CUTE(EventTest::testComparator));
	cute::makeRunner(lis, argc, argv)(eventSuite, "EventTests");
	cute::suite activitySuite;
	activitySuite.push_back(CUTE(ActivityTest::testInsertAgent));
	activitySuite.push_back(CUTE(ActivityTest::testRemoveAgent));
	activitySuite.push_back(CUTE(ActivityTest::testRemoveAgentFromEmptySet));
	cute::makeRunner(lis, argc, argv)(activitySuite, "ActivityTests");
	cute::suite EANSuite;
	EANSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, EANetworkParserTest, testEANByApplyNetwork));
	EANSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, EANetworkParserTest, testIdToEventMapByApplyNetwork));
	EANSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, EANetworkParserTest, testContextByApplyNetwork));
	EANSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, EANetworkParserTest, testApplyPassengersByODMatrixOffline));
	EANSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, EANetworkParserTest, testApplyPassengersByODMatrixOnline));
	EANSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, EANetworkParserTest, testApplyPassengersByODMatrixMixed));
	EANSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, EANetworkParserTest, testReadDelays));
	EANSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, EANetworkParserTest, testReadEmptyDelays));
	cute::makeRunner(lis, argc, argv)(EANSuite, "EANTests");
	cute::suite pathSuite;
	pathSuite.push_back(CUTE(PathTest::testConstructor));
	pathSuite.push_back(CUTE(PathTest::testGetNext));
	pathSuite.push_back(CUTE(PathTest::testAddToFront));
	pathSuite.push_back(CUTE(PathTest::testAddToEnd));
	pathSuite.push_back(CUTE(PathTest::testHasEvent));
	pathSuite.push_back(CUTE(PathTest::testSetNewPathFromHere));
	cute::makeRunner(lis, argc, argv)(pathSuite, "PathTests");
	cute::suite dijkstraSuite;
	dijkstraSuite.push_back(CUTE(DijkstraTest::testCalculatePath));
	dijkstraSuite.push_back(CUTE(DijkstraTest::testCalculatePathOnCircle));
	dijkstraSuite.push_back(CUTE(DijkstraTest::testCalculatePathWithoutPath));
	cute::makeRunner(lis, argc, argv)(dijkstraSuite, "DijkstraTests");
	cute::suite modelSuite;
	modelSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, PTSimulationModelTest, testInit));
	modelSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, PTSimulationModelTest, testInitSchedule));
	modelSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, PTSimulationModelTest, testExecuteEvent));
	cute::makeRunner(lis, argc, argv)(modelSuite, "ModelTests");
	cute::suite managerSuite;
	managerSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, ManagerTest, testReceiveDelayNoWait));
	managerSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, ManagerTest, testReceiveDelayWait));
	managerSuite.push_back(CUTE_CONTEXT_MEMFUN(&world, ManagerTest, testReceiveDelayWaitTime));
	cute::makeRunner(lis, argc, argv)(managerSuite, "ManagerTests");
	cute::suite serializationSuite;
	serializationSuite.push_back(CUTE(SerializationTest::testEventSerialization));
	serializationSuite.push_back(CUTE(SerializationTest::testActivitySerialization));
	serializationSuite.push_back(CUTE(SerializationTest::testPassengerSerialization));
	serializationSuite.push_back(CUTE(SerializationTest::testPathSerialization));
	cute::makeRunner(lis, argc, argv)(serializationSuite, "SerializationTests");
}
void runSerializationSuite(int argc, char const *argv[]){
  cute::xml_file_opener xmlfile(argc,argv);
  cute::xml_listener<cute::ide_listener<> >  lis(xmlfile.out);
  cute::suite serializationSuite;
  serializationSuite.push_back(CUTE(SerializationTest::testEventSerialization));
  serializationSuite.push_back(CUTE(SerializationTest::testActivitySerialization));
  serializationSuite.push_back(CUTE(SerializationTest::testPassengerSerialization));
  serializationSuite.push_back(CUTE(SerializationTest::testPathSerialization));
  cute::makeRunner(lis, argc, argv)(serializationSuite, "SerializationTests");
}


int main(int argc, char const *argv[]){
  runSuite(argc,argv);
  //runSerializationSuite(argc,argv);
  return 0;
}





