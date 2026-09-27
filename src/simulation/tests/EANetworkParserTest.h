/*
 * EANetworkParserTest.h
 */

#ifndef EANETWORKPARSERTEST_H_
#define EANETWORKPARSERTEST_H_

#include "cute.h"
#include "EANetworkParser.h"
#include "Passenger.h"

class EANetworkParserTest {
public:
	boost::mpi::communicator * world_;
	EANetworkParserTest(boost::mpi::communicator * world) : world_(world) {}
	void testEANByApplyNetwork();
	void testIdToEventMapByApplyNetwork();
	void testContextByApplyNetwork();
	void testApplyPassengersByODMatrixOffline();
	void testApplyPassengersByODMatrixOnline();
	void testApplyPassengersByODMatrixMixed();
	void testReadDelays();
	void testReadEmptyDelays();
};

#endif /* EANETWORKPARSERTEST_H_ */
