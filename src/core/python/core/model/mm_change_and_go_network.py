import logging

from collections import defaultdict
from collections.abc import MutableMapping
from typing import List, Dict

from core.exceptions.config_exceptions import ConfigInvalidValueException

from core.model.lines import LinePool, Line
from core.model.ridepooling import RidepoolingPool, RidepoolingArea
from core.model.graph import Graph
from core.model.ptn import Link, Stop
from core.model.mm_change_and_go import MMCGNode, MMCGEdge, MMCGNodeType, MMCGArcType
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.util.config import Config

from core.exceptions.exceptions import LinTimException

logger = logging.getLogger(__name__)

def driving_time(model_drive: str, link: Link) -> float:
    """
    Compute the driving time of a PTN link based on the given model

    :param model_drive: the model to use ("MINIMAL_DRIVING_TIME", "MAXIMAL_DRIVING_TIME", "AVERAGE_DRIVING_TIME", "EDGE_LENGTH")
    :type model_drive: str
    :param link: the PTN link
    :type link: Link
    :raises ConfigInvalidValueException: if the given model is not known
    :return: the driving time
    :rtype: float
    """
    if model_drive.upper() == "MINIMAL_DRIVING_TIME":
        return link.getLowerBound()
    elif model_drive.upper() == "MAXIMAL_DRIVING_TIME":
        return link.getUpperBound()
    elif model_drive.upper() == "AVERAGE_DRIVING_TIME":
        return 0.5*link.getLowerBound()+0.5*link.getUpperBound()
    elif model_drive.upper() == "EDGE_LENGTH":
        return link.getLength()
    else:
        raise ConfigInvalidValueException("mm_cg_model_drive")

def compute_waiting_time(model: str, min_wait_time: int, max_wait_time: int) -> float:
    """
    Compute the waiting time based on the given model and time bounds.

    :param model: the model to use for computing waiting time
    :type model: str
    :param min_wait_time: the minimum waiting time
    :type min_wait_time: int
    :param max_wait_time: the maximum waiting time
    :type max_wait_time: int
    :return: the computed waiting time
    :rtype: float
    """
    if model.upper() == "ZERO_COST":
        wait_time = 0
    elif model.upper() == "MINIMAL_WAITING_TIME":
        wait_time = min_wait_time
    elif model.upper() == "MAXIMAL_WAITING_TIME":
        wait_time = max_wait_time
    elif model.upper() == "AVERAGE_WAITING_TIME":
        wait_time = (min_wait_time + max_wait_time) / 2
    else:
        raise ConfigInvalidValueException(["mm_cg_model_wait"])
    return wait_time


