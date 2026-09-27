/*
 * DijkstraTest.h
 */

#ifndef TESTS_DIJKSTRATEST_H_
#define TESTS_DIJKSTRATEST_H_

#include "cute.h"
#include "PathCalculator.h"
#include "Manager.h"


class DijkstraTest {
public:
	DijkstraTest() {}
	static void testCalculatePath();
	static void testCalculatePathOnCircle();
	static void testCalculatePathWithoutPath();
};

#endif /* TESTS_DIJKSTRATEST_H_ */
