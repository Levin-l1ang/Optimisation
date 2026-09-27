import logging
import unittest

from core.exceptions.config_exceptions import ConfigInvalidValueException
from core.exceptions.exceptions import LinTimException
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.impl.dict_graph import DictGraph
from core.model.lines import Line, LinePool
from core.model.mm_change_and_go import MMCGArcType, MMCGEdge, MMCGNode, MMCGNodeType
from core.model.mm_change_and_go_network import MMCG, compute_waiting_time, driving_time
from core.model.ptn import Link, Stop
from core.model.ridepooling import RidepoolingArea, RidepoolingPool
from core.util.config import Config

logging.disable(logging.CRITICAL)


# ---------------------------------------------------------------------------
# fixture helpers
# ---------------------------------------------------------------------------

def build_stops():
    return [Stop(i, f"s{i}", f"stop {i}", 0.0, float(i)) for i in (1, 2, 3)]


def build_ptn(stops, directed=False, lower=2, upper=6, length=4):
    """Path PTN 1 - 2 - 3."""
    ptn = SimpleDictGraph()
    for stop in stops:
        ptn.addNode(stop)
    ptn.addEdge(Link(1, stops[0], stops[1], length, lower, upper, directed))
    ptn.addEdge(Link(2, stops[1], stops[2], length, lower, upper, directed))
    return ptn


def build_line_pool(ptn, frequencies=(2,)):
    """One line per frequency, each running 1 - 2 - 3."""
    pool = LinePool()
    links = sorted(ptn.getEdges(), key=lambda l: l.getId())
    for index, frequency in enumerate(frequencies, start=1):
        line = Line(index, ptn.isDirected())
        for link in links:
            line.addLink(link, False)
        line.setFrequency(frequency)
        pool.addLine(line)
    return pool


def build_ridepooling_pool(ptn):
    """One area covering all three stops (i.e. both links)."""
    pool = RidepoolingPool()
    links = sorted(ptn.getEdges(), key=lambda l: l.getId())
    area = RidepoolingArea(1)
    for link in links:
        area.addLink(link)
    pool.addArea(area)
    return pool


class MMCGTestBase(unittest.TestCase):
    """Provides three modalities on the same three stops.

    * ``bus``  - line based, one line 1-2-3 with frequency 2
    * ``rp``   - ridepooling, one area covering stops 1, 2 and 3
    * ``walk`` - nonscheduled
    """

    def setUp(self):
        self.stops = build_stops()
        self.bus_ptn = build_ptn(self.stops)
        self.rp_ptn = build_ptn(self.stops)
        self.walk_ptn = build_ptn(self.stops)

        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2,))
        self.rp_pool = build_ridepooling_pool(self.rp_ptn)

        self.categories = {"bus": "line-based", "rp": "ridepooling", "walk": "nonscheduled"}
        self.station_times = {"bus": 6, "rp": 6, "walk": 6}
        self.platform_times = {"bus": 4, "rp": 4, "walk": 4}
        self.detour_factors = {"bus": 1.0, "rp": 2.0, "walk": 1.0}

    # -- construction -------------------------------------------------------

    def build_mmcg(self, modalities, **kwargs):
        ptns = {"bus": self.bus_ptn, "rp": self.rp_ptn, "walk": self.walk_ptn}
        return MMCG(
            modalities=list(modalities),
            modality_categories={m: self.categories[m] for m in modalities},
            ptns={m: ptns[m] for m in modalities},
            line_pools={"bus": self.line_pool} if "bus" in modalities else {},
            ridepooling_pools={"rp": self.rp_pool} if "rp" in modalities else {},
            station_times={m: self.station_times[m] for m in modalities},
            platform_times={m: self.platform_times[m] for m in modalities},
            detour_factors={m: self.detour_factors[m] for m in modalities},
            **kwargs,
        )

    # -- query helpers ------------------------------------------------------

    @staticmethod
    def nodes_of_type(mmcg, node_type):
        return [n for n in mmcg.get_graph().getNodes() if n.getType() == node_type]

    @staticmethod
    def arcs_of_type(mmcg, arc_type):
        return [a for a in mmcg.get_graph().getEdges() if a.getType() == arc_type]

    @staticmethod
    def stops_of_modality(mmcg, modality):
        return mmcg.get_modality_stop_ids(modality)


# ---------------------------------------------------------------------------
# node and edge model
# ---------------------------------------------------------------------------

class TestMMCGNode(unittest.TestCase):

    def make_node(self, **overrides):
        kwargs = dict(node_id=1, stop_id=5, line_id=3, area_id=0,
                      type=MMCGNodeType.LINE, modality="bus")
        kwargs.update(overrides)
        return MMCGNode(**kwargs)

    def test_getters(self):
        node = self.make_node()
        self.assertEqual(1, node.getId())
        self.assertEqual(5, node.getStopId())
        self.assertEqual(3, node.getLineId())
        self.assertEqual(0, node.getAreaId())
        self.assertEqual(MMCGNodeType.LINE, node.getType())
        self.assertEqual("bus", node.getModality())

    def test_setters(self):
        node = self.make_node()
        node.setId(7)
        node.setStopId(8)
        node.setLineId(9)
        node.setAreaId(10)
        node.setModality("rail")
        self.assertEqual(7, node.getId())
        self.assertEqual(8, node.getStopId())
        self.assertEqual(9, node.getLineId())
        self.assertEqual(10, node.getAreaId())
        self.assertEqual("rail", node.getModality())

    def test_default_ids(self):
        node = MMCGNode(node_id=1, stop_id=1, type=MMCGNodeType.STATION)
        self.assertEqual(0, node.getLineId())
        self.assertEqual(0, node.getAreaId())
        self.assertIsNone(node.getModality())

    def test_origin_and_destination_flags(self):
        expectations = {
            MMCGNodeType.PLATFORM: (True, True),
            MMCGNodeType.ORIGIN: (True, False),
            MMCGNodeType.DESTINATION: (False, True),
            MMCGNodeType.STATION: (False, False),
            MMCGNodeType.LINE: (False, False),
            MMCGNodeType.RIDEPOOLING: (False, False),
            MMCGNodeType.NONSCHEDULED: (False, False),
        }
        for node_type, (is_origin, is_destination) in expectations.items():
            with self.subTest(node_type=node_type):
                node = self.make_node(type=node_type)
                self.assertEqual(is_origin, node.isOrigin())
                self.assertEqual(is_destination, node.isDestination())

    def test_csv_strings(self):
        node = self.make_node()
        self.assertEqual(["1", "5", "3", "0", "bus"], node.toCsvStrings())

    def test_str_contains_all_fields(self):
        text = str(self.make_node())
        for part in ("1", "5", "3", "0", "bus"):
            self.assertIn(part, text)

    def test_equality_and_hash(self):
        node = self.make_node()
        same = self.make_node()
        self.assertEqual(node, same)
        self.assertEqual(hash(node), hash(same))
        self.assertFalse(node != same)

    def test_inequality_for_each_attribute(self):
        node = self.make_node()
        for overrides in ({"node_id": 2}, {"stop_id": 6}, {"line_id": 4},
                          {"area_id": 1}, {"modality": "rail"}):
            with self.subTest(**overrides):
                self.assertNotEqual(node, self.make_node(**overrides))

    def test_equality_ignores_type(self):
        """The type is deliberately not part of the equality check."""
        node = self.make_node()
        other = self.make_node(type=MMCGNodeType.ORIGIN)
        self.assertEqual(node, other)

    def test_not_equal_to_other_objects(self):
        self.assertNotEqual(self.make_node(), "not a node")
        self.assertTrue(self.make_node() != 42)


