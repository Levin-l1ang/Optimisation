/*
 * ODParserTest.cpp
 */

#include "ODParserTest.h"
#include "boost/filesystem.hpp"
#include <iostream>


void ODParserTest::testEmptyODSize(){
	ODParser OD;
	ASSERT_EQUAL(0, OD.size());
}

void ODParserTest::testEmptyODNumberOfPassengers(){
	ODParser OD;
	ASSERT_EQUAL(0, OD.numberOfPassengers());
}

void ODParserTest::testODSize(){
	ODParser OD;
	OD.parse("testdata/OD.giv");
	ASSERT_EQUAL(8, OD.size());
}

void ODParserTest::testODEntry(){
	ODParser OD;
	OD.parse("testdata/OD.giv");
	ASSERT_EQUAL(10, OD.getODEntry(2,8));
}

void ODParserTest::testNumberOfPassengers(){
	ODParser OD;
	OD.parse("testdata/OD.giv");
	ASSERT_EQUAL(2622, OD.numberOfPassengers());
}

