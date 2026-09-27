/*
 * PassengerTest.h
 */

#ifndef PASSENGERTEST_H_
#define PASSENGERTEST_H_

#include "Passenger.h"
#include "cute.h"
#include "Properties.h"

class PassengerTest {
public:
	boost::mpi::communicator * world_;
	PassengerTest(boost::mpi::communicator * world):world_(world){}
	void testAdvance();
	void testHasEvent();
	void testHasNotEvent();
	void testChooseNewPath();

};

#endif /* PASSENGERTEST_H_ */