class TestMMCGEdge(unittest.TestCase):

    def setUp(self):
        self.left = MMCGNode(node_id=1, stop_id=1, line_id=1,
                             type=MMCGNodeType.LINE, modality="bus")
        self.right = MMCGNode(node_id=2, stop_id=2, line_id=1,
                              type=MMCGNodeType.LINE, modality="bus")

    def make_edge(self, **overrides):
        kwargs = dict(link_id=1, left_node=self.left, right_node=self.right,
                      cost=5.0, type=MMCGArcType.LINE, directed=True)
        kwargs.update(overrides)
        return MMCGEdge(**kwargs)

    def test_getters(self):
        edge = self.make_edge()
        self.assertEqual(1, edge.getId())
        self.assertIs(self.left, edge.getLeftNode())
        self.assertIs(self.right, edge.getRightNode())
        self.assertEqual(5.0, edge.getCost())
        self.assertEqual(MMCGArcType.LINE, edge.getType())
        self.assertTrue(edge.isDirected())
        self.assertEqual(0, edge.getLoad())

    def test_setters(self):
        edge = self.make_edge()
        edge.setId(9)
        edge.setCost(1.5)
        edge.setDirected(False)
        edge.setLoad(17.5)
        self.assertEqual(9, edge.getId())
        self.assertEqual(1.5, edge.getCost())
        self.assertFalse(edge.isDirected())
        self.assertEqual(17.5, edge.getLoad())

    def test_csv_strings(self):
        edge = self.make_edge()
        self.assertEqual(["1", "1", "2", "5.0", '"line"'], edge.toCsvStrings())

    def test_str_contains_all_fields(self):
        text = str(self.make_edge())
        for part in ("1", "2", "5.0", '"line"'):
            self.assertIn(part, text)

    def test_equality_and_hash(self):
        edge = self.make_edge()
        same = self.make_edge()
        self.assertEqual(edge, same)
        self.assertEqual(hash(edge), hash(same))

    def test_inequality_for_each_attribute(self):
        edge = self.make_edge()
        other_node = MMCGNode(node_id=3, stop_id=3, type=MMCGNodeType.LINE, modality="bus")
        for overrides in ({"link_id": 2}, {"cost": 6.0},
                          {"type": MMCGArcType.TRANSFER},
                          {"directed": False},
                          {"right_node": other_node}):
            with self.subTest(**overrides):
                self.assertNotEqual(edge, self.make_edge(**overrides))

    def test_inequality_for_load(self):
        edge = self.make_edge()
        other = self.make_edge()
        other.setLoad(3)
        self.assertNotEqual(edge, other)

    def test_undirected_equality_allows_swapped_nodes(self):
        edge = self.make_edge(directed=False)
        swapped = MMCGEdge(link_id=1, left_node=self.right, right_node=self.left,
                           cost=5.0, type=MMCGArcType.LINE, directed=False)
        self.assertEqual(edge, swapped)

    def test_directed_equality_forbids_swapped_nodes(self):
        edge = self.make_edge(directed=True)
        swapped = MMCGEdge(link_id=1, left_node=self.right, right_node=self.left,
                           cost=5.0, type=MMCGArcType.LINE, directed=True)
        self.assertNotEqual(edge, swapped)

    def test_not_equal_to_other_objects(self):
        self.assertNotEqual(self.make_edge(), "not an edge")
        self.assertTrue(self.make_edge() != None)


# ---------------------------------------------------------------------------
# module level helper functions
# ---------------------------------------------------------------------------

class TestDrivingTime(unittest.TestCase):

    def setUp(self):
        stops = build_stops()
        self.link = Link(1, stops[0], stops[1], 10, 2, 6, True)

    def test_all_models(self):
        expectations = {
            "MINIMAL_DRIVING_TIME": 2,
            "MAXIMAL_DRIVING_TIME": 6,
            "AVERAGE_DRIVING_TIME": 4,
            "EDGE_LENGTH": 10,
        }
        for model, expected in expectations.items():
            with self.subTest(model=model):
                self.assertEqual(expected, driving_time(model, self.link))

    def test_models_are_case_insensitive(self):
        self.assertEqual(2, driving_time("minimal_driving_time", self.link))
        self.assertEqual(6, driving_time("Maximal_Driving_Time", self.link))

    def test_invalid_model_raises(self):
        with self.assertRaises(ConfigInvalidValueException):
            driving_time("SOME_OTHER_MODEL", self.link)


class TestComputeWaitingTime(unittest.TestCase):

    def test_all_models(self):
        expectations = {
            "ZERO_COST": 0,
            "MINIMAL_WAITING_TIME": 2,
            "MAXIMAL_WAITING_TIME": 8,
            "AVERAGE_WAITING_TIME": 5,
        }
        for model, expected in expectations.items():
            with self.subTest(model=model):
                self.assertEqual(expected, compute_waiting_time(model, 2, 8))

    def test_models_are_case_insensitive(self):
        self.assertEqual(0, compute_waiting_time("zero_cost", 2, 8))
        self.assertEqual(8, compute_waiting_time("Maximal_Waiting_Time", 2, 8))

    def test_invalid_model_raises(self):
        with self.assertRaises(ConfigInvalidValueException):
            compute_waiting_time("UNKNOWN", 2, 8)


# ---------------------------------------------------------------------------
# basic construction (station node setup, the default)
# ---------------------------------------------------------------------------

