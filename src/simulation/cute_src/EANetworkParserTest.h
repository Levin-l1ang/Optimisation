/*
 * EANetworkParserTest.h
 */

#ifndef EANETWORKPARSERTEST_H_
#define EANETWORKPARSERTEST_H_

#include "cute.h"
#include "EANetworkParser.h"

class EANetworkParserTest {
public:
	EANetworkParserTest() {}
	void operator()();
private:
	void testApplyNetwork();
	void testApplyPassengersByODMatrix();
	void testReadDelays();
};

#endif /* EANETWORKPARSERTEST_H_ */
