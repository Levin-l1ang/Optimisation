#include "cute.h"
#include "ide_listener.h"
#include "xml_listener.h"
#include "cute_runner.h"
#include "ODParserTest.h"
#include "PassengerTest.h"
#include "EventTest.h"
#include "ActivityTest.h"

void runSuite(int argc, char const *argv[]){
	cute::xml_file_opener xmlfile(argc,argv);
	cute::xml_listener<cute::ide_listener<> >  lis(xmlfile.out);
	boost::mpi::environment env;
	boost::mpi::communicator world;
	repast::RepastProcess::init("testdata/config.props");
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
}

int main(int argc, char const *argv[]){
    runSuite(argc,argv);
    return 0;
}