class TestMMCGBasicConstruction(MMCGTestBase):

    def test_line_subnetwork_nodes(self):
        mmcg = self.build_mmcg(["bus"])
        line_nodes = self.nodes_of_type(mmcg, MMCGNodeType.LINE)
        self.assertEqual(3, len(line_nodes))
        self.assertEqual({1, 2, 3}, {n.getStopId() for n in line_nodes})
        for node in line_nodes:
            self.assertEqual(1, node.getLineId())
            self.assertEqual(0, node.getAreaId())
            self.assertEqual("bus", node.getModality())

    def test_line_subnetwork_arcs_undirected_ptn(self):
        mmcg = self.build_mmcg(["bus"])
        line_arcs = self.arcs_of_type(mmcg, MMCGArcType.LINE)
        self.assertEqual(4, len(line_arcs))  # both directions per link
        for arc in line_arcs:
            self.assertEqual(2, arc.getCost())  # MINIMAL_DRIVING_TIME
            self.assertTrue(arc.isDirected())

    def test_line_subnetwork_arcs_directed_ptn(self):
        self.bus_ptn = build_ptn(self.stops, directed=True)
        self.line_pool = build_line_pool(self.bus_ptn)
        mmcg = self.build_mmcg(["bus"])
        self.assertEqual(2, len(self.arcs_of_type(mmcg, MMCGArcType.LINE)))

    def test_line_subnetwork_respects_drive_model(self):
        for model, expected in (("MINIMAL_DRIVING_TIME", 2),
                                ("MAXIMAL_DRIVING_TIME", 6),
                                ("AVERAGE_DRIVING_TIME", 4),
                                ("EDGE_LENGTH", 4)):
            with self.subTest(model=model):
                mmcg = self.build_mmcg(["bus"], model_drive=model)
                for arc in self.arcs_of_type(mmcg, MMCGArcType.LINE):
                    self.assertEqual(expected, arc.getCost())

    def test_ridepooling_subnetwork(self):
        mmcg = self.build_mmcg(["rp"])
        rp_nodes = self.nodes_of_type(mmcg, MMCGNodeType.RIDEPOOLING)
        self.assertEqual(3, len(rp_nodes))
        self.assertEqual({1, 2, 3}, {n.getStopId() for n in rp_nodes})
        for node in rp_nodes:
            self.assertEqual(1, node.getAreaId())
            self.assertEqual(0, node.getLineId())
        rp_arcs = self.arcs_of_type(mmcg, MMCGArcType.RIDEPOOLING)
        self.assertEqual(4, len(rp_arcs))
        for arc in rp_arcs:
            self.assertEqual(4, arc.getCost())  # 2 * detour factor 2.0

    def test_nonscheduled_subnetwork_is_the_ptn(self):
        mmcg = self.build_mmcg(["walk"])
        walk_nodes = self.nodes_of_type(mmcg, MMCGNodeType.NONSCHEDULED)
        self.assertEqual(3, len(walk_nodes))
        walk_arcs = self.arcs_of_type(mmcg, MMCGArcType.NONSCHEDULED)
        self.assertEqual(4, len(walk_arcs))
        for arc in walk_arcs:
            self.assertEqual(2, arc.getCost())

    def test_nonscheduled_subnetwork_directed_ptn(self):
        self.walk_ptn = build_ptn(self.stops, directed=True)
        mmcg = self.build_mmcg(["walk"])
        self.assertEqual(2, len(self.arcs_of_type(mmcg, MMCGArcType.NONSCHEDULED)))

    def test_default_setup_creates_platform_and_station_nodes(self):
        mmcg = self.build_mmcg(["bus"])
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.PLATFORM)))
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.STATION)))
        self.assertEqual(0, len(self.nodes_of_type(mmcg, MMCGNodeType.ORIGIN)))
        self.assertEqual(0, len(self.nodes_of_type(mmcg, MMCGNodeType.DESTINATION)))

    def test_default_setup_platform_transfer_arcs(self):
        mmcg = self.build_mmcg(["bus"])
        transfer_arcs = self.arcs_of_type(mmcg, MMCGArcType.TRANSFER)
        self.assertEqual(6, len(transfer_arcs))  # to and from the line node
        for arc in transfer_arcs:
            self.assertEqual(2, arc.getCost())  # 0.5 * platform time 4

    def test_default_setup_station_is_origin_and_destination(self):
        mmcg = self.build_mmcg(["bus"])
        for stop_id in (1, 2, 3):
            origin = mmcg.get_origin_at_stop(stop_id)
            destination = mmcg.get_destination_at_stop(stop_id)
            self.assertIs(origin, destination)
            self.assertEqual(MMCGNodeType.STATION, origin.getType())
            self.assertEqual(stop_id, origin.getStopId())
            self.assertIsNone(origin.getModality())

    def test_default_setup_mode_transfer_arcs_to_station(self):
        mmcg = self.build_mmcg(["bus"])
        mode_transfers = self.arcs_of_type(mmcg, MMCGArcType.MODE_TRANSFER)
        self.assertEqual(6, len(mode_transfers))  # both directions per platform
        for arc in mode_transfers:
            self.assertEqual(3, arc.getCost())  # 0.5 * station time 6
        self.assertEqual(mode_transfers.__len__(), len(mmcg.intermodal_arcs))

    def test_default_setup_has_no_board_or_alight_arcs(self):
        mmcg = self.build_mmcg(["bus"])
        self.assertEqual([], self.arcs_of_type(mmcg, MMCGArcType.BOARD))
        self.assertEqual([], self.arcs_of_type(mmcg, MMCGArcType.ALIGHT))
        self.assertEqual({}, dict(mmcg.boarding_arcs_by_stopid))
        self.assertEqual({}, dict(mmcg.alighting_arcs_by_stopid))

    def test_tracking_dicts(self):
        mmcg = self.build_mmcg(["bus", "rp", "walk"])
        self.assertEqual({1, 2, 3}, set(mmcg.get_modality_stop_ids("bus")))
        self.assertEqual({1, 2, 3}, set(mmcg.get_modality_stop_ids("rp")))
        self.assertEqual({1, 2, 3}, set(mmcg.get_modality_stop_ids("walk")))
        for stop_id in (1, 2, 3):
            for node in mmcg.nodes_by_stopid[stop_id]:
                self.assertEqual(stop_id, node.getStopId())
        for modality in ("bus", "rp", "walk"):
            for node in mmcg.get_modality_nodes(modality):
                self.assertEqual(modality, node.getModality())

    def test_graph_contains_all_tracked_objects(self):
        mmcg = self.build_mmcg(["bus", "rp", "walk"])
        nodes = mmcg.get_graph().getNodes()
        edges = mmcg.get_graph().getEdges()
        for modality in ("bus", "rp", "walk"):
            for node in mmcg.get_modality_nodes(modality):
                self.assertIn(node, nodes)
            for arc in mmcg.get_modality_arcs(modality):
                self.assertIn(arc, edges)
        for arc in mmcg.intermodal_arcs:
            self.assertIn(arc, edges)

    def test_modality_category_accessors(self):
        mmcg = self.build_mmcg(["bus", "rp", "walk"])
        self.assertEqual(["bus"], mmcg.get_line_modalities())
        self.assertEqual(["rp"], mmcg.get_ridepooling_modalities())
        self.assertEqual(["walk"], mmcg.get_nonscheduled_modalities())

    def test_pool_accessors(self):
        mmcg = self.build_mmcg(["bus", "rp"])
        self.assertIs(self.line_pool, mmcg.get_line_pools()["bus"])
        self.assertIs(self.rp_pool, mmcg.get_ridepooling_pools()["rp"])

    def test_arc_accessors_by_category(self):
        mmcg = self.build_mmcg(["bus", "rp", "walk"])
        line_arcs = mmcg.get_line_arcs()
        line = self.line_pool.getLine(1)
        self.assertEqual(4, len(line_arcs["bus"][line]))
        rp_arcs = mmcg.get_ridepooling_arcs()
        area = self.rp_pool.getArea(1)
        self.assertEqual(4, len(rp_arcs["rp"][area]))
        ns_arcs = mmcg.get_nonscheduled_arcs()
        self.assertEqual(4, len(ns_arcs["walk"]))

    def test_multiple_lines(self):
        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))
        mmcg = self.build_mmcg(["bus"])
        self.assertEqual(6, len(self.nodes_of_type(mmcg, MMCGNodeType.LINE)))
        self.assertEqual(8, len(self.arcs_of_type(mmcg, MMCGArcType.LINE)))
        self.assertEqual(12, len(self.arcs_of_type(mmcg, MMCGArcType.TRANSFER)))
        line_arcs = mmcg.get_line_arcs()
        for line_id in (1, 2):
            line = self.line_pool.getLine(line_id)
            self.assertEqual(4, len(line_arcs["bus"][line]))

    def test_graph_is_directed(self):
        mmcg = self.build_mmcg(["bus"])
        for arc in mmcg.get_graph().getEdges():
            self.assertTrue(arc.isDirected())


