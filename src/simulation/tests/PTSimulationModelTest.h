/*
 * PTSimulationModelTest.h
 */

#ifndef TESTS_PTSIMULATIONMODELTEST_H_
#define TESTS_PTSIMULATIONMODELTEST_H_

#include "cute.h"
#include "PTSimulationModel.h"

class PTSimulationModelTest {
public:
	boost::mpi::communicator * world_;
	std::unique_ptr<PTSimulationModel> model;
	PTSimulationModelTest(boost::mpi::communicator * world):world_(world),model(new PTSimulationModel("tests/testdata/model.props", 0, NULL, world_)) {}
	void testInitSchedule();
	void testExecuteEvent();
	void testInit();
};

#endif /* TESTS_PTSIMULATIONMODELTEST_H_ */