class MMCG:
    def __init__(self,
                 modalities: list[str],
                 modality_categories: Dict[str, str],
                 ptns: Dict[str, Graph[Stop, Link]],
                 line_pools: Dict[str, LinePool],
                 ridepooling_pools: Dict[str, RidepoolingPool],
                 station_times: Dict[str, float],
                 platform_times: Dict[str, float],
                 detour_factors: Dict[str, float],
                 create_intramodal_transfer_arcs: bool = False,
                 create_intermodal_transfer_arcs: bool = False,
                 create_all_transfer_arcs: bool = False,
                 model_drive: str = "MINIMAL_DRIVING_TIME"):
        """
        Create a Multimodal Change&Go Network (MCGN) for the given modalities.

        For every modality a mode specific subnetwork is created, depending on the
        category of the modality:

        * ``line-based``: one node per line and stop of the line, connected by
          ``LINE`` arcs weighted with the driving time of the corresponding PTN link.
        * ``ridepooling``: one node per area and stop of the area, connected by
          ``RIDEPOOLING`` arcs weighted with the driving time of the corresponding
          PTN link, scaled by the detour factor of the modality.
        * ``nonscheduled``: a copy of the PTN, connected by ``NONSCHEDULED`` arcs
          weighted with the driving time of the corresponding PTN link.

        Undirected PTNs result in one arc per direction.

        The transfer structure of the network is controlled by the three transfer
        flags:

        * ``create_all_transfer_arcs``: transfer arcs between every pair of nodes at
          the same stop (intramodal ``TRANSFER``, intermodal ``MODE_TRANSFER``) plus
          one origin and one destination node per stop with ``BOARD``/``ALIGHT`` arcs
          to every node at the stop. This setting overrides the other two flags.
        * ``create_intramodal_transfer_arcs``: per modality, transfer arcs between
          all driving nodes of this modality at the same stop plus modality specific
          origin/destination nodes with ``BOARD``/``ALIGHT`` arcs. Otherwise the
          intramodal transfers are modelled via one platform node per modality and
          stop.
        * ``create_intermodal_transfer_arcs``: direct ``MODE_TRANSFER`` arcs between
          the modality specific transfer points (modality origin/destination nodes
          resp. platform nodes) plus a global origin and destination node per stop.
          Otherwise the intermodal transfers are modelled via one station node per
          stop, which also serves as global origin and destination.

        Waiting times, transfer times and transfer penalties are initialised with
        the platform resp. station times and can be adjusted afterwards via
        :meth:`set_waiting_times`, :meth:`set_intramodal_transfer_times`,
        :meth:`set_intermodal_transfer_times`,
        :meth:`set_intramodal_transfer_penalty` and
        :meth:`set_intermodal_transfer_penalty`. Note that these setters are only
        applicable for certain combinations of the transfer flags, see the
        documentation of the respective method. Use
        :meth:`deduce_transfer_settings` to determine suitable transfer flags for a
        given configuration.

        :param modalities: the names of all modalities to include in the network
        :type modalities: list[str]
        :param modality_categories: the category of each modality, one of
            ``"line-based"``, ``"ridepooling"`` or ``"nonscheduled"``
        :type modality_categories: Dict[str, str]
        :param ptns: the public transportation network of each modality
        :type ptns: Dict[str, Graph[Stop, Link]]
        :param line_pools: the line pool of each line-based modality
        :type line_pools: Dict[str, LinePool]
        :param ridepooling_pools: the ridepooling pool of each ridepooling modality
        :type ridepooling_pools: Dict[str, RidepoolingPool]
        :param station_times: the intermodal transfer time of each modality, used
            (halved) for the arcs to and from station resp. transfer nodes
        :type station_times: Dict[str, float]
        :param platform_times: the intramodal transfer time of each modality
        :type platform_times: Dict[str, float]
        :param detour_factors: the detour factor of each ridepooling modality
        :type detour_factors: Dict[str, float]
        :param create_intramodal_transfer_arcs: whether explicit intramodal transfer
            arcs should be created instead of platform nodes, defaults to False
        :type create_intramodal_transfer_arcs: bool, optional
        :param create_intermodal_transfer_arcs: whether explicit intermodal transfer
            arcs should be created instead of station nodes, defaults to False
        :type create_intermodal_transfer_arcs: bool, optional
        :param create_all_transfer_arcs: whether all transfer arcs between all nodes
            at the same stop should be created; overrides the other two transfer
            settings, defaults to False
        :type create_all_transfer_arcs: bool, optional
        :param model_drive: the model used to compute the driving times, see
            :func:`driving_time`, defaults to "MINIMAL_DRIVING_TIME"
        :type model_drive: str, optional
        :raises ConfigInvalidValueException: if the given driving time model is not
            known
        """

        self.modalities = modalities
        self.modality_categories = modality_categories
        self.ptns = ptns
        self.line_pools = line_pools
        self.ridepooling_pools = ridepooling_pools
        self.station_times = station_times
        self.platform_times = platform_times
        self.detour_factors = detour_factors
        self.intramodal_transfer_arcs = create_intramodal_transfer_arcs
        self.intermodal_transfer_arcs = create_intermodal_transfer_arcs
        self.all_transfer_arcs = create_all_transfer_arcs
        if self.all_transfer_arcs and (self.intermodal_transfer_arcs or self.intramodal_transfer_arcs):
            logger.warning("MCGN will be generated with all transfer arcs, the settings for intermodal transfer arcs and intramodal transfer arcs are ignored!")
            self.intermodal_transfer_arcs = False
            self.intramodal_transfer_arcs = False
        self.model_drive = model_drive

        # BUILD NETWORK

        # initialize graph
        self.graph = SimpleDictGraph()

        # initialize ids and tracking dicts
        node_id = 1
        arc_id = 1
        self.nodes_by_modality: MutableMapping[str, List[MMCGNode]] = defaultdict(list)
        self.nodes_by_stopid: MutableMapping[int, List[MMCGNode]] = defaultdict(list)
        self.stop_ids_by_modality: MutableMapping[str, set[int]] = defaultdict(set)
        self.nodes_by_modality_and_stopid: MutableMapping[str, MutableMapping[int, List[MMCGNode]]] = {}
        for modality in self.modalities:
            self.nodes_by_modality_and_stopid[modality] = defaultdict(list)
        self.arcs_by_modality: MutableMapping[str, List[MMCGEdge]] = defaultdict(list)
        self.intermodal_arcs: List[MMCGEdge] = []
        self.origins_by_stopid: dict[int, MMCGNode] = defaultdict()
        self.destinations_by_stopid: dict[int, MMCGNode] = defaultdict()
        # the following two mappings are only used, if boarding/alighting arcs are created
        self.boarding_arcs_by_stopid: MutableMapping[int, List[MMCGEdge]] = defaultdict(list)
        self.alighting_arcs_by_stopid: MutableMapping[int, List[MMCGEdge]] = defaultdict(list)
        # track the waiting times, tranfer times and penalties that were set per modality, to be able to reset it correctly to another value with the class methods
        self.current_waiting_times: dict[str, float] = {modality: 0 for modality in self.modalities}
        self.current_intramodal_penalties: dict[str: float] = {modality: 0 for modality in self.modalities}
        self.current_intermodal_penalty: float = 0
        self.current_transfer_times: dict[int, float] = {} # arc-id: tranfer time (for all inter/intramodal tranfers and board/alight arcs)


        # build mode specific sub networks for all modes
        for modality in self.modalities:
            # LINE MODALITY SUBNETWORKS
            if self.modality_categories[modality].lower() == "line-based":
                # get the modality info
                ptn = self.ptns[modality]
                directed = ptn.isDirected()
                lpool = self.line_pools[modality]
                lines = lpool.getLines()

                # build driving stuff
                for line in lines:
                    # get line info
                    line_path = line.getLinePath()
                    line_stops = line_path.getNodes()
                    line_links = line_path.getEdges()

                    # generate line nodes and edges
                    prev_node = None
                    for i, stop in enumerate(line_stops):
                        # line node
                        line_node = MMCGNode(node_id=node_id,
                                             stop_id=stop.getId(),
                                             line_id=line.getId(),
                                             area_id = 0,
                                             type=MMCGNodeType.LINE,
                                             modality=modality)
                        self.graph.addNode(line_node)
                        node_id += 1
                        # tracking
                        self.nodes_by_modality[modality].append(line_node)
                        self.nodes_by_stopid[stop.getId()].append(line_node)
                        self.nodes_by_modality_and_stopid[modality][stop.getId()].append(line_node)
                        self.stop_ids_by_modality[modality].add(stop.getId())

                        # line arc (if possible)
                        if i > 0:
                            link = line_links[i-1]
                            arc = MMCGEdge(link_id=arc_id,
                                           left_node=prev_node,
                                           right_node=line_node,
                                           cost=driving_time(self.model_drive, link),
                                           type=MMCGArcType.LINE,
                                           directed=True)
                            self.graph.addEdge(arc)
                            arc_id += 1
                            self.arcs_by_modality[modality].append(arc)

                            if not directed:
                                arc = MMCGEdge(link_id=arc_id,
                                               left_node=line_node,
                                               right_node=prev_node,
                                               cost=driving_time(self.model_drive, link),
                                               type=MMCGArcType.LINE,
                                               directed=True)
                                self.graph.addEdge(arc)
                                arc_id += 1
                                self.arcs_by_modality[modality] .append(arc)

                        # update previous node for next iteration
                        prev_node = line_node


                # build other stuff
                if self.all_transfer_arcs:
                    # will create all transfer arcs in the end
                    pass
                elif self.intramodal_transfer_arcs:
                    # transfer arcs between all modality driving nodes at the same stop
                    for stop in self.ptns[modality].getNodes():
                        # transfer arcs at stop
                        for node1 in self.nodes_by_modality_and_stopid[modality][stop.getId()]:
                            for node2 in self.nodes_by_modality_and_stopid[modality][stop.getId()]:
                                if node1 != node2:
                                    arc = MMCGEdge(link_id=arc_id,
                                                left_node=node1,
                                                right_node=node2,
                                                cost=self.platform_times[modality],
                                                type=MMCGArcType.TRANSFER,
                                                directed=True)
                                    self.graph.addEdge(arc)
                                    arc_id += 1
                                    self.arcs_by_modality[modality].append(arc)
                                    self.current_transfer_times[arc.getId()] = arc.getCost()
                        # boarding and alighting nodes and arcs
                        # nodes
                        o_node = MMCGNode(node_id=node_id,
                                            stop_id=stop.getId(),
                                            line_id=0,
                                            area_id=0,
                                            type=MMCGNodeType.ORIGIN,
                                            modality=modality)
                        node_id += 1
                        self.graph.addNode(o_node)
                        self.nodes_by_modality[modality].append(o_node)
                        self.nodes_by_stopid[stop.getId()].append(o_node)
                        self.nodes_by_modality_and_stopid[modality][stop.getId()].append(o_node)
                        d_node = MMCGNode(node_id=node_id,
                                            stop_id=stop.getId(),
                                            line_id=0,
                                            area_id=0,
                                            type=MMCGNodeType.DESTINATION,
                                            modality=modality)
                        node_id += 1
                        self.graph.addNode(d_node)
                        self.nodes_by_modality[modality].append(d_node)
                        self.nodes_by_stopid[stop.getId()].append(d_node)
                        self.nodes_by_modality_and_stopid[modality][stop.getId()].append(d_node)
                        # arcs
                        for node in self.nodes_by_modality_and_stopid[modality][stop.getId()]:
                            if node.getType() != MMCGNodeType.ORIGIN and node.getType() != MMCGNodeType.DESTINATION:
                                # boarding
                                board = MMCGEdge(link_id=arc_id,
                                                 left_node=o_node,
                                                 right_node=node,
                                                 cost=0,
                                                 type=MMCGArcType.BOARD,
                                                 directed=True)
                                self.graph.addEdge(board)
                                arc_id += 1
                                self.arcs_by_modality[modality].append(board)
                                self.boarding_arcs_by_stopid[stop.getId()].append(board)
                                self.current_transfer_times[board.getId()] = board.getCost()
                                # alighting
                                alight = MMCGEdge(link_id=arc_id,
                                                  left_node=node,
                                                  right_node=d_node,
                                                  cost=0,
                                                  type=MMCGArcType.ALIGHT,
                                                  directed=True)
                                self.graph.addEdge(alight)
                                arc_id += 1
                                self.arcs_by_modality[modality].append(alight)
                                self.alighting_arcs_by_stopid[stop.getId()].append(alight)
                                self.current_transfer_times[alight.getId()] = alight.getCost()
                # if not all transfer arcs should be created
                else:
                    # transfers via platform node
                    for stop in self.ptns[modality].getNodes():
                        platform_node = MMCGNode(node_id=node_id,
                                                 stop_id=stop.getId(),
                                                 line_id=0,
                                                 area_id=0,
                                                 type=MMCGNodeType.PLATFORM,
                                                 modality=modality)
                        self.graph.addNode(platform_node)
                        node_id += 1
                        for node in self.nodes_by_modality_and_stopid[modality][stop.getId()]:
                            board = MMCGEdge(link_id=arc_id,
                                             left_node=platform_node,
                                             right_node=node,
                                             cost=0.5*self.platform_times[modality],
                                             type=MMCGArcType.TRANSFER,
                                             directed=True)
                            self.graph.addEdge(board)
                            arc_id += 1
                            self.arcs_by_modality[modality].append(board)
                            self.current_transfer_times[board.getId()] = board.getCost()
                            alight = MMCGEdge(link_id=arc_id,
                                              left_node=node,
                                              right_node=platform_node,
                                              cost=0.5*self.platform_times[modality],
                                              type=MMCGArcType.TRANSFER,
                                              directed=True)
                            self.graph.addEdge(alight)
                            arc_id += 1
                            self.arcs_by_modality[modality].append(alight)
                            self.current_transfer_times[alight.getId()] = alight.getCost()
                        self.nodes_by_modality[modality].append(platform_node)
                        self.nodes_by_stopid[stop.getId()].append(platform_node)
                        self.nodes_by_modality_and_stopid[modality][stop.getId()].append(platform_node)

            # RIDEPOOLING MODALITY SUBNETWORKS
            elif self.modality_categories[modality].lower() == "ridepooling":
                # get the modality info
                ptn = self.ptns[modality]
                directed = ptn.isDirected()
                rpool = self.ridepooling_pools[modality]
                areas = rpool.getAreas()

                # tracking dict
                nodes_by_area_by_stop: MutableMapping[int, MutableMapping[int, MMCGNode]] = defaultdict(None)

                # build driving stuff
                for area in areas:
                    # get area info
                    edges = area.getEdges()
                    nodes = []
                    nodes_by_area_by_stop[area.getId()] = defaultdict(None)

                    # generate area nodes and edges
                    for edge in edges:
                        left_stop = edge.getLeftNode()
                        right_stop = edge.getRightNode()

                        # generate ridepooling nodes that don't exist yet
                        if not left_stop in nodes:
                            nodes.append(left_stop)
                            node = MMCGNode(node_id=node_id,
                                            stop_id=left_stop.getId(),
                                            line_id=0,
                                            area_id=area.getId(),
                                            type=MMCGNodeType.RIDEPOOLING,
                                            modality=modality)
                            self.graph.addNode(node)
                            node_id += 1
                            self.nodes_by_modality[modality].append(node)
                            self.nodes_by_stopid[left_stop.getId()].append(node)
                            self.nodes_by_modality_and_stopid[modality][left_stop.getId()].append(node)
                            self.stop_ids_by_modality[modality].add(left_stop.getId())
                            nodes_by_area_by_stop[area.getId()][left_stop.getId()] = node
                        if not right_stop in nodes:
                            nodes.append(right_stop)
                            node = MMCGNode(node_id=node_id,
                                            stop_id=right_stop.getId(),
                                            line_id=0,
                                            area_id=area.getId(),
                                            type=MMCGNodeType.RIDEPOOLING,
                                            modality=modality)
                            self.graph.addNode(node)
                            node_id += 1
                            self.nodes_by_modality[modality].append(node)
                            self.nodes_by_stopid[right_stop.getId()].append(node)
                            self.nodes_by_modality_and_stopid[modality][right_stop.getId()].append(node)
                            self.stop_ids_by_modality[modality].add(right_stop.getId())
                            nodes_by_area_by_stop[area.getId()][right_stop.getId()] = node

                        # generate ridepooling arc
                        left = nodes_by_area_by_stop[area.getId()][left_stop.getId()]
                        right = nodes_by_area_by_stop[area.getId()][right_stop.getId()]
                        arc = MMCGEdge(link_id=arc_id,
                                       left_node=left,
                                       right_node=right,
                                       cost=driving_time(self.model_drive, edge)*self.detour_factors[modality],
                                       type=MMCGArcType.RIDEPOOLING,
                                       directed=True)
                        self.graph.addEdge(arc)
                        arc_id += 1
                        self.arcs_by_modality[modality].append(arc)

                        if not directed:
                            arc = MMCGEdge(link_id=arc_id,
                                           left_node=right,
                                           right_node=left,
                                           cost=driving_time(self.model_drive, edge)*self.detour_factors[modality],
                                           type=MMCGArcType.RIDEPOOLING,
                                           directed=True)
                            self.graph.addEdge(arc)
                            arc_id += 1
                            self.arcs_by_modality[modality].append(arc)

                # build other stuff
                if self.all_transfer_arcs:
                    # will create all transfer arcs in the end
                    pass
                elif self.intramodal_transfer_arcs:
                    # transfer arcs between all modality driving nodes at the same stop
                    for stop in self.ptns[modality].getNodes():
                        # transfer arcs at stop
                        for node1 in self.nodes_by_modality_and_stopid[modality][stop.getId()]:
                            for node2 in self.nodes_by_modality_and_stopid[modality][stop.getId()]:
                                if node1 != node2:
                                    arc = MMCGEdge(link_id=arc_id,
                                                   left_node=node1,
                                                   right_node=node2,
                                                   cost=self.platform_times[modality],
                                                   type=MMCGArcType.TRANSFER,
                                                   directed=True)
                                    self.graph.addEdge(arc)
                                    arc_id += 1
                                    self.arcs_by_modality[modality].append(arc)
                                    self.current_transfer_times[arc.getId()] = arc.getCost()
                        # boarding and alighting nodes and arcs
                        # nodes
                        o_node = MMCGNode(node_id=node_id,
                                          stop_id=stop.getId(),
                                          line_id=0,
                                          area_id=0,
                                          type=MMCGNodeType.ORIGIN,
                                          modality=modality)
                        node_id += 1
                        self.graph.addNode(o_node)
                        self.nodes_by_modality[modality].append(o_node)
                        self.nodes_by_stopid[stop.getId()].append(o_node)
                        self.nodes_by_modality_and_stopid[modality][stop.getId()].append(o_node)
                        d_node = MMCGNode(node_id=node_id,
                                          stop_id=stop.getId(),
                                          line_id=0,
                                          area_id=0,
                                          type=MMCGNodeType.DESTINATION,
                                          modality=modality)
                        node_id += 1
                        self.graph.addNode(d_node)
                        self.nodes_by_modality[modality].append(d_node)
                        self.nodes_by_stopid[stop.getId()].append(d_node)
                        self.nodes_by_modality_and_stopid[modality][stop.getId()].append(d_node)
                        # arcs
                        for node in self.nodes_by_modality_and_stopid[modality][stop.getId()]:
                            if node.getType() != MMCGNodeType.ORIGIN and node.getType() != MMCGNodeType.DESTINATION:
                                # boarding
                                board = MMCGEdge(link_id=arc_id,
                                                 left_node=o_node,
                                                 right_node=node,
                                                 cost=0,
                                                 type=MMCGArcType.BOARD,
                                                 directed=True)
                                self.graph.addEdge(board)
                                arc_id += 1
                                self.arcs_by_modality[modality].append(board)
                                self.boarding_arcs_by_stopid[stop.getId()].append(board)
                                self.current_transfer_times[board.getId()] = board.getCost()
                                # alighting
                                alight = MMCGEdge(link_id=arc_id,
                                                  left_node=node,
                                                  right_node=d_node,
                                                  cost=0,
                                                  type=MMCGArcType.ALIGHT,
                                                  directed=True)
                                self.graph.addEdge(alight)
                                arc_id += 1
                                self.arcs_by_modality[modality].append(alight)
                                self.alighting_arcs_by_stopid[stop.getId()].append(alight)
                                self.current_transfer_times[alight.getId()] = alight.getCost()
                # if not all transfer arcs should be created
                else:
                    # transfers via platform nodes
                    for stop in self.ptns[modality].getNodes():
                        platform_node = MMCGNode(node_id=node_id,
                                                    stop_id=stop.getId(),
                                                    line_id=0,
                                                    area_id=0,
                                                    type=MMCGNodeType.PLATFORM,
                                                    modality=modality)
                        self.graph.addNode(platform_node)
                        node_id += 1
                        for node in self.nodes_by_modality_and_stopid[modality][stop.getId()]:
                            board = MMCGEdge(link_id=arc_id,
                                                left_node=platform_node,
                                                right_node=node,
                                                cost=0.5*self.platform_times[modality],
                                                type=MMCGArcType.TRANSFER,
                                                directed=True)
                            self.graph.addEdge(board)
                            arc_id += 1
                            self.arcs_by_modality[modality].append(board)
                            self.current_transfer_times[board.getId()] = board.getCost()
                            alight = MMCGEdge(link_id=arc_id,
                                                left_node=node,
                                                right_node=platform_node,
                                                cost=0.5*self.platform_times[modality],
                                                type=MMCGArcType.TRANSFER,
                                                directed=True)
                            self.graph.addEdge(alight)
                            arc_id += 1
                            self.arcs_by_modality[modality].append(alight)
                            self.current_transfer_times[alight.getId()] = alight.getCost()
                        self.nodes_by_modality[modality].append(platform_node)
                        self.nodes_by_stopid[stop.getId()].append(platform_node)
                        self.nodes_by_modality_and_stopid[modality][stop.getId()].append(platform_node)

            # NONSCHEDULED MODALITY SUBNETWORKS
            elif self.modality_categories[modality].lower() == "nonscheduled":
                # get the modality info
                ptn = self.ptns[modality]
                directed = ptn.isDirected()

                # tracking
                nodes_by_stop = {}

                # build stuff (a nonscheduled CGN is the PTN)
                # nodes
                for stop in ptn.getNodes():
                    cgnode = MMCGNode(node_id=node_id,
                                      stop_id=stop.getId(),
                                      line_id=0,
                                      area_id=0,
                                      type=MMCGNodeType.NONSCHEDULED,
                                      modality=modality)
                    self.graph.addNode(cgnode)
                    node_id += 1
                    self.nodes_by_modality[modality].append(cgnode)
                    self.nodes_by_stopid[stop.getId()].append(cgnode)
                    self.nodes_by_modality_and_stopid[modality][stop.getId()].append(cgnode)
                    self.stop_ids_by_modality[modality].add(stop.getId())
                    nodes_by_stop[stop] = cgnode

                # arcs
                for link in ptn.getEdges():
                    left = nodes_by_stop[link.getLeftNode()]
                    right = nodes_by_stop[link.getRightNode()]
                    arc = MMCGEdge(link_id=arc_id,
                                   left_node=left,
                                   right_node=right,
                                   cost=driving_time(self.model_drive, link),
                                   type=MMCGArcType.NONSCHEDULED,
                                   directed=True)
                    self.graph.addEdge(arc)
                    arc_id += 1
                    self.arcs_by_modality[modality].append(arc)

                    if not directed:
                        arc = MMCGEdge(link_id=arc_id,
                                       left_node=right,
                                       right_node=left,
                                       cost=driving_time(self.model_drive, link),
                                       type=MMCGArcType.NONSCHEDULED,
                                       directed=True)
                        self.graph.addEdge(arc)
                        arc_id += 1
                        self.arcs_by_modality[modality].append(arc)

        # build intermodal transfer stuff
        if self.all_transfer_arcs:
            # transfer arcs between all nodes at the same stop

            # get all stop ids from all ptns
            all_stop_ids = []
            for modality in self.modalities:
                for stop in self.ptns[modality].getNodes():
                    if not stop.getId() in all_stop_ids:
                        all_stop_ids.append(stop.getId())

            # build transfer arcs
            for stopid in all_stop_ids:
                for node1 in self.nodes_by_stopid[stopid]:
                    for node2 in self.nodes_by_stopid[stopid]:
                        if node1 != node2:
                            if node1.getModality() == node2.getModality():
                                transfertime = self.platform_times[node1.getModality()]
                                type = MMCGArcType.TRANSFER
                            else:
                                transfertime = 0.5*self.station_times[node1.getModality()]+0.5*self.station_times[node2.getModality()]
                                type = MMCGArcType.MODE_TRANSFER
                            arc = MMCGEdge(link_id=arc_id,
                                           left_node=node1,
                                           right_node=node2,
                                           cost=transfertime,
                                           type=type,
                                           directed=True)
                            self.graph.addEdge(arc)
                            arc_id += 1
                            self.current_transfer_times[arc.getId()] = arc.getCost()
                            if node1.getModality() == node2.getModality():
                                self.arcs_by_modality[node1.getModality()].append(arc)
                            else:
                                self.intermodal_arcs.append(arc)
                # boarding and alighting nodes and arcs
                # nodes
                o_node = MMCGNode(node_id=node_id,
                                  stop_id=stopid,
                                  line_id=0,
                                  area_id=0,
                                  type=MMCGNodeType.ORIGIN,
                                  modality=None)
                self.graph.addNode(o_node)
                node_id += 1
                self.nodes_by_stopid[stopid].append(o_node)
                self.origins_by_stopid[stopid] = o_node
                d_node = MMCGNode(node_id=node_id,
                                  stop_id=stopid,
                                  line_id=0,
                                  area_id=0,
                                  type=MMCGNodeType.DESTINATION,
                                  modality=None)
                self.graph.addNode(d_node)
                node_id += 1
                self.nodes_by_stopid[stopid].append(d_node)
                self.destinations_by_stopid[stopid] = d_node
                # arcs
                for node in self.nodes_by_stopid[stopid]:
                    if node.getType() != MMCGNodeType.ORIGIN and node.getType() != MMCGNodeType.DESTINATION:
                        # boarding
                        board = MMCGEdge(link_id=arc_id,
                                         left_node=o_node,
                                         right_node=node,
                                         cost=0,
                                         type=MMCGArcType.BOARD,
                                         directed=True)
                        self.graph.addEdge(board)
                        arc_id += 1
                        self.boarding_arcs_by_stopid[stopid].append(board)
                        self.current_transfer_times[board.getId()] = board.getCost()
                        # alighting
                        alight = MMCGEdge(link_id=arc_id,
                                          left_node=node,
                                          right_node=d_node,
                                          cost=0,
                                          type=MMCGArcType.ALIGHT,
                                          directed=True)
                        self.graph.addEdge(alight)
                        arc_id += 1
                        self.alighting_arcs_by_stopid[stopid].append(alight)
                        self.current_transfer_times[alight.getId()] = alight.getCost()

        # if intermodal transfer arcs should be built
        elif self.intermodal_transfer_arcs:
            # if also intramodal transfer arcs were built
            if self.intramodal_transfer_arcs:
                # transfer arcs from each modality destination node to each modality origin node of different modality
                # boarding arcs origin node to each modality origin node
                # alighting arcs from each modality destination node to destination node

                # get all stop ids from all ptns
                all_stop_ids = []
                for modality in self.modalities:
                    for stop in self.ptns[modality].getNodes():
                        if not stop.getId() in all_stop_ids:
                            all_stop_ids.append(stop.getId())

                # collect all origin and destination nodes by stop
                origin_nodes_by_stopid: Dict[int, list[MMCGNode]] = defaultdict(list)
                destination_nodes_by_stopid: Dict[int, list[MMCGNode]] = defaultdict(list)
                for stopid in all_stop_ids:
                    for node in self.nodes_by_stopid[stopid]:
                        if node.getType() == MMCGNodeType.ORIGIN:
                            origin_nodes_by_stopid[stopid].append(node)
                        if node.getType() == MMCGNodeType.DESTINATION:
                            destination_nodes_by_stopid[stopid].append(node)

                # build transfer arcs
                for stopid in all_stop_ids:
                    for o_node in origin_nodes_by_stopid[stopid]:
                        for d_node in destination_nodes_by_stopid[stopid]:
                            if o_node.getModality() != d_node.getModality():
                                arc = MMCGEdge(link_id=arc_id,
                                                left_node=d_node,
                                                right_node=o_node,
                                                cost=0.5*self.station_times[o_node.getModality()]+0.5*self.station_times[d_node.getModality()],
                                                type=MMCGArcType.MODE_TRANSFER,
                                                directed=True)
                                self.graph.addEdge(arc)
                                arc_id += 1
                                self.intermodal_arcs.append(arc)
                                self.current_transfer_times[arc.getId()] = arc.getCost()

                    # build origin and destination nodes and boarding and alighting arcs
                    # origin node
                    origin = MMCGNode(node_id=node_id,
                                      stop_id=stopid,
                                      line_id=0,
                                      area_id=0,
                                      type=MMCGNodeType.ORIGIN,
                                      modality=None)
                    self.graph.addNode(origin)
                    node_id += 1
                    self.nodes_by_stopid[stopid].append(origin)
                    self.origins_by_stopid[stopid] = origin
                    # destination node
                    destination = MMCGNode(node_id=node_id,
                                           stop_id=stopid,
                                           line_id=0,
                                           area_id=0,
                                           type=MMCGNodeType.DESTINATION,
                                           modality=None)
                    self.graph.addNode(destination)
                    node_id += 1
                    self.nodes_by_stopid[stopid].append(destination)
                    self.destinations_by_stopid[stopid] = destination
                    # arcs
                    for o_node in origin_nodes_by_stopid[stopid]:
                        arc = MMCGEdge(link_id=arc_id,
                                       left_node=origin,
                                       right_node=o_node,
                                       cost=0,
                                       type=MMCGArcType.BOARD,
                                       directed=True)
                        self.graph.addEdge(arc)
                        arc_id += 1
                        self.boarding_arcs_by_stopid[stopid].append(arc)
                        self.current_transfer_times[arc.getId()] = arc.getCost()
                    for d_node in destination_nodes_by_stopid[stopid]:
                        arc = MMCGEdge(link_id=arc_id,
                                       left_node=d_node,
                                       right_node=destination,
                                       cost = 0,
                                       type=MMCGArcType.ALIGHT,
                                       directed=True)
                        self.graph.addEdge(arc)
                        arc_id += 1
                        self.alighting_arcs_by_stopid[stopid].append(arc)
                        self.current_transfer_times[arc.getId()] = arc.getCost()

            # if intramodal transfers happen via platform nodes
            else:
                # transfer arcs between each pair of platform nodes at the same station
                # origin and destination nodes at each station
                # boarding and alighting arcs to/from the platform nodes

                # get all stop ids from all ptns
                all_stop_ids = []
                for modality in self.modalities:
                    for stop in self.ptns[modality].getNodes():
                        if not stop.getId() in all_stop_ids:
                            all_stop_ids.append(stop.getId())

                # collect all platform nodes by stop
                platform_nodes_by_stopid: Dict[int, list[MMCGNode]] = defaultdict(list)
                for stopid in all_stop_ids:
                    for node in self.nodes_by_stopid[stopid]:
                        if node.getType() == MMCGNodeType.PLATFORM:
                            platform_nodes_by_stopid[stopid].append(node)

                # build transfer arcs
                for stopid in all_stop_ids:
                    for node1 in platform_nodes_by_stopid[stopid]:
                        for node2 in platform_nodes_by_stopid[stopid]:
                            if node1 != node2:
                                arc = MMCGEdge(link_id=arc_id,
                                               left_node=node1,
                                               right_node=node2,
                                               cost=0.5*self.station_times[node1.getModality()]+0.5*self.station_times[node2.getModality()],
                                               type=MMCGArcType.MODE_TRANSFER,
                                               directed=True)
                                self.graph.addEdge(arc)
                                arc_id += 1
                                self.intermodal_arcs.append(arc)
                                self.current_transfer_times[arc.getId()] = arc.getCost()
                                arc = MMCGEdge(link_id=arc_id,
                                               left_node=node2,
                                               right_node=node1,
                                               cost=0.5*self.station_times[node1.getModality()]+0.5*self.station_times[node2.getModality()],
                                               type=MMCGArcType.MODE_TRANSFER,
                                               directed=True)
                                self.graph.addEdge(arc)
                                arc_id += 1
                                self.intermodal_arcs.append(arc)
                                self.current_transfer_times[arc.getId()] = arc.getCost()

                    # origin and destination nodes
                    origin = MMCGNode(node_id=node_id,
                                      stop_id=stopid,
                                      line_id=0,
                                      area_id=0,
                                      type=MMCGNodeType.ORIGIN,
                                      modality=None)
                    self.graph.addNode(origin)
                    node_id += 1
                    self.nodes_by_stopid[stopid].append(origin)
                    self.origins_by_stopid[stopid] = origin
                    destination = MMCGNode(node_id=node_id,
                                           stop_id=stopid,
                                           line_id=0,
                                           area_id=0,
                                           type=MMCGNodeType.DESTINATION,
                                           modality=None)
                    self.graph.addNode(destination)
                    node_id += 1
                    self.nodes_by_stopid[stopid].append(destination)
                    self.destinations_by_stopid[stopid] = destination

                    # boarding and alighting arcs
                    for node in platform_nodes_by_stopid[stopid]:
                        # boarding
                        board = MMCGEdge(link_id=arc_id,
                                         left_node=origin,
                                         right_node=node,
                                         cost=0,
                                         type=MMCGArcType.BOARD,
                                         directed=True)
                        self.graph.addEdge(board)
                        arc_id += 1
                        self.boarding_arcs_by_stopid[stopid].append(board)
                        self.current_transfer_times[board.getId()] = board.getCost()
                        # alighting
                        alight = MMCGEdge(link_id=arc_id,
                                          left_node=node,
                                          right_node=destination,
                                          cost=0,
                                          type=MMCGArcType.ALIGHT,
                                          directed=True)
                        self.graph.addEdge(alight)
                        arc_id += 1
                        self.alighting_arcs_by_stopid[stopid].append(alight)
                        self.current_transfer_times[alight.getId()] = alight.getCost()

        # if intermodal transfers happen via station nodes
        else:
            # if intramodal transfer arcs were built
            if self.intramodal_transfer_arcs:
                # station node at each station
                # transfer arcs from destination nodes to station node
                # transfer arcs from station node to origin nodes

                # get all stop ids from all ptns
                all_stop_ids = []
                for modality in self.modalities:
                    for stop in self.ptns[modality].getNodes():
                        if not stop.getId() in all_stop_ids:
                            all_stop_ids.append(stop.getId())

                # collect all origin and destination nodes by stop
                origin_nodes_by_stopid: Dict[int, list[MMCGNode]] = defaultdict(list)
                destination_nodes_by_stopid: Dict[int, list[MMCGNode]] = defaultdict(list)
                for stopid in all_stop_ids:
                    for node in self.nodes_by_stopid[stopid]:
                        if node.getType() == MMCGNodeType.ORIGIN:
                            origin_nodes_by_stopid[stopid].append(node)
                        if node.getType() == MMCGNodeType.DESTINATION:
                            destination_nodes_by_stopid[stopid].append(node)

                # build station nodes and transfer arcs
                for stopid in all_stop_ids:
                    # station node
                    station = MMCGNode(node_id=node_id,
                                       stop_id=stopid,
                                       line_id=0,
                                       area_id=0,
                                       type=MMCGNodeType.STATION,
                                       modality=None)
                    self.graph.addNode(station)
                    node_id += 1
                    self.nodes_by_stopid[stopid].append(station)
                    self.origins_by_stopid[stopid] = station
                    self.destinations_by_stopid[stopid] = station
                    # transfer arcs
                    for node in origin_nodes_by_stopid[stopid]:
                        arc = MMCGEdge(link_id=arc_id,
                                       left_node=station,
                                       right_node=node,
                                       cost=0.5*self.station_times[node.getModality()],
                                       type=MMCGArcType.MODE_TRANSFER,
                                       directed=True)
                        self.graph.addEdge(arc)
                        arc_id += 1
                        self.intermodal_arcs.append(arc)
                        self.current_transfer_times[arc.getId()] = arc.getCost()
                    for node in destination_nodes_by_stopid[stopid]:
                        arc = MMCGEdge(link_id=arc_id,
                                        left_node=node,
                                        right_node=station,
                                        cost=0.5*self.station_times[node.getModality()],
                                        type=MMCGArcType.MODE_TRANSFER,
                                        directed=True)
                        self.graph.addEdge(arc)
                        arc_id += 1
                        self.intermodal_arcs.append(arc)
                        self.current_transfer_times[arc.getId()] = arc.getCost()

            # if intramodal transfers happen via platform nodes
            else:
                # transfer arcs from/to station node to/from platform nodes

                # get all stop ids from all ptns
                all_stop_ids = []
                for modality in self.modalities:
                    for stop in self.ptns[modality].getNodes():
                        if not stop.getId() in all_stop_ids:
                            all_stop_ids.append(stop.getId())

                # collect all platform nodes by stop
                platform_nodes_by_stopid: Dict[int, list[MMCGNode]] = defaultdict(list)
                for stopid in all_stop_ids:
                    for node in self.nodes_by_stopid[stopid]:
                        if node.getType() == MMCGNodeType.PLATFORM:
                            platform_nodes_by_stopid[stopid].append(node)

                # build station nodes and transfer arcs
                for stopid in all_stop_ids:
                    # station node
                    station = MMCGNode(node_id=node_id,
                                       stop_id=stopid,
                                       line_id=0,
                                       area_id=0,
                                       type=MMCGNodeType.STATION,
                                       modality=None)
                    self.graph.addNode(station)
                    node_id += 1
                    self.nodes_by_stopid[stopid].append(station)
                    self.origins_by_stopid[stopid] = station
                    self.destinations_by_stopid[stopid] = station

                    # transfer arcs
                    for node in platform_nodes_by_stopid[stopid]:
                        arc = MMCGEdge(link_id=arc_id,
                                       left_node=station,
                                       right_node=node,
                                       cost=0.5*self.station_times[node.getModality()],
                                       type=MMCGArcType.MODE_TRANSFER,
                                       directed=True)
                        self.graph.addEdge(arc)
                        arc_id += 1
                        self.intermodal_arcs.append(arc)
                        self.current_transfer_times[arc.getId()] = arc.getCost()
                        arc = MMCGEdge(link_id=arc_id,
                                        left_node=node,
                                        right_node=station,
                                        cost=0.5*self.station_times[node.getModality()],
                                        type=MMCGArcType.MODE_TRANSFER,
                                        directed=True)
                        self.graph.addEdge(arc)
                        arc_id += 1
                        self.intermodal_arcs.append(arc)
                        self.current_transfer_times[arc.getId()] = arc.getCost()

    def get_graph(self) -> Graph[MMCGNode, MMCGEdge]:
        """
        Get the MCGN as graph object

        :return: the MCGN
        :rtype: Graph[MMCGNode, MMCGEdge]
        """
        return self.graph

    def get_line_pools(self) -> Dict[str, LinePool]:
        """
        Get the line pools of all line-based modalities.

        :return: a dictionary mapping each line-based modality to its line pool
        :rtype: Dict[str, LinePool]
        """
        return self.line_pools

    def get_ridepooling_pools(self) -> Dict[str, RidepoolingPool]:
        """
        Get the ridepooling pools of all ridepooling modalities.

        :return: a dictionary mapping each ridepooling modality to its ridepooling
            pool
        :rtype: Dict[str, RidepoolingPool]
        """
        return self.ridepooling_pools

    def get_line_modalities(self) -> list[str]:
        """
        Get all line-based modalities of the network.

        :return: the names of all modalities of category ``"line-based"``
        :rtype: list[str]
        """
        line_modalities = []
        for modality in self.modalities:
            if self.modality_categories[modality].lower() == "line-based":
                line_modalities.append(modality)
        return line_modalities

    def get_ridepooling_modalities(self) -> list[str]:
        """
        Get all ridepooling modalities of the network.

        :return: the names of all modalities of category ``"ridepooling"``
        :rtype: list[str]
        """
        ridepooling_modalities = []
        for modality in self.modalities:
            if self.modality_categories[modality].lower() == "ridepooling":
                ridepooling_modalities.append(modality)
        return ridepooling_modalities

    def get_nonscheduled_modalities(self) -> list[str]:
        """
        Get all nonscheduled modalities of the network.

        :return: the names of all modalities of category ``"nonscheduled"``
        :rtype: list[str]
        """
        nonscheduled_modalities = []
        for modality in self.modalities:
            if self.modality_categories[modality].lower() == "nonscheduled":
                nonscheduled_modalities.append(modality)
        return nonscheduled_modalities

    def get_origin_at_stop(self, stopid: int) -> MMCGNode:
        """
        Get the global origin node of the given stop, i.e. the node where passengers
        starting their journey at this stop enter the network. Depending on the
        transfer settings this is either an origin node or a station node.

        :param stopid: the id of the stop
        :type stopid: int
        :return: the origin node at the given stop
        :rtype: MMCGNode
        """
        return self.origins_by_stopid[stopid]

    def get_destination_at_stop(self, stopid: int) -> MMCGNode:
        """
        Get the global destination node of the given stop, i.e. the node where
        passengers ending their journey at this stop leave the network. Depending on
        the transfer settings this is either a destination node or a station node.

        :param stopid: the id of the stop
        :type stopid: int
        :return: the destination node at the given stop
        :rtype: MMCGNode
        """
        return self.destinations_by_stopid[stopid]

    def get_modality_arcs(self, modality: str) -> list[MMCGEdge]:
        """
        Get all arcs belonging to the subnetwork of the given modality, i.e. driving
        arcs, intramodal transfer arcs and, if present, the modality specific
        boarding and alighting arcs.

        :param modality: the name of the modality
        :type modality: str
        :return: all arcs of the given modality
        :rtype: list[MMCGEdge]
        """
        return self.arcs_by_modality[modality]

    def get_modality_nodes(self, modality: str) -> list[MMCGNode]:
        """
        Get all nodes belonging to the subnetwork of the given modality, i.e. driving
        nodes and, depending on the transfer settings, platform nodes or modality
        specific origin and destination nodes.

        :param modality: the name of the modality
        :type modality: str
        :return: all nodes of the given modality
        :rtype: list[MMCGNode]
        """
        return self.nodes_by_modality[modality]

    def get_modality_stop_ids(self, modality: str) -> list[int]:
        """
        Get the ids of all stops that are served by driving arcs of the given
        modality, i.e. that are contained in a line resp. an area of the modality.

        :param modality: the name of the modality
        :type modality: str
        :return: the ids of all stops served by the given modality
        :rtype: list[int]
        """
        return self.stop_ids_by_modality[modality]

    def get_line_arcs(self) -> dict[str, dict[Line, list[MMCGEdge]]]:
        """
        Collect all driving arcs of the line-based modalities, grouped by modality
        and line.

        :return: a nested dictionary mapping each line-based modality and each of its
            lines to the arcs of type ``LINE`` belonging to this line
        :rtype: dict[str, dict[Line, list[MMCGEdge]]]
        """
        line_arcs: dict[str, dict[Line, list[MMCGEdge]]] = defaultdict(list)
        for modality in self.get_line_modalities():
            line_arcs[modality] = defaultdict(list)
        for arc in self.get_graph().getEdges():
            if arc.getType() == MMCGArcType.LINE:
                modality = arc.getLeftNode().getModality()
                line = self.get_line_pools()[modality].getLine(arc.getLeftNode().getLineId())
                line_arcs[modality][line].append(arc)
        return line_arcs

    def get_ridepooling_arcs(self) -> dict[str, dict[RidepoolingArea, list[MMCGEdge]]]:
        """
        Collect all driving arcs of the ridepooling modalities, grouped by modality
        and ridepooling area.

        :return: a nested dictionary mapping each ridepooling modality and each of
            its areas to the arcs of type ``RIDEPOOLING`` belonging to this area
        :rtype: dict[str, dict[RidepoolingArea, list[MMCGEdge]]]
        """
        ridepooling_arcs: dict[str, dict[RidepoolingArea, list[MMCGEdge]]] = defaultdict(list)
        for modality in self.get_ridepooling_modalities():
            ridepooling_arcs[modality] = defaultdict(list)
        for arc in self.get_graph().getEdges():
            if arc.getType() == MMCGArcType.RIDEPOOLING:
                modality = arc.getLeftNode().getModality()
                area = self.get_ridepooling_pools()[modality].getArea(arc.getLeftNode().getAreaId())
                ridepooling_arcs[modality][area].append(arc)
        return ridepooling_arcs

    def get_nonscheduled_arcs(self) -> dict[str, list[MMCGEdge]]:
        """
        Collect all driving arcs of the nonscheduled modalities, grouped by modality.

        :return: a dictionary mapping each nonscheduled modality to its arcs of type
            ``NONSCHEDULED``
        :rtype: dict[str, list[MMCGEdge]]
        """
        nonscheduled_arcs: dict[str, list[MMCGEdge]] = defaultdict(list)
        for modality in self.get_nonscheduled_modalities():
            nonscheduled_arcs[modality] = []
        for arc in self.get_graph().getEdges():
            if arc.getType() == MMCGArcType.NONSCHEDULED:
                modality = arc.getLeftNode().getModality()
                nonscheduled_arcs[modality].append(arc)
        return nonscheduled_arcs

    def set_intramodal_transfer_times(self, modality: str, model_change: str, period: int):
        """
        (Re)set the intramodal transfer times of the given line-based modality on all
        of its transfer arcs. The previously set transfer time is subtracted from the
        arc cost before the newly computed one is added, hence the method may be
        called repeatedly.

        Supported models, depending on the transfer settings of the network:

        * ``TRANSFER_TIME``: the platform time of the modality.
        * ``FORMULA_1``: ``period / f_1 + period / f_2`` with the frequencies of the
          two lines involved. Without intramodal transfer arcs, i.e. with transfers
          via platform nodes, only one line is incident, so ``period / f`` is used.
        * ``FORMULA_2``: ``period / (f_1 + f_2)``. Only available if intramodal or all
          transfer arcs are present.
        * ``FORMULA_3``: ``period / (2 * f_2)`` with the frequency of the line that is
          boarded. Without intramodal transfer arcs ``0.5 * period / (2 * f)`` is used,
          since the transfer is split into two arcs via the platform node.

        :param modality: the name of the line-based modality
        :type modality: str
        :param model_change: the model used to compute the transfer time, one of
            ``"TRANSFER_TIME"``, ``"FORMULA_1"``, ``"FORMULA_2"``, ``"FORMULA_3"``
        :type model_change: str
        :param period: the period length used in the frequency based formulas
        :type period: int
        :raises LinTimException: if the given modality is not line-based, if
            ``FORMULA_2`` is used in a network without intramodal transfer arcs or if
            the given model is not known
        """
        if self.modality_categories[modality].lower() != "line-based":
            raise LinTimException("intramodal transfer times settings only supported for line-based modalities!")
        if self.intramodal_transfer_arcs or self.all_transfer_arcs:
            for arc in self.arcs_by_modality[modality]:
                if arc.getType() == MMCGArcType.TRANSFER:
                    if model_change.upper() == "TRANSFER_TIME":
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + self.platform_times[modality])
                        self.current_transfer_times[arc.getId()] = self.platform_times[modality]
                    elif model_change.upper() == "FORMULA_1":
                        line1 = arc.getLeftNode().getLineId()
                        line2 = arc.getRightNode().getLineId()
                        f_1 = self.line_pools[modality].getLine(line1).getFrequency()
                        f_2 = self.line_pools[modality].getLine(line2).getFrequency()
                        time = period / f_1 + period / f_2
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + time)
                        self.current_transfer_times[arc.getId()] = time
                    elif model_change.upper() == "FORMULA_2":
                        line1 = arc.getLeftNode().getLineId()
                        line2 = arc.getRightNode().getLineId()
                        f_1 = self.line_pools[modality].getLine(line1).getFrequency()
                        f_2 = self.line_pools[modality].getLine(line2).getFrequency()
                        time = period / (f_1 + f_2)
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + time)
                        self.current_transfer_times[arc.getId()] = time
                    elif model_change.upper() == "FORMULA_3":
                        line1 = arc.getLeftNode().getLineId()
                        line2 = arc.getRightNode().getLineId()
                        f_1 = self.line_pools[modality].getLine(line1).getFrequency()
                        f_2 = self.line_pools[modality].getLine(line2).getFrequency()
                        time = period / (2*f_2)
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + time)
                        self.current_transfer_times[arc.getId()] = time
                    else:
                        raise LinTimException("Invalid mm_cg_model_intramodal_change!")
        else:
            for arc in self.arcs_by_modality[modality]:
                if arc.getType() == MMCGArcType.TRANSFER:
                    if model_change.upper() == "TRANSFER_TIME":
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + self.platform_times[modality])
                        self.current_transfer_times[arc.getId()] = self.platform_times[modality]
                    elif model_change.upper() == "FORMULA_1":
                        line = arc.getLeftNode().getLineId()
                        if line == 0:
                            line = arc.getRightNode().getLineId()
                        f = self.line_pools[modality].getLine(line).getFrequency()
                        time = period / f
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + time)
                        self.current_transfer_times[arc.getId()] = time
                    elif model_change.upper() == "FORMULA_2":
                        raise LinTimException("mm_cg_model_intramodal_change=FORMULA_2 not possible in a MCGN without intramodal transfer arcs!")
                    elif model_change.upper() == "FORMULA_3":
                        line = arc.getLeftNode().getLineId()
                        if line == 0:
                            line = arc.getRightNode().getLineId()
                        f = self.line_pools[modality].getLine(line).getFrequency()
                        time = 0.5* period / (2*f)
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + time)
                        self.current_transfer_times[arc.getId()] = time
                    else:
                        raise LinTimException("Invalid mm_cg_model_intramodal_change!")


    def set_intermodal_transfer_times(self, model_change: str, period: int):
        """
        (Re)set the intermodal transfer times on all intermodal transfer arcs of the
        network. The previously set transfer time is subtracted from the arc cost
        before the newly computed one is added, hence the method may be called
        repeatedly.

        Supported models:

        * ``TRANSFER_TIME``: half the station time of each of the two incident
          modalities.
        * ``FORMULA_1``, ``FORMULA_2``, ``FORMULA_3``: the frequency based formulas of
          :meth:`set_intramodal_transfer_times`, applied to the two lines involved.
          They are only available if the network was built with all transfer arcs,
          since only then the intermodal arcs connect two driving nodes. For arcs that
          are not incident to two line-based modalities, the station time based value
          is used instead.

        Note that in a network with intermodal transfers via station nodes the
        intermodal arcs are incident to a station node without modality; in this
        setup the transfer times cannot be recomputed with this method.

        :param model_change: the model used to compute the transfer time, one of
            ``"TRANSFER_TIME"``, ``"FORMULA_1"``, ``"FORMULA_2"``, ``"FORMULA_3"``
        :type model_change: str
        :param period: the period length used in the frequency based formulas
        :type period: int
        :raises LinTimException: if a formula based model is used in a network without
            all transfer arcs or if the given model is not known
        :raises KeyError: if an incident node has no modality, i.e. for intermodal arcs
            incident to a station node
        """
        if self.all_transfer_arcs:
            for arc in self.intermodal_arcs:
                if model_change.upper() == "TRANSFER_TIME":
                    transfertime = 0.5*self.station_times[arc.getLeftNode().getModality()]+0.5*self.station_times[arc.getRightNode().getModality()]
                    arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + transfertime)
                    self.current_transfer_times[arc.getId()] = transfertime
                elif model_change.upper() == "FORMULA_1":
                    if arc.getLeftNode().getModality() in self.get_line_modalities() and arc.getRightNode().getModality() in self.get_line_modalities():
                        line1 = arc.getLeftNode().getLineId()
                        line2 = arc.getRightNode().getLineId()
                        f_1 = self.line_pools[arc.getLeftNode().getModality()].getLine(line1).getFrequency()
                        f_2 = self.line_pools[arc.getRightNode().getModality()].getLine(line2).getFrequency()
                        time = period / f_1 + period / f_2
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + time)
                        self.current_transfer_times[arc.getId()] = time
                    else:
                        transfertime = 0.5*self.station_times[arc.getLeftNode().getModality()]+0.5*self.station_times[arc.getRightNode().getModality()]
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + transfertime)
                        self.current_transfer_times[arc.getId()] = transfertime
                elif model_change.upper() == "FORMULA_2":
                    if arc.getLeftNode().getModality() in self.get_line_modalities() and arc.getRightNode().getModality() in self.get_line_modalities():
                        line1 = arc.getLeftNode().getLineId()
                        line2 = arc.getRightNode().getLineId()
                        f_1 = self.line_pools[arc.getLeftNode().getModality()].getLine(line1).getFrequency()
                        f_2 = self.line_pools[arc.getRightNode().getModality()].getLine(line2).getFrequency()
                        time = period / (f_1 + f_2)
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + time)
                        self.current_transfer_times[arc.getId()] = time
                    else:
                        transfertime = 0.5*self.station_times[arc.getLeftNode().getModality()]+0.5*self.station_times[arc.getRightNode().getModality()]
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + transfertime)
                        self.current_transfer_times[arc.getId()] = transfertime
                elif model_change.upper() == "FORMULA_3":
                    if arc.getLeftNode().getModality() in self.get_line_modalities() and arc.getRightNode().getModality() in self.get_line_modalities():
                        line1 = arc.getLeftNode().getLineId()
                        line2 = arc.getRightNode().getLineId()
                        f_1 = self.line_pools[arc.getLeftNode().getModality()].getLine(line1).getFrequency()
                        f_2 = self.line_pools[arc.getRightNode().getModality()].getLine(line2).getFrequency()
                        time = period / (2*f_2)
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + time)
                        self.current_transfer_times[arc.getId()] = time
                    else:
                        transfertime = 0.5*self.station_times[arc.getLeftNode().getModality()]+0.5*self.station_times[arc.getRightNode().getModality()]
                        arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + transfertime)
                        self.current_transfer_times[arc.getId()] = transfertime
                else:
                    raise LinTimException("Invalid mm_cg_model_intermodal_change!")
        else:
            for arc in self.intermodal_arcs:
                if model_change.upper() == "TRANSFER_TIME":
                    transfertime = 0.5*self.station_times[arc.getLeftNode().getModality()]+0.5*self.station_times[arc.getRightNode().getModality()]
                    arc.setCost(arc.getCost() - self.current_transfer_times[arc.getId()] + transfertime)
                    self.current_transfer_times[arc.getId()] = transfertime
                elif model_change.upper() in ["FORMULA_1", "FORMULA_2", "FORMULA_3"]:
                    raise LinTimException("mm_cg_model_intermodal_change=FORMULA_{1,2,3} not possible in a MCGN without all transfer arcs!")
                else:
                    raise LinTimException("Invalid mm_cg_model_intermodal_change!")


    def set_intramodal_transfer_penalty(self, modality: str, penalty: float):
        """
        (Re)set the intramodal transfer penalty of the given modality by adding it to
        the cost of all intramodal transfer arcs of the modality. The previously set
        penalty is subtracted first, hence the method may be called repeatedly.

        :param modality: the name of the modality
        :type modality: str
        :param penalty: the penalty to add to each intramodal transfer
        :type penalty: float
        :raises LinTimException: if the network was built without intramodal and
            without all transfer arcs, since then a transfer is not represented by a
            single arc
        """
        if not (self.intramodal_transfer_arcs or self.all_transfer_arcs):
            raise LinTimException("Intramodal transfer penalties in a multimodal change&go network are only allowed if intramodal transfer arcs are present!")
        for arc in self.get_modality_arcs(modality):
            if arc.getType() == MMCGArcType.TRANSFER:
                arc.setCost(arc.getCost() + penalty - self.current_intramodal_penalties[modality])
        self.current_intramodal_penalties[modality] = penalty


    def set_intermodal_transfer_penalty(self, penalty: float):
        """
        (Re)set the intermodal transfer penalty by adding it to the cost of all
        intermodal transfer arcs. The previously set penalty is subtracted first,
        hence the method may be called repeatedly.

        :param penalty: the penalty to add to each intermodal transfer
        :type penalty: float
        :raises LinTimException: if the network was built without intermodal and
            without all transfer arcs, since then a transfer is not represented by a
            single arc
        """
        if not (self.intermodal_transfer_arcs or self.all_transfer_arcs):
            raise LinTimException("Intermodal transfer penalties in a multimodal change&go network are only allowed if intermodal transfer arcs are present!")
        for arc in self.intermodal_arcs:
            arc.setCost(arc.getCost() + penalty - self.current_intermodal_penalty)
        self.current_intermodal_penalty = penalty


    def set_waiting_times(self, modality: str, model_wait: str, min_wait_time: int, max_wait_time: int):
        """
        (Re)set the waiting time of the given modality. The waiting time is added to
        every driving arc of the modality and subtracted again on the transfer arcs,
        so that it is only paid once when entering a vehicle. On boarding arcs half of
        the waiting time is subtracted and on alighting arcs half of it is added.
        The previously set waiting time is compensated first, hence the method may be
        called repeatedly.

        :param modality: the name of the modality
        :type modality: str
        :param model_wait: the model used to compute the waiting time, see
            :func:`compute_waiting_time`
        :type model_wait: str
        :param min_wait_time: the minimum waiting time of the modality
        :type min_wait_time: int
        :param max_wait_time: the maximum waiting time of the modality
        :type max_wait_time: int
        :raises LinTimException: if the network was built without intramodal and
            without all transfer arcs, since then the waiting time cannot be
            compensated on the transfer arcs
        :raises ConfigInvalidValueException: if the given waiting time model is not
            known
        """
        if not (self.intramodal_transfer_arcs or self.all_transfer_arcs):
            raise LinTimException("Waiting times in a multimodal change&go network are only allowed if intramodal transfer arcs are present!")
        wait_time = compute_waiting_time(model_wait, min_wait_time, max_wait_time)

        if self.intramodal_transfer_arcs:
            for arc in self.get_modality_arcs(modality):
                if arc.getType() in [MMCGArcType.LINE, MMCGArcType.RIDEPOOLING, MMCGArcType.NONSCHEDULED]:
                    arc.setCost(arc.getCost() + wait_time - self.current_waiting_times[modality])
                if arc.getType() == MMCGArcType.TRANSFER:
                    arc.setCost(arc.getCost() - wait_time + self.current_waiting_times[modality])
                if arc.getType() == MMCGArcType.BOARD:
                    arc.setCost(arc.getCost() - wait_time/2 + self.current_waiting_times[modality]/2)
                if arc.getType() == MMCGArcType.ALIGHT:
                    arc.setCost(arc.getCost() + wait_time/2 - self.current_waiting_times[modality]/2)

        elif self.all_transfer_arcs:
            for arc in self.get_modality_arcs(modality):
                if arc.getType() in [MMCGArcType.LINE, MMCGArcType.RIDEPOOLING, MMCGArcType.NONSCHEDULED]:
                    arc.setCost(arc.getCost() + wait_time - self.current_waiting_times[modality])
                if arc.getType() == MMCGArcType.TRANSFER:
                    arc.setCost(arc.getCost() - wait_time + self.current_waiting_times[modality])
            for stop_id in self.get_modality_stop_ids(modality):
                for board in self.boarding_arcs_by_stopid[stop_id]:
                    if board.getRightNode().getModality() == modality:
                        board.setCost(board.getCost() - wait_time/2 + self.current_waiting_times[modality]/2)
                for alight in self.alighting_arcs_by_stopid[stop_id]:
                    if alight.getLeftNode().getModality() == modality:
                        alight.setCost(alight.getCost() + wait_time/2 - self.current_waiting_times[modality]/2)

        # set the class attribute to the new waiting time, to be able to reset it correctly to another value
        self.current_waiting_times[modality] = wait_time


    @staticmethod
    def deduce_transfer_settings(config: Config, modalities: list[str])-> dict[str, bool]:
        """
        Deduce the transfer settings for the MCGN construction from the given
        configuration.

        First, the transfer arcs that are *required* by the configured waiting and
        transfer models are determined:

        * Intramodal transfer arcs are needed if a modality uses a nonzero waiting
          time model, a positive intramodal transfer penalty or the intramodal change
          model ``FORMULA_2``.
        * Intermodal transfer arcs are needed if a positive intermodal transfer
          penalty is configured.
        * All transfer arcs are needed if the intermodal change model is one of
          ``FORMULA_1``, ``FORMULA_2`` or ``FORMULA_3``. In this case the other two
          settings are reset to ``False``, since all transfer arcs include them.

        Afterwards the explicit wishes of the configuration, given by the keys
        ``mm_cg_create_intramodal_transfer_arcs``,
        ``mm_cg_create_intermodal_transfer_arcs`` and
        ``mm_cg_create_all_transfer_arcs``, are taken into account. They may only
        enlarge the resulting network, never shrink it:

        * A required arc type is always created, even if the corresponding config key
          is ``false``; an info message is logged in this case.
        * A requested arc type is created even if it is not necessary for the
          configured models.
        * If ``mm_cg_create_all_transfer_arcs`` is set, all transfer arcs are created
          and the intra- and intermodal settings are reset to ``False``.

        Used config keys:

        * ``mm_cg_model_wait`` (per modality)
        * ``mm_cg_intramodal_transfer_penalty`` (per modality)
        * ``mm_cg_intermodal_transfer_penalty``
        * ``mm_cg_model_intramodal_change`` (per modality)
        * ``mm_cg_model_intermodal_change``
        * ``mm_cg_create_intramodal_transfer_arcs``,
          ``mm_cg_create_intermodal_transfer_arcs``,
          ``mm_cg_create_all_transfer_arcs``

        :param config: the configuration to read the models, penalties and explicit
            transfer arc settings from
        :type config: Config
        :param modalities: the names of all modalities of the network
        :type modalities: list[str]
        :return: a dictionary with the keys ``"create_intramodal_transfer_arcs"``,
            ``"create_intermodal_transfer_arcs"`` and
            ``"create_all_transfer_arcs"``, where at most one of
            ``"create_all_transfer_arcs"`` and the other two is ``True``
        :rtype: dict[str, bool]
        """

        settings_in_config = {
            "create_intramodal_transfer_arcs": config.getBooleanValue("mm_cg_create_intramodal_transfer_arcs"),
            "create_intermodal_transfer_arcs": config.getBooleanValue("mm_cg_create_intermodal_transfer_arcs"),
            "create_all_transfer_arcs": config.getBooleanValue("mm_cg_create_all_transfer_arcs")
        }

        settings = {"create_intramodal_transfer_arcs": False, "create_intermodal_transfer_arcs": False, "create_all_transfer_arcs": False}

        for modality in modalities:
            if config.getStringValue("mm_cg_model_wait", modality=modality).upper() != "ZERO_COST":
                if not (settings_in_config["create_intramodal_transfer_arcs"] or settings_in_config["create_all_transfer_arcs"]):
                    logger.info(f"To use mm_cg_model_wait {config.getStringValue('mm_cg_model_wait', modality=modality)}, intramodal transfer arcs are needed. Ignore Value of config key mm_cg_create_intramodal_transfer_arcs.")
                settings["create_intramodal_transfer_arcs"] = True
                break

        for modality in modalities:
            if config.getIntegerValue("mm_cg_intramodal_transfer_penalty", modality=modality) > 0:
                if not (settings_in_config["create_intramodal_transfer_arcs"] or settings_in_config["create_all_transfer_arcs"]):
                    logger.info(f"To use non-zero intramodal transfer penalties, intramodal transfer arcs are needed. Ignore Value of config key mm_cg_create_intramodal_transfer_arcs.")
                settings["create_intramodal_transfer_arcs"] = True
                break

        if config.getIntegerValue("mm_cg_intermodal_transfer_penalty") > 0:
            if not (settings_in_config["create_intermodal_transfer_arcs"] or settings_in_config["create_all_transfer_arcs"]):
                logger.info(f"To use non-zero intermodal transfer penalties, intermodal transfer arcs are needed. Ignore Value of config key mm_cg_create_intermodal_transfer_arcs.")
            settings["create_intermodal_transfer_arcs"] = True

        for modality in modalities:
            if config.getStringValue("mm_cg_model_intramodal_change", modality=modality).upper() == "FORMULA_2":
                if not (settings_in_config["create_intramodal_transfer_arcs"] or settings_in_config["create_all_transfer_arcs"]):
                    logger.info(f"To use mm_cg_model_intramodal_change {config.getStringValue('mm_cg_model_intramodal_change', modality=modality)}, intramodal transfer arcs are needed. Ignore Value of config key mm_cg_create_intramodal_transfer_arcs.")
                settings["create_intramodal_transfer_arcs"] = True
                break

        if config.getStringValue("mm_cg_model_intermodal_change").upper() in ["FORMULA_1", "FORMULA_2", "FORMULA_3"]:
            if not (settings_in_config["create_all_transfer_arcs"]):
                logger.info(f"To use mm_cg_model_intermodal_change {config.getStringValue('mm_cg_model_intermodal_change')}, all transfer arcs are needed. Ignore Value of config key mm_cg_create_all_transfer_arcs.")
            settings["create_all_transfer_arcs"] = True
            settings["create_intermodal_transfer_arcs"] = False
            settings["create_intramodal_transfer_arcs"] = False

        # check, if config settings want a "larger" network than necessary
        if settings_in_config["create_all_transfer_arcs"] and not settings["create_all_transfer_arcs"]:
            logger.info("Create MCGN with all transfer arcs, even if this is not necessary for the specified models.")
            settings["create_all_transfer_arcs"] = True
            settings["create_intermodal_transfer_arcs"] = False
            settings["create_intramodal_transfer_arcs"] = False

        elif not settings["create_all_transfer_arcs"]:
            if settings_in_config["create_intermodal_transfer_arcs"] and not settings["create_intermodal_transfer_arcs"]:
                logger.info("Create MCGN with intermodal transfer arcs, even if this is not necessary for the specified models.")
                settings["create_intermodal_transfer_arcs"] = True
            if settings_in_config["create_intramodal_transfer_arcs"] and not settings["create_intramodal_transfer_arcs"]:
                logger.info("Create MCGN with intramodal transfer arcs, even if this is not necessary for the specified models.")
                settings["create_intramodal_transfer_arcs"] = True

        return settings


    @staticmethod
    def build_MMCG_from_config_settings(config: Config, modalities: list[str], ptns: Dict[str, Graph[Stop, Link]],
                     line_pools: Dict[str, LinePool], ridepooling_pools: Dict[str, RidepoolingPool], use_concepts: bool = True) -> 'MMCG':
        """
        Build a Multimodal Change&Go Network (MCGN) from the given configuration.

        The modality categories, the intermodal and intramodal transfer times, the
        ridepooling detour factors and the driving time model are read from the
        configuration. The transfer structure of the network is deduced via
        :meth:`deduce_transfer_settings`, i.e. it is determined by the configured
        waiting and transfer models together with the explicit transfer arc settings
        ``mm_cg_create_intramodal_transfer_arcs``,
        ``mm_cg_create_intermodal_transfer_arcs`` and
        ``mm_cg_create_all_transfer_arcs``. Afterwards the waiting times, the transfer
        times and the transfer penalties are set according to the configured models,
        see :meth:`set_waiting_times`, :meth:`set_intramodal_transfer_times`,
        :meth:`set_intermodal_transfer_times`,
        :meth:`set_intramodal_transfer_penalty` and
        :meth:`set_intermodal_transfer_penalty`.

        If ``use_concepts`` is set, all lines with frequency 0 and all ridepooling
        areas without vehicles are removed from the given pools beforehand, i.e. only
        the actual line resp. ridepooling concept is used. Note that the given pools
        are modified in place. If ``use_concepts`` is not set, lines with frequency 0
        remain in the network, which may lead to a division by zero in the frequency
        based transfer time models.

        Used config keys:

        * ``modality_category`` (per modality)
        * ``mm_cg_model_drive``
        * ``mm_cg_intermodal_transfer_time`` (per modality)
        * ``mm_cg_intramodal_transfer_time`` (per modality)
        * ``mm_cg_rp_detour_factor`` (per ridepooling modality)
        * ``mm_cg_model_wait``, ``mm_cg_minimal_waiting_time``,
          ``mm_cg_maximal_waiting_time`` (per modality)
        * ``mm_cg_model_intramodal_change``,
          ``mm_cg_intramodal_transfer_penalty`` (per modality)
        * ``mm_cg_model_intermodal_change``, ``mm_cg_intermodal_transfer_penalty``
        * ``mm_cg_create_intramodal_transfer_arcs``,
          ``mm_cg_create_intermodal_transfer_arcs``,
          ``mm_cg_create_all_transfer_arcs``
        * ``period_length``

        :param config: the configuration to read all models, times, penalties and
            transfer arc settings from
        :type config: Config
        :param modalities: the names of all modalities to include in the network
        :type modalities: list[str]
        :param ptns: the public transportation network of each modality
        :type ptns: Dict[str, Graph[Stop, Link]]
        :param line_pools: the line pool of each line-based modality
        :type line_pools: Dict[str, LinePool]
        :param ridepooling_pools: the ridepooling pool of each ridepooling modality
        :type ridepooling_pools: Dict[str, RidepoolingPool]
        :param use_concepts: whether only lines with positive frequency and areas with
            at least one vehicle should be used, defaults to True
        :type use_concepts: bool, optional
        :return: the constructed MCGN
        :rtype: MMCG
        :raises ConfigInvalidValueException: if a configured driving time or waiting
            time model is not known
        :raises LinTimException: if a configured transfer time model is not known or
            not compatible with the deduced transfer settings
        :raises ZeroDivisionError: if a frequency based transfer time model is used
            and a line with frequency 0 is present, i.e. if ``use_concepts`` is False
        """

        modality_categories: dict[str, str] = {}
        for mode in modalities:
            modality_categories[mode] = config.getStringValue("modality_category", modality=mode)

        if use_concepts:
            # If only the concepts should be used, all lines and areas with frequency 0 / zero vehicles must be deleted.
            # Be careful, that if this option is false and transfer times are calculated based on frequencies, ZeroDivision Errors might occur.
            for mode in modalities:
                if modality_categories[mode] == "line-based":
                    for line in line_pools[mode].getLines():
                        if line.getFrequency() == 0:
                            line_pools[mode].removeLine(line.getId())
                elif modality_categories[mode] == "ridepooling":
                    for area in ridepooling_pools[mode].getAreas():
                        if area.getNumberOfVehicles() == 0:
                            ridepooling_pools[mode].removeArea(area.getId())

        # extract the settings for transfer arc construction based on selected waiting and transfer models
        settings = MMCG.deduce_transfer_settings(config, modalities)

        # build graph
        mcgn = MMCG(
                        modalities = modalities,
                        modality_categories = modality_categories,
                        ptns = ptns,
                        line_pools = line_pools,
                        ridepooling_pools = ridepooling_pools,
                        station_times = {mode: config.getDoubleValue("mm_cg_intermodal_transfer_time", mode) for mode in modalities},
                        platform_times = {mode: config.getDoubleValue("mm_cg_intramodal_transfer_time", mode) for mode in modalities},
                        detour_factors = {mode: config.getDoubleValue("mm_cg_rp_detour_factor", mode) for mode in modalities if modality_categories[mode] == "ridepooling"},
                        create_intermodal_transfer_arcs=settings["create_intermodal_transfer_arcs"],
                        create_intramodal_transfer_arcs=settings["create_intramodal_transfer_arcs"],
                        create_all_transfer_arcs=settings["create_all_transfer_arcs"],
                        model_drive = config.getStringValue("mm_cg_model_drive")
            )

        # set waiting times
        for modality in modalities:
            model_wait = config.getStringValue("mm_cg_model_wait", modality=modality)
            if model_wait.upper() != "ZERO_COST":
                mcgn.set_waiting_times(
                                        modality,
                                        model_wait,
                                        config.getIntegerValue("mm_cg_minimal_waiting_time", modality=modality),
                                        config.getIntegerValue("mm_cg_maximal_waiting_time", modality=modality)
                                        )

        # set transfer times and penalties
        period = config.getIntegerValue("period_length")
        for modality in mcgn.get_line_modalities():
            mcgn.set_intramodal_transfer_times(modality, config.getStringValue("mm_cg_model_intramodal_change", modality=modality), period)
            penalty =  config.getIntegerValue("mm_cg_intramodal_transfer_penalty", modality=modality)
            if penalty > 0:
                mcgn.set_intramodal_transfer_penalty(modality, penalty)

        mcgn.set_intermodal_transfer_times(config.getStringValue("mm_cg_model_intermodal_change"), period=period)
        penalty =  config.getIntegerValue("mm_cg_intermodal_transfer_penalty")
        if penalty > 0:
            mcgn.set_intermodal_transfer_penalty(penalty)

        return mcgn