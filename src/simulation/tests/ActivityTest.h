/*
 * ActivityTest.h
 */

#ifndef ACTIVITYTEST_H_
#define ACTIVITYTEST_H_

#include "cute.h"
#include "Activity.h"
#include "Manager.h"

class ActivityTest {
public:
	ActivityTest() {}
	static void testInsertAgent();
	static void testRemoveAgent();
	static void testRemoveAgentFromEmptySet();
};

#endif /* ACTIVITYTEST_H_ */
