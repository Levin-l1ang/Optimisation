/*
 * PathTest.h
 */

#ifndef PATHTEST_H_
#define PATHTEST_H_

#include "cute.h"
#include "Path.h"
#include "Manager.h"

class PathTest {
public:
	PathTest() {}
	static void testConstructor();
	static void testGetNext();
	static void testAddToFront();
	static void testAddToEnd();
	static void testSetNewPathFromHere();
	static void testHasEvent();
};

#endif /* PATHTEST_H_ */
