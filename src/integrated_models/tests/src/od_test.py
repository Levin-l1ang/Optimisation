import time
import unittest

import od_activator
import parameters
import read_csv
from ean_data import Ean
from line_data import LinePool
from od_data import OD
from ptn_data import Ptn


class ODActivatorTest(unittest.TestCase):

    @staticmethod
    def provide_data() -> (OD, Ptn, LinePool, Ean):
        ptn = Ptn()
        od = OD()
        line_concept = LinePool()
        ean = Ean()
        read_csv.read_ptn("../data/Stop.giv", "../data/Edge.giv", ptn, False)
        read_csv.read_od_matrix("../data/OD.giv", ptn, od)
        read_csv.read_line_pool("../data/Line-Concept.lin", line_concept, ptn, False)
        ean = Ean(ptn, line_concept, od)
        return od, ptn, line_concept, ean

    def test_activator_smallest(self):
        od, ptn, _, _ = ODActivatorTest.provide_data()
        od_activator.activate_od_pairs(od, 30, parameters.ODActivator.SMALLEST_WEIGHT)
        active_od_pairs = od.get_active_od_pairs()
        self.assertEqual(30, len(active_od_pairs))
        self.assertTrue(od.get_od_pair(ptn.get_node(2), ptn.get_node(11)) in active_od_pairs)

    def test_activator_largest(self):
        od, ptn, _, _ = ODActivatorTest.provide_data()
        od_activator.activate_od_pairs(od, 30, parameters.ODActivator.LARGEST_WEIGHT)
        active_od_pairs = od.get_active_od_pairs()
        self.assertEqual(30, len(active_od_pairs))
        self.assertTrue(od.get_od_pair(ptn.get_node(12), ptn.get_node(13)) in active_od_pairs)

    def test_activator_largest_with_transfer(self):
        od, ptn, _, ean = ODActivatorTest.provide_data()
        od_activator.activate_od_pairs(od, 30, parameters.ODActivator.LARGEST_WEIGHT_WITH_TRANSFER, ean, 1, 0, 0, 0, 5)
        active_od_pairs = od.get_active_od_pairs()
        self.assertEqual(30, len(active_od_pairs))
        self.assertTrue(od.get_od_pair(ptn.get_node(1), ptn.get_node(18)) in active_od_pairs)

    def test_activator_largest_distance(self):
        od, ptn, _, _ = ODActivatorTest.provide_data()
        od_activator.activate_od_pairs(od, 30, parameters.ODActivator.LARGEST_DISTANCE)
        active_od_pairs = od.get_active_od_pairs()
        self.assertEqual(30, len(active_od_pairs))
        self.assertTrue(od.get_od_pair(ptn.get_node(1), ptn.get_node(25)) in active_od_pairs)

    def test_activator_random(self):
        od, ptn, _, _ = ODActivatorTest.provide_data()
        od_activator.activate_od_pairs(od, 30, parameters.ODActivator.RANDOM, random_seed=1)
        active_od_pairs = od.get_active_od_pairs()
        self.assertEqual(30, len(active_od_pairs))
        self.assertTrue(od.get_od_pair(ptn.get_node(3), ptn.get_node(10)) in active_od_pairs)

    def test_activator_diff(self):
        od, ptn, _, ean = ODActivatorTest.provide_data()
        od_activator.activate_od_pairs(od, 30, parameters.ODActivator.DIFF, ean, 1, 0, 0, 0, 5)
        active_od_pairs = od.get_active_od_pairs()
        self.assertEqual(30, len(active_od_pairs))
        self.assertTrue(od.get_od_pair(ptn.get_node(1), ptn.get_node(21)) in active_od_pairs)

    def test_activator_potential(self):
        od, ptn, _, ean = ODActivatorTest.provide_data()
        od_activator.activate_od_pairs(od, 30, parameters.ODActivator.POTENTIAL, ean, 1, 0, 0, 0, 5)
        active_od_pairs = od.get_active_od_pairs()
        self.assertEqual(30, len(active_od_pairs))
        self.assertTrue(od.get_od_pair(ptn.get_node(1), ptn.get_node(23)) in active_od_pairs)
