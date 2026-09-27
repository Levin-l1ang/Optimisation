import os
from unittest import TestCase

from core.io.config import ConfigReader
from core.io.lines import LineReader
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.util.change_and_go_method import build_cg_graph
from multi_commodity_flow import capacitated_multi_commodity_flow
from parameters import Parameters

class Test(TestCase):
    input_path = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "dataset"))

    def test_capacitated_multi_commodity_flow(self):
        config = ConfigReader.read(os.path.join(Test.input_path, "Config.cnf"))
        ptn = PTNReader.read(
            stop_file_name=os.path.join(Test.input_path, "Stop.giv"),
            link_file_name=os.path.join(Test.input_path, "Edge.giv"),
            directed=False)
        od = ODReader.read(file_name=os.path.join(Test.input_path, "OD.giv"))
        lines = LineReader.read(
            ptn,
            line_file_name=os.path.join(Test.input_path, "Line-Concept.lin"),
            read_costs=False, read_frequencies=True)
        parameters = Parameters(config)
        change_and_go = build_cg_graph(
            lines.getLineConcept(), False, parameters.ean_model_weight_drive,
            parameters.ean_model_weight_wait,
            parameters.min_wait_time, parameters.max_wait_time, True,
            parameters.ean_model_weight_change, parameters.ean_change_penalty,
            parameters.min_transfer_time, parameters.period_length)
        travel_time, transfers = capacitated_multi_commodity_flow(
            change_and_go, ptn, od, lines, parameters, 0)
        self.assertEqual(9.5, travel_time)
        self.assertEqual(10, transfers)
