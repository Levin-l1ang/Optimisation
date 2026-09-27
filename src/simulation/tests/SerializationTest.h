/*
 * SerializationTest.h
 */

#ifndef TESTS_SERIALIZATIONTEST_H_
#define TESTS_SERIALIZATIONTEST_H_

#include "cute.h"
#include "Event.h"
#include "Manager.h"
#include "Activity.h"
#include "Passenger.h"
#include "Path.h"

#include <fstream>

// include headers that implement a archive in simple text format
#include <boost/archive/text_oarchive.hpp>
#include <boost/archive/text_iarchive.hpp>


class SerializationTest {
public:
  SerializationTest() {}

  static void testEventSerialization();
  static void testActivitySerialization();
  static void testPassengerSerialization();
  static void testPathSerialization();
};

#endif /* TESTS_SERIALIZATIONTEST_H_ */
