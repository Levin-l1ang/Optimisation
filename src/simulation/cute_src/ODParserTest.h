/*
 * ODParserTest.h
 */

#ifndef ODPARSERTEST_H_
#define ODPARSERTEST_H_

#include "cute.h"
#include "ODParser.h"

class ODParserTest {
public:
	ODParserTest() {}
	static void testODSize();
	static void testODEntry();
	static void testEmptyODSize();
	static void testNumberOfPassengers();
	static void testEmptyODNumberOfPassengers();
};

#endif /* ODPARSERTEST_H_ */
