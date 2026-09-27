/*
 * ManagerTest.h
 */

#ifndef TESTS_MANAGERTEST_H_
#define TESTS_MANAGERTEST_H_

#include "cute.h"
#include "Manager.h"
#include "PTSimulationModel.h"

class ManagerTest {
public:
	boost::mpi::communicator * world_;
	ManagerTest(boost::mpi::communicator * world):world_(world){}
	void testReceiveDelayNoWait();
	void testReceiveDelayWait();
	void testReceiveDelayWaitTime();
};

#endif /* TESTS_MANAGERTEST_H_ */
