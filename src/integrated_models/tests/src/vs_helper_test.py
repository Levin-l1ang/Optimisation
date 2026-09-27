import unittest

import read_csv
from od_data import OD
from ptn_data import Ptn
from vs_helper import TurnaroundData


class VSHelperTest(unittest.TestCase):

    def test_computation_turn_around(self):
        ptn = Ptn()
        read_csv.read_ptn("../data/Stop.giv", "../data/Edge.giv", ptn, False)
        data = TurnaroundData(ptn, 1, 0)
        self.assertEqual(24, data.get_min_turnaround_time(ptn.get_node(1), ptn.get_node(5)))
        self.assertEqual(8, data.get_min_turnaround_distance(ptn.get_node(3), ptn.get_node(17)))
        self.assertEqual(24, data.get_min_from_depot_time(ptn.get_node(5)))
        self.assertEqual(18, data.get_min_to_depot_time(ptn.get_node(8)))
        self.assertEqual(6, data.get_min_from_depot_distance(ptn.get_node(12)))
        self.assertEqual(8, data.get_min_to_depot_distance(ptn.get_node(17)))
