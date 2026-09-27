import unittest
from abc import ABCMeta, abstractmethod

from core.model.od import OD, ODPair


class ODTest(metaclass=ABCMeta):

    @abstractmethod
    def getOd(self, size: int) -> OD:
        raise NotImplementedError

    def test_compute_number_of_passengers(self):
        od = self.getOd(5)
        self.assertAlmostEqual(0, od.computeNumberOfPassengers())
        od.setValue(1, 1, 1)
        self.assertAlmostEqual(1, od.computeNumberOfPassengers())
        od.setValue(5, 1, 1)
        self.assertAlmostEqual(2, od.computeNumberOfPassengers())
        od.setValue(1, 5, 1)
        self.assertAlmostEqual(3, od.computeNumberOfPassengers())
        od.setValue(1, 5, 1.5)
        self.assertAlmostEqual(3.5, od.computeNumberOfPassengers())

    def test_get_od_pairs(self):
        od = self.getOd(3)
        od.setValue(1, 1, 1)
        od.setValue(3, 1, 5)
        od.setValue(2, 1, 0.1)
        od_pairs = od.getODPairs()
        self.assertEqual(3, len(od_pairs))
        self.assertTrue(ODPair(2, 1, 0.1) in od_pairs)
        self.assertTrue(ODPair(1, 1, 1) in od_pairs)
        self.assertTrue(ODPair(3, 1, 5) in od_pairs)
        od.setValue(2, 1, 0)
        od_pairs = od.getODPairs()
        self.assertEqual(2, len(od_pairs))
        self.assertTrue(ODPair(1, 1, 1) in od_pairs)
        self.assertTrue(ODPair(3, 1, 5) in od_pairs)