# ---------------------------------------------------------------------------
# the four transfer setups
# ---------------------------------------------------------------------------

class TestMMCGTransferSetups(MMCGTestBase):

    def test_intramodal_setup_creates_origin_and_destination_per_modality(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.ORIGIN)))
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.DESTINATION)))
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.STATION)))
        self.assertEqual(0, len(self.nodes_of_type(mmcg, MMCGNodeType.PLATFORM)))
        self.assertEqual(3, len(self.arcs_of_type(mmcg, MMCGArcType.BOARD)))
        self.assertEqual(3, len(self.arcs_of_type(mmcg, MMCGArcType.ALIGHT)))
        mode_transfers = self.arcs_of_type(mmcg, MMCGArcType.MODE_TRANSFER)
        self.assertEqual(6, len(mode_transfers))
        for arc in mode_transfers:
            self.assertEqual(3, arc.getCost())  # 0.5 * station time 6

    def test_intramodal_transfer_arcs_between_two_lines(self):
        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        transfer_arcs = self.arcs_of_type(mmcg, MMCGArcType.TRANSFER)
        self.assertEqual(6, len(transfer_arcs))  # 2 ordered pairs per stop
        for arc in transfer_arcs:
            self.assertEqual(4, arc.getCost())  # full platform time
            self.assertNotEqual(arc.getLeftNode().getLineId(),
                                arc.getRightNode().getLineId())

    def test_intramodal_setup_board_and_alight_arcs_skip_od_nodes(self):
        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        board_arcs = self.arcs_of_type(mmcg, MMCGArcType.BOARD)
        alight_arcs = self.arcs_of_type(mmcg, MMCGArcType.ALIGHT)
        self.assertEqual(6, len(board_arcs))  # 2 line nodes per stop
        self.assertEqual(6, len(alight_arcs))
        for arc in board_arcs:
            self.assertEqual(MMCGNodeType.ORIGIN, arc.getLeftNode().getType())
            self.assertEqual(MMCGNodeType.LINE, arc.getRightNode().getType())
        for arc in alight_arcs:
            self.assertEqual(MMCGNodeType.LINE, arc.getLeftNode().getType())
            self.assertEqual(MMCGNodeType.DESTINATION, arc.getRightNode().getType())

    def test_nonscheduled_modality_gets_no_platform_or_od_nodes(self):
        """A nonscheduled subnetwork consists of the PTN nodes only."""
        for kwargs in ({}, {"create_intramodal_transfer_arcs": True},
                       {"create_intermodal_transfer_arcs": True}):
            with self.subTest(**kwargs):
                self.setUp()
                mmcg = self.build_mmcg(["walk"], **kwargs)
                walk_nodes = mmcg.get_modality_nodes("walk")
                self.assertEqual(3, len(walk_nodes))
                for node in walk_nodes:
                    self.assertEqual(MMCGNodeType.NONSCHEDULED, node.getType())

    def test_intermodal_setup_creates_mode_transfers_between_platforms(self):
        mmcg = self.build_mmcg(["bus", "rp"], create_intermodal_transfer_arcs=True)
        self.assertEqual(6, len(self.nodes_of_type(mmcg, MMCGNodeType.PLATFORM)))
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.ORIGIN)))
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.DESTINATION)))
        self.assertEqual(0, len(self.nodes_of_type(mmcg, MMCGNodeType.STATION)))
        mode_transfers = self.arcs_of_type(mmcg, MMCGArcType.MODE_TRANSFER)
        self.assertEqual(12, len(mode_transfers))  # both orderings twice per stop
        for arc in mode_transfers:
            self.assertEqual(6, arc.getCost())  # 0.5*6 + 0.5*6
        self.assertEqual(6, len(self.arcs_of_type(mmcg, MMCGArcType.BOARD)))
        self.assertEqual(6, len(self.arcs_of_type(mmcg, MMCGArcType.ALIGHT)))

    def test_intermodal_setup_with_nonscheduled_modality_has_single_platform(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_intermodal_transfer_arcs=True)
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.PLATFORM)))
        self.assertEqual([], self.arcs_of_type(mmcg, MMCGArcType.MODE_TRANSFER))
        self.assertEqual([], mmcg.intermodal_arcs)
        self.assertEqual(3, len(self.arcs_of_type(mmcg, MMCGArcType.BOARD)))
        self.assertEqual(3, len(self.arcs_of_type(mmcg, MMCGArcType.ALIGHT)))

    def test_intermodal_and_intramodal_setup(self):
        mmcg = self.build_mmcg(["bus", "rp"],
                               create_intermodal_transfer_arcs=True,
                               create_intramodal_transfer_arcs=True)
        # per stop: 2 modality origins + 1 global origin (same for destinations)
        self.assertEqual(9, len(self.nodes_of_type(mmcg, MMCGNodeType.ORIGIN)))
        self.assertEqual(9, len(self.nodes_of_type(mmcg, MMCGNodeType.DESTINATION)))
        self.assertEqual(0, len(self.nodes_of_type(mmcg, MMCGNodeType.PLATFORM)))
        self.assertEqual(0, len(self.nodes_of_type(mmcg, MMCGNodeType.STATION)))
        mode_transfers = self.arcs_of_type(mmcg, MMCGArcType.MODE_TRANSFER)
        self.assertEqual(6, len(mode_transfers))  # destination -> origin of other modality
        for arc in mode_transfers:
            self.assertEqual(6, arc.getCost())
            self.assertEqual(MMCGNodeType.DESTINATION, arc.getLeftNode().getType())
            self.assertEqual(MMCGNodeType.ORIGIN, arc.getRightNode().getType())
            self.assertNotEqual(arc.getLeftNode().getModality(),
                                arc.getRightNode().getModality())

    def test_intermodal_and_intramodal_setup_tracks_correct_alighting_arcs(self):
        mmcg = self.build_mmcg(["bus", "rp"],
                               create_intermodal_transfer_arcs=True,
                               create_intramodal_transfer_arcs=True)
        all_edges = mmcg.get_graph().getEdges()
        for stop_id in (1, 2, 3):
            arcs_to_global_destination = [
                a for a in mmcg.alighting_arcs_by_stopid[stop_id]
                if a.getRightNode() is mmcg.get_destination_at_stop(stop_id)
            ]
            self.assertEqual(2, len(arcs_to_global_destination))
            for arc in arcs_to_global_destination:
                self.assertEqual(MMCGArcType.ALIGHT, arc.getType())
                self.assertEqual(stop_id, arc.getLeftNode().getStopId())
                self.assertEqual(MMCGNodeType.DESTINATION, arc.getLeftNode().getType())
                self.assertIn(arc, all_edges)

    def test_intermodal_and_intramodal_setup_tracks_correct_boarding_arcs(self):
        mmcg = self.build_mmcg(["bus", "rp"],
                               create_intermodal_transfer_arcs=True,
                               create_intramodal_transfer_arcs=True)
        for stop_id in (1, 2, 3):
            arcs_from_global_origin = [
                a for a in mmcg.boarding_arcs_by_stopid[stop_id]
                if a.getLeftNode() is mmcg.get_origin_at_stop(stop_id)
            ]
            self.assertEqual(2, len(arcs_from_global_origin))
            for arc in arcs_from_global_origin:
                self.assertEqual(MMCGArcType.BOARD, arc.getType())
                self.assertEqual(MMCGNodeType.ORIGIN, arc.getRightNode().getType())

    def test_all_transfer_setup_creates_intra_and_intermodal_arcs(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        self.assertEqual(0, len(self.nodes_of_type(mmcg, MMCGNodeType.PLATFORM)))
        self.assertEqual(0, len(self.nodes_of_type(mmcg, MMCGNodeType.STATION)))
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.ORIGIN)))
        self.assertEqual(3, len(self.nodes_of_type(mmcg, MMCGNodeType.DESTINATION)))
        mode_transfers = self.arcs_of_type(mmcg, MMCGArcType.MODE_TRANSFER)
        self.assertEqual(6, len(mode_transfers))  # bus <-> walk both ways per stop
        for arc in mode_transfers:
            self.assertEqual(6, arc.getCost())
            self.assertNotEqual(arc.getLeftNode().getModality(),
                                arc.getRightNode().getModality())
        self.assertEqual(len(mode_transfers), len(mmcg.intermodal_arcs))
        self.assertEqual(6, len(self.arcs_of_type(mmcg, MMCGArcType.BOARD)))
        self.assertEqual(6, len(self.arcs_of_type(mmcg, MMCGArcType.ALIGHT)))

    def test_all_transfer_setup_intramodal_arcs_use_platform_time(self):
        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        transfer_arcs = self.arcs_of_type(mmcg, MMCGArcType.TRANSFER)
        self.assertEqual(6, len(transfer_arcs))  # only bus has two nodes per stop
        for arc in transfer_arcs:
            self.assertEqual(4, arc.getCost())
            self.assertEqual(arc.getLeftNode().getModality(),
                             arc.getRightNode().getModality())
            self.assertIn(arc, mmcg.get_modality_arcs("bus"))

    def test_all_transfer_arcs_overrides_other_flags(self):
        mmcg = self.build_mmcg(
            ["bus"],
            create_all_transfer_arcs=True,
            create_intermodal_transfer_arcs=True,
            create_intramodal_transfer_arcs=True,
        )
        self.assertTrue(mmcg.all_transfer_arcs)
        self.assertFalse(mmcg.intermodal_transfer_arcs)
        self.assertFalse(mmcg.intramodal_transfer_arcs)

    def test_all_transfer_setup_board_and_alight_arcs_are_tracked(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        for stop_id in (1, 2, 3):
            self.assertEqual(2, len(mmcg.boarding_arcs_by_stopid[stop_id]))
            self.assertEqual(2, len(mmcg.alighting_arcs_by_stopid[stop_id]))
            for arc in mmcg.boarding_arcs_by_stopid[stop_id]:
                self.assertEqual(MMCGArcType.BOARD, arc.getType())
                self.assertIs(mmcg.get_origin_at_stop(stop_id), arc.getLeftNode())
            for arc in mmcg.alighting_arcs_by_stopid[stop_id]:
                self.assertEqual(MMCGArcType.ALIGHT, arc.getType())
                self.assertIs(mmcg.get_destination_at_stop(stop_id), arc.getRightNode())

    def test_three_modalities_with_all_transfer_arcs(self):
        mmcg = self.build_mmcg(["bus", "walk", "rp"], create_all_transfer_arcs=True)
        mode_transfers = self.arcs_of_type(mmcg, MMCGArcType.MODE_TRANSFER)
        self.assertEqual(18, len(mode_transfers))  # 3*2 ordered pairs per stop
        self.assertEqual(9, len(self.arcs_of_type(mmcg, MMCGArcType.BOARD)))
        self.assertEqual(9, len(self.arcs_of_type(mmcg, MMCGArcType.ALIGHT)))

    def test_node_and_arc_ids_are_unique(self):
        for kwargs in (
            {},
            {"create_intramodal_transfer_arcs": True},
            {"create_intermodal_transfer_arcs": True},
            {"create_intermodal_transfer_arcs": True,
             "create_intramodal_transfer_arcs": True},
            {"create_all_transfer_arcs": True},
        ):
            with self.subTest(**kwargs):
                self.setUp()
                self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))
                mmcg = self.build_mmcg(["bus", "walk", "rp"], **kwargs)
                node_ids = [n.getId() for n in mmcg.get_graph().getNodes()]
                arc_ids = [a.getId() for a in mmcg.get_graph().getEdges()]
                self.assertEqual(len(node_ids), len(set(node_ids)))
                self.assertEqual(len(arc_ids), len(set(arc_ids)))

    def test_transfer_time_tracking_matches_arc_costs(self):
        tracked_types = {MMCGArcType.TRANSFER, MMCGArcType.MODE_TRANSFER,
                         MMCGArcType.BOARD, MMCGArcType.ALIGHT}
        for kwargs in (
            {},
            {"create_intramodal_transfer_arcs": True},
            {"create_intermodal_transfer_arcs": True},
            {"create_intermodal_transfer_arcs": True,
             "create_intramodal_transfer_arcs": True},
            {"create_all_transfer_arcs": True},
        ):
            with self.subTest(**kwargs):
                self.setUp()
                mmcg = self.build_mmcg(["bus", "rp"], **kwargs)
                for arc in mmcg.get_graph().getEdges():
                    if arc.getType() in tracked_types:
                        self.assertIn(arc.getId(), mmcg.current_transfer_times)
                        self.assertAlmostEqual(
                            arc.getCost(), mmcg.current_transfer_times[arc.getId()])

    def test_initial_tracking_values(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        self.assertEqual({"bus": 0, "walk": 0}, mmcg.current_waiting_times)
        self.assertEqual({"bus": 0, "walk": 0}, mmcg.current_intramodal_penalties)
        self.assertEqual(0, mmcg.current_intermodal_penalty)


# ---------------------------------------------------------------------------
# intramodal transfer times
# ---------------------------------------------------------------------------

class TestIntramodalTransferTimes(MMCGTestBase):

    def setUp(self):
        super().setUp()
        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))

    def test_transfer_time_model_with_intramodal_arcs(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_intramodal_transfer_times("bus", "TRANSFER_TIME", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(4, arc.getCost())

    def test_formula_1_with_intramodal_arcs(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_1", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(60 / 2 + 60 / 3, arc.getCost())
            self.assertAlmostEqual(arc.getCost(),
                                   mmcg.current_transfer_times[arc.getId()])

    def test_formula_2_with_intramodal_arcs(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_2", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(60 / 5, arc.getCost())

    def test_formula_3_with_intramodal_arcs_uses_target_frequency(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_3", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            f_2 = self.line_pool.getLine(arc.getRightNode().getLineId()).getFrequency()
            self.assertAlmostEqual(60 / (2 * f_2), arc.getCost())

    def test_setting_transfer_times_twice_is_idempotent(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_1", period=60)
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_2", period=60)
        mmcg.set_intramodal_transfer_times("bus", "TRANSFER_TIME", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(4, arc.getCost())

    def test_transfer_time_model_with_platform_arcs(self):
        mmcg = self.build_mmcg(["bus"])
        mmcg.set_intramodal_transfer_times("bus", "TRANSFER_TIME", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(4, arc.getCost())

    def test_formula_1_with_platform_arcs(self):
        mmcg = self.build_mmcg(["bus"])
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_1", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            line_id = arc.getLeftNode().getLineId() or arc.getRightNode().getLineId()
            f = self.line_pool.getLine(line_id).getFrequency()
            self.assertAlmostEqual(60 / f, arc.getCost())

    def test_formula_3_with_platform_arcs(self):
        mmcg = self.build_mmcg(["bus"])
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_3", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            line_id = arc.getLeftNode().getLineId() or arc.getRightNode().getLineId()
            f = self.line_pool.getLine(line_id).getFrequency()
            self.assertAlmostEqual(0.5 * 60 / (2 * f), arc.getCost())

    def test_formula_2_without_intramodal_arcs_raises(self):
        mmcg = self.build_mmcg(["bus"])
        with self.assertRaises(LinTimException):
            mmcg.set_intramodal_transfer_times("bus", "FORMULA_2", period=60)

    def test_invalid_model_raises(self):
        for kwargs in ({}, {"create_intramodal_transfer_arcs": True}):
            with self.subTest(**kwargs):
                self.setUp()
                mmcg = self.build_mmcg(["bus"], **kwargs)
                self.assertTrue(self.arcs_of_type(mmcg, MMCGArcType.TRANSFER))
                with self.assertRaises(LinTimException):
                    mmcg.set_intramodal_transfer_times("bus", "FORMULA_42", period=60)

    def test_non_line_based_modality_raises(self):
        mmcg = self.build_mmcg(["bus", "rp", "walk"], create_all_transfer_arcs=True)
        for modality in ("rp", "walk"):
            with self.subTest(modality=modality):
                with self.assertRaises(LinTimException):
                    mmcg.set_intramodal_transfer_times(modality, "TRANSFER_TIME", period=60)

    def test_other_arc_types_are_untouched(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        before = {a.getId(): a.getCost() for a in self.arcs_of_type(mmcg, MMCGArcType.LINE)}
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_1", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.LINE):
            self.assertAlmostEqual(before[arc.getId()], arc.getCost())


# ---------------------------------------------------------------------------
# intermodal transfer times
# ---------------------------------------------------------------------------

class TestIntermodalTransferTimes(MMCGTestBase):

    def test_transfer_time_model_with_all_transfer_arcs(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_intermodal_transfer_times("TRANSFER_TIME", period=60)
        for arc in mmcg.intermodal_arcs:
            self.assertAlmostEqual(6, arc.getCost())

    def test_transfer_time_model_via_platforms(self):
        mmcg = self.build_mmcg(["bus", "rp"], create_intermodal_transfer_arcs=True)
        self.assertTrue(mmcg.intermodal_arcs)
        mmcg.set_intermodal_transfer_times("TRANSFER_TIME", period=60)
        for arc in mmcg.intermodal_arcs:
            self.assertAlmostEqual(6, arc.getCost())

    def test_transfer_time_model_via_station_is_not_supported(self):
        """In the station setup the intermodal arcs touch the station node,
        which has no modality, so the transfer time cannot be recomputed."""
        mmcg = self.build_mmcg(["bus", "walk"])
        self.assertTrue(mmcg.intermodal_arcs)
        for arc in mmcg.intermodal_arcs:
            self.assertIsNone(arc.getLeftNode().getModality() if
                              arc.getLeftNode().getType() == MMCGNodeType.STATION
                              else arc.getRightNode().getModality())
        with self.assertRaises(KeyError):
            mmcg.set_intermodal_transfer_times("TRANSFER_TIME", period=60)

    def test_formula_1_between_two_line_modalities(self):
        self.categories["rp"] = "line-based"
        rp_line_pool = build_line_pool(self.rp_ptn, frequencies=(4,))
        mmcg = MMCG(
            modalities=["bus", "rp"],
            modality_categories={"bus": "line-based", "rp": "line-based"},
            ptns={"bus": self.bus_ptn, "rp": self.rp_ptn},
            line_pools={"bus": self.line_pool, "rp": rp_line_pool},
            ridepooling_pools={},
            station_times={"bus": 6, "rp": 6},
            platform_times={"bus": 4, "rp": 4},
            detour_factors={"bus": 1.0, "rp": 1.0},
            create_all_transfer_arcs=True,
        )
        mmcg.set_intermodal_transfer_times("FORMULA_1", period=60)
        for arc in mmcg.intermodal_arcs:
            self.assertAlmostEqual(60 / 2 + 60 / 4, arc.getCost())

    def test_formula_falls_back_to_transfer_time_for_non_line_modalities(self):
        for model in ("FORMULA_1", "FORMULA_2", "FORMULA_3"):
            with self.subTest(model=model):
                self.setUp()
                mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
                mmcg.set_intermodal_transfer_times(model, period=60)
                for arc in mmcg.intermodal_arcs:
                    self.assertAlmostEqual(6, arc.getCost())

    def test_setting_intermodal_times_twice_is_idempotent(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_intermodal_transfer_times("FORMULA_1", period=60)
        mmcg.set_intermodal_transfer_times("TRANSFER_TIME", period=60)
        for arc in mmcg.intermodal_arcs:
            self.assertAlmostEqual(6, arc.getCost())

    def test_formula_without_all_transfer_arcs_raises(self):
        mmcg = self.build_mmcg(["bus", "rp"], create_intermodal_transfer_arcs=True)
        self.assertTrue(mmcg.intermodal_arcs)
        for model in ("FORMULA_1", "FORMULA_2", "FORMULA_3"):
            with self.subTest(model=model):
                with self.assertRaises(LinTimException):
                    mmcg.set_intermodal_transfer_times(model, period=60)

    def test_invalid_model_raises(self):
        for kwargs in ({"create_all_transfer_arcs": True},
                       {"create_intermodal_transfer_arcs": True}):
            with self.subTest(**kwargs):
                self.setUp()
                mmcg = self.build_mmcg(["bus", "rp"], **kwargs)
                self.assertTrue(mmcg.intermodal_arcs)
                with self.assertRaises(LinTimException):
                    mmcg.set_intermodal_transfer_times("NOT_A_MODEL", period=60)

    def test_intramodal_arcs_are_untouched(self):
        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        before = {a.getId(): a.getCost()
                  for a in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER)}
        mmcg.set_intermodal_transfer_times("TRANSFER_TIME", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(before[arc.getId()], arc.getCost())


# ---------------------------------------------------------------------------
# transfer penalties
# ---------------------------------------------------------------------------

class TestTransferPenalties(MMCGTestBase):

    def setUp(self):
        super().setUp()
        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))

    def test_intramodal_penalty_with_intramodal_arcs(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_intramodal_transfer_penalty("bus", 5)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(9, arc.getCost())  # 4 + 5
        self.assertEqual(5, mmcg.current_intramodal_penalties["bus"])

    def test_intramodal_penalty_can_be_reset(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_intramodal_transfer_penalty("bus", 5)
        mmcg.set_intramodal_transfer_penalty("bus", 2)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(6, arc.getCost())  # 4 + 2
        mmcg.set_intramodal_transfer_penalty("bus", 0)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(4, arc.getCost())

    def test_intramodal_penalty_with_all_transfer_arcs(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_intramodal_transfer_penalty("bus", 3)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(7, arc.getCost())

    def test_intramodal_penalty_only_affects_own_modality(self):
        rp_line_pool = build_line_pool(self.rp_ptn, frequencies=(2, 3))
        mmcg = MMCG(
            modalities=["bus", "rp"],
            modality_categories={"bus": "line-based", "rp": "line-based"},
            ptns={"bus": self.bus_ptn, "rp": self.rp_ptn},
            line_pools={"bus": self.line_pool, "rp": rp_line_pool},
            ridepooling_pools={},
            station_times={"bus": 6, "rp": 6},
            platform_times={"bus": 4, "rp": 4},
            detour_factors={"bus": 1.0, "rp": 1.0},
            create_intramodal_transfer_arcs=True,
        )
        mmcg.set_intramodal_transfer_penalty("bus", 5)
        for arc in mmcg.get_modality_arcs("rp"):
            if arc.getType() == MMCGArcType.TRANSFER:
                self.assertAlmostEqual(4, arc.getCost())
        self.assertEqual(0, mmcg.current_intramodal_penalties["rp"])

    def test_intramodal_penalty_without_intramodal_arcs_raises(self):
        mmcg = self.build_mmcg(["bus"])
        with self.assertRaises(LinTimException):
            mmcg.set_intramodal_transfer_penalty("bus", 5)

    def test_intermodal_penalty_with_all_transfer_arcs(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_intermodal_transfer_penalty(5)
        for arc in mmcg.intermodal_arcs:
            self.assertAlmostEqual(11, arc.getCost())  # 6 + 5
        self.assertEqual(5, mmcg.current_intermodal_penalty)

    def test_intermodal_penalty_can_be_reset(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_intermodal_transfer_penalty(5)
        mmcg.set_intermodal_transfer_penalty(1)
        for arc in mmcg.intermodal_arcs:
            self.assertAlmostEqual(7, arc.getCost())
        mmcg.set_intermodal_transfer_penalty(0)
        for arc in mmcg.intermodal_arcs:
            self.assertAlmostEqual(6, arc.getCost())

    def test_intermodal_penalty_works_with_intermodal_arcs_only(self):
        mmcg = self.build_mmcg(["bus", "rp"], create_intermodal_transfer_arcs=True)
        arcs = mmcg.intermodal_arcs
        self.assertTrue(arcs)
        before = [a.getCost() for a in arcs]
        mmcg.set_intermodal_transfer_penalty(3)
        for old, arc in zip(before, arcs):
            self.assertAlmostEqual(old + 3, arc.getCost())

    def test_intermodal_penalty_without_intermodal_arcs_raises(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_intramodal_transfer_arcs=True)
        with self.assertRaises(LinTimException):
            mmcg.set_intermodal_transfer_penalty(5)

    def test_intermodal_penalty_leaves_intramodal_arcs_untouched(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_intermodal_transfer_penalty(5)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(4, arc.getCost())

    def test_penalties_are_additive_with_transfer_times(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_intramodal_transfer_penalty("bus", 5)
        mmcg.set_intramodal_transfer_times("bus", "FORMULA_2", period=60)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(60 / 5 + 5, arc.getCost())


# ---------------------------------------------------------------------------
# waiting times
# ---------------------------------------------------------------------------

class TestWaitingTimes(MMCGTestBase):

    def test_waiting_times_with_intramodal_arcs(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_waiting_times("bus", "MAXIMAL_WAITING_TIME", 2, 8)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.LINE):
            self.assertAlmostEqual(2 + 8, arc.getCost())
        for arc in self.arcs_of_type(mmcg, MMCGArcType.BOARD):
            self.assertAlmostEqual(-4, arc.getCost())
        for arc in self.arcs_of_type(mmcg, MMCGArcType.ALIGHT):
            self.assertAlmostEqual(4, arc.getCost())
        self.assertEqual(8, mmcg.current_waiting_times["bus"])

    def test_waiting_times_reduce_transfer_arcs(self):
        self.line_pool = build_line_pool(self.bus_ptn, frequencies=(2, 3))
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_waiting_times("bus", "MINIMAL_WAITING_TIME", 2, 8)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.TRANSFER):
            self.assertAlmostEqual(4 - 2, arc.getCost())

    def test_waiting_times_can_be_reset(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        mmcg.set_waiting_times("bus", "MAXIMAL_WAITING_TIME", 2, 8)
        mmcg.set_waiting_times("bus", "MINIMAL_WAITING_TIME", 2, 8)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.LINE):
            self.assertAlmostEqual(2 + 2, arc.getCost())
        mmcg.set_waiting_times("bus", "ZERO_COST", 2, 8)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.LINE):
            self.assertAlmostEqual(2, arc.getCost())
        for arc in self.arcs_of_type(mmcg, MMCGArcType.BOARD):
            self.assertAlmostEqual(0, arc.getCost())
        for arc in self.arcs_of_type(mmcg, MMCGArcType.ALIGHT):
            self.assertAlmostEqual(0, arc.getCost())
        self.assertEqual(0, mmcg.current_waiting_times["bus"])

    def test_waiting_times_with_all_transfer_arcs(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_waiting_times("bus", "AVERAGE_WAITING_TIME", 2, 8)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.LINE):
            self.assertAlmostEqual(2 + 5, arc.getCost())
        for stop_id in (1, 2, 3):
            for arc in mmcg.boarding_arcs_by_stopid[stop_id]:
                if arc.getRightNode().getModality() == "bus":
                    self.assertAlmostEqual(-2.5, arc.getCost())
                else:
                    self.assertAlmostEqual(0, arc.getCost())
            for arc in mmcg.alighting_arcs_by_stopid[stop_id]:
                if arc.getLeftNode().getModality() == "bus":
                    self.assertAlmostEqual(2.5, arc.getCost())
                else:
                    self.assertAlmostEqual(0, arc.getCost())

    def test_waiting_times_only_affect_own_modality(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_waiting_times("bus", "MAXIMAL_WAITING_TIME", 2, 8)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.NONSCHEDULED):
            self.assertAlmostEqual(2, arc.getCost())
        self.assertEqual(0, mmcg.current_waiting_times["walk"])

    def test_waiting_times_for_ridepooling_and_nonscheduled(self):
        mmcg = self.build_mmcg(["rp", "walk"], create_intramodal_transfer_arcs=True)
        mmcg.set_waiting_times("rp", "MAXIMAL_WAITING_TIME", 2, 8)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.RIDEPOOLING):
            self.assertAlmostEqual(4 + 8, arc.getCost())
        mmcg.set_waiting_times("walk", "MINIMAL_WAITING_TIME", 2, 8)
        for arc in self.arcs_of_type(mmcg, MMCGArcType.NONSCHEDULED):
            self.assertAlmostEqual(2 + 2, arc.getCost())

    def test_waiting_times_without_intramodal_arcs_raises(self):
        mmcg = self.build_mmcg(["bus"])
        with self.assertRaises(LinTimException):
            mmcg.set_waiting_times("bus", "MAXIMAL_WAITING_TIME", 2, 8)

    def test_waiting_times_with_intermodal_arcs_only_raises(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_intermodal_transfer_arcs=True)
        with self.assertRaises(LinTimException):
            mmcg.set_waiting_times("bus", "MAXIMAL_WAITING_TIME", 2, 8)

    def test_invalid_waiting_model_raises(self):
        mmcg = self.build_mmcg(["bus"], create_intramodal_transfer_arcs=True)
        with self.assertRaises(ConfigInvalidValueException):
            mmcg.set_waiting_times("bus", "SOMETHING", 2, 8)

    def test_waiting_times_leave_mode_transfers_untouched(self):
        mmcg = self.build_mmcg(["bus", "walk"], create_all_transfer_arcs=True)
        mmcg.set_waiting_times("bus", "MAXIMAL_WAITING_TIME", 2, 8)
        for arc in mmcg.intermodal_arcs:
            self.assertAlmostEqual(6, arc.getCost())


# ---------------------------------------------------------------------------
# deduce_transfer_settings
# ---------------------------------------------------------------------------

class DummyConfig:
    """Minimal config stub supporting the modality keyword."""

    def __init__(self, values):
        self.values = values

    def _get(self, key, modality=None):
        if modality is not None and (key, modality) in self.values:
            return self.values[(key, modality)]
        return self.values[key]

    def getStringValue(self, key, modality=None):
        return self._get(key, modality)

    def getIntegerValue(self, key, modality=None):
        return self._get(key, modality)

    def getBooleanValue(self, key, modality=None):
        return self._get(key, modality)


class TestDeduceTransferSettings(unittest.TestCase):

    def default_values(self):
        return {
            "mm_cg_model_wait": "ZERO_COST",
            "mm_cg_intramodal_transfer_penalty": 0,
            "mm_cg_intermodal_transfer_penalty": 0,
            "mm_cg_model_intramodal_change": "TRANSFER_TIME",
            "mm_cg_model_intermodal_change": "TRANSFER_TIME",
            "mm_cg_create_intramodal_transfer_arcs": False,
            "mm_cg_create_intermodal_transfer_arcs": False,
            "mm_cg_create_all_transfer_arcs": False,
        }

    def deduce(self, overrides=None, modalities=("bus", "walk")):
        values = self.default_values()
        if overrides:
            values.update(overrides)
        return MMCG.deduce_transfer_settings(DummyConfig(values), list(modalities))

    # -- deduction from models and penalties --------------------------------

    def test_default_needs_no_transfer_arcs(self):
        self.assertEqual(
            {"create_intramodal_transfer_arcs": False,
             "create_intermodal_transfer_arcs": False,
             "create_all_transfer_arcs": False},
            self.deduce())

    def test_waiting_model_requires_intramodal_arcs(self):
        settings = self.deduce({("mm_cg_model_wait", "bus"): "MINIMAL_WAITING_TIME"})
        self.assertTrue(settings["create_intramodal_transfer_arcs"])
        self.assertFalse(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_all_transfer_arcs"])

    def test_intramodal_penalty_requires_intramodal_arcs(self):
        settings = self.deduce({("mm_cg_intramodal_transfer_penalty", "walk"): 3})
        self.assertTrue(settings["create_intramodal_transfer_arcs"])

    def test_intermodal_penalty_requires_intermodal_arcs(self):
        settings = self.deduce({"mm_cg_intermodal_transfer_penalty": 4})
        self.assertTrue(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_intramodal_transfer_arcs"])

    def test_formula_2_intramodal_requires_intramodal_arcs(self):
        settings = self.deduce({("mm_cg_model_intramodal_change", "bus"): "FORMULA_2"})
        self.assertTrue(settings["create_intramodal_transfer_arcs"])

    def test_formula_1_intramodal_needs_nothing(self):
        settings = self.deduce({("mm_cg_model_intramodal_change", "bus"): "FORMULA_1"})
        self.assertFalse(settings["create_intramodal_transfer_arcs"])

    def test_intermodal_formulas_require_all_transfer_arcs(self):
        for model in ("FORMULA_1", "FORMULA_2", "FORMULA_3"):
            with self.subTest(model=model):
                settings = self.deduce({"mm_cg_model_intermodal_change": model})
                self.assertTrue(settings["create_all_transfer_arcs"])
                self.assertFalse(settings["create_intermodal_transfer_arcs"])
                self.assertFalse(settings["create_intramodal_transfer_arcs"])

    def test_intermodal_formula_overrides_other_requirements(self):
        settings = self.deduce({
            "mm_cg_model_intermodal_change": "FORMULA_2",
            ("mm_cg_model_wait", "bus"): "MAXIMAL_WAITING_TIME",
            "mm_cg_intermodal_transfer_penalty": 7,
            ("mm_cg_intramodal_transfer_penalty", "bus"): 7,
        })
        self.assertTrue(settings["create_all_transfer_arcs"])
        self.assertFalse(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_intramodal_transfer_arcs"])

    def test_both_intra_and_intermodal_can_be_required(self):
        settings = self.deduce({
            ("mm_cg_model_wait", "bus"): "AVERAGE_WAITING_TIME",
            "mm_cg_intermodal_transfer_penalty": 2,
        })
        self.assertTrue(settings["create_intramodal_transfer_arcs"])
        self.assertTrue(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_all_transfer_arcs"])

    def test_models_are_case_insensitive(self):
        settings = self.deduce({"mm_cg_model_intermodal_change": "formula_3"})
        self.assertTrue(settings["create_all_transfer_arcs"])

    # -- explicit config wishes --------------------------------------------

    def test_config_can_request_intramodal_arcs(self):
        settings = self.deduce({"mm_cg_create_intramodal_transfer_arcs": True})
        self.assertTrue(settings["create_intramodal_transfer_arcs"])
        self.assertFalse(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_all_transfer_arcs"])

    def test_config_can_request_intermodal_arcs(self):
        settings = self.deduce({"mm_cg_create_intermodal_transfer_arcs": True})
        self.assertTrue(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_intramodal_transfer_arcs"])
        self.assertFalse(settings["create_all_transfer_arcs"])

    def test_config_can_request_both_intra_and_intermodal_arcs(self):
        settings = self.deduce({"mm_cg_create_intramodal_transfer_arcs": True,
                                "mm_cg_create_intermodal_transfer_arcs": True})
        self.assertTrue(settings["create_intramodal_transfer_arcs"])
        self.assertTrue(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_all_transfer_arcs"])

    def test_config_all_transfer_arcs_overrides_other_settings(self):
        settings = self.deduce({
            "mm_cg_create_all_transfer_arcs": True,
            ("mm_cg_model_wait", "bus"): "MAXIMAL_WAITING_TIME",
            "mm_cg_intermodal_transfer_penalty": 3,
        })
        self.assertTrue(settings["create_all_transfer_arcs"])
        self.assertFalse(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_intramodal_transfer_arcs"])

    def test_config_wishes_do_not_shrink_required_arcs(self):
        """A required arc type is created even if the config forbids it."""
        settings = self.deduce({
            "mm_cg_create_intramodal_transfer_arcs": False,
            "mm_cg_create_intermodal_transfer_arcs": False,
            ("mm_cg_model_wait", "bus"): "MAXIMAL_WAITING_TIME",
            "mm_cg_intermodal_transfer_penalty": 3,
        })
        self.assertTrue(settings["create_intramodal_transfer_arcs"])
        self.assertTrue(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_all_transfer_arcs"])

    def test_required_all_transfer_arcs_ignore_config_intra_and_intermodal(self):
        settings = self.deduce({
            "mm_cg_model_intermodal_change": "FORMULA_1",
            "mm_cg_create_intramodal_transfer_arcs": True,
            "mm_cg_create_intermodal_transfer_arcs": True,
        })
        self.assertTrue(settings["create_all_transfer_arcs"])
        self.assertFalse(settings["create_intermodal_transfer_arcs"])
        self.assertFalse(settings["create_intramodal_transfer_arcs"])


if __name__ == "__main__":
    unittest.main()