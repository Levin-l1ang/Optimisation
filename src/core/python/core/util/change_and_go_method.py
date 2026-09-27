import itertools
from collections import defaultdict
from collections.abc import MutableMapping
from typing import List, Tuple, Mapping, Dict, Callable, Optional

from core.exceptions.config_exceptions import ConfigInvalidValueException
from core.model.change_and_go import CGArcType, CGEdge, CGNode, CGNodeType
from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.lines import Line
from core.model.ptn import Link
import logging

logger = logging.getLogger(__name__)


def build_cg_graph(
        line_concept: List[Line], directed: bool, model_drive: str,
        model_wait: str, min_wait_time: int, max_wait_time: int,
        create_transfer_arcs: bool,
        model_change: str = "MINIMAL_CHANGING_TIME",
        change_penalty: int = 0, min_change_time: float = 5,
        period_length: int = 60) -> Graph[CGNode, CGEdge]:
    """
    Create a change-and-go network, which has a node for every station and 
    every line serving the station. This comes in two variants, depending on
    the parameter create_transfer_arcs. 
    - either as defined by Schöbel and Scholl with one arc for each possible
      transfer 
    - or as introduced by Goerigk and Schmidt with additional station nodes
      and one arc for each boarding or alighting 

    :param line_concept: the underlying line concept; 
        Caveat: this is not of type LinePool.
    :type line_concept: List[Line]
    :param directed: whether the lines are directed
    :type directed: bool
    :param model_drive: model to estimate driving times
    :type model_drive: str
    :param model_wait: model to estimate waiting times
    :type model_wait: str
    :param min_wait_time: minimum waiting time of a vehicle at a station
    :type min_wait_time: int
    :param max_wait_time: maximum waiting time of a vehicle at a station
    :type max_wait_time: int
    :param create_transfer_arcs: whether transfer arcs or station nodes with
        boarding and alighting arcs are created
    :type create_transfer_arcs: bool
    :param model_change: model to estimate transfer times, 
        defaults to "MINIMAL_CHANGING_TIME"
    :type model_change: str, optional
    :param change_penalty: added to estimated transfer time to model additional
        inconvenience of transfers, defaults to 0
    :type change_penalty: int, optional
    :param min_change_time: minimum transfer time, defaults to 5
    :type min_change_time: float, optional
    :param period_length: period length (used to estimate transfer times),
        defaults to 60
    :type period_length: int, optional
    :return: change-and-go network
    :rtype: Graph[CGNode, CGEdge]
    """

    wait_time = compute_wait_time(model_wait, min_wait_time, max_wait_time)

    # The waiting time is added to each driving time and subtracted at each
    # transfer arc and boarding-alighting arc pair.

    # If create_transfer_arcs is True, an origin and a destination node is 
    # created for every station, which are linked to the line-station nodes via
    # boarding and alighting arcs, respectively, of cost -wait_time/2.
    # If create_transfer_arcs is False, one central node is created for every
    # station, which is linked to the line-station-nodes via boarding and
    # alighting arcs whose costs together correspond to the difference
    # change_time + change_penalty - wait_time.

    if create_transfer_arcs:
        return _build_cg_graph_with_transfer_arcs(
            line_concept, directed, model_drive, wait_time, model_change,
            change_penalty, min_change_time, period_length)
    return _build_cg_graph_without_transfer_arcs(
        line_concept, directed, model_drive, wait_time, model_change,
        change_penalty, min_change_time, period_length)


def _build_cg_graph_with_transfer_arcs(
        line_concept: List[Line], directed: bool, model_drive: str,
        wait_time: float, model_change: str, change_penalty: int,
        min_change_time: float, period_length: int) -> Graph[CGNode, CGEdge]:

    cgn, line_stop_nodes_by_line, line_stop_nodes_by_stop = _create_line_nodes_and_arcs(
        line_concept, directed, model_drive, wait_time)


    # Create origin and destination nodes with associated arcs

    node_id = len(cgn.getNodes()) + 1
    arc_id = len(cgn.getEdges()) + 1
    origin_nodes: Dict[int, CGNode] = {}
    destination_nodes: Dict[int, CGNode] = {}

    for line in line_concept:
        for line_stop_node in line_stop_nodes_by_line[line.line_id]:
            stop_id = line_stop_node.stop_id

            if stop_id not in origin_nodes:
                origin_node = CGNode(node_id, stop_id, 0, CGNodeType.ORIGIN)
                cgn.addNode(origin_node)
                origin_nodes[stop_id] = origin_node
                node_id += 1

                assert stop_id not in destination_nodes
                destination_node = CGNode(node_id, stop_id, 0,
                                          CGNodeType.DESTINATION)
                cgn.addNode(destination_node)
                destination_nodes[stop_id] = destination_node
                node_id += 1

            origin_node = origin_nodes[stop_id]
            destination_node = destination_nodes[stop_id]
            # Boarding arc
            cgn.addEdge(CGEdge(arc_id, origin_node, line_stop_node,
                               -wait_time / 2, CGArcType.BOARD, directed=True))
            arc_id += 1
            # Alighting edge
            cgn.addEdge(CGEdge(arc_id, line_stop_node, destination_node,
                               -wait_time / 2, CGArcType.ALIGHT,
                               directed=True))
            arc_id += 1


    def cost_function_change(frequency1: int, frequency2: int):
        if model_change.upper() == "MINIMAL_CHANGING_TIME":
            change_time = min_change_time
        elif model_change.upper() == "FORMULA_1":
            change_time = period_length / frequency1 + period_length / frequency2
        elif model_change.upper() == "FORMULA_2":
            change_time = period_length / (2 * frequency1 * frequency2)
        elif model_change.upper() == "FORMULA_3":
            change_time = period_length / (2 * frequency2)
        else:
            raise ConfigInvalidValueException(["ean_model_weight_change"])
        return change_time + change_penalty - wait_time


    # Create transfer arcs

    for stop in origin_nodes:
        line_nodes_this_stop = line_stop_nodes_by_stop[stop]
        all_line_pairs = itertools.combinations(line_nodes_this_stop, 2)

        for pair in all_line_pairs:
            frequency_1 = next(line.frequency for line in line_concept
                               if line.line_id == pair[0].line_id)
            frequency_2 = next(line.frequency for line in line_concept
                               if line.line_id == pair[1].line_id)
            cgn.addEdge(CGEdge(
                arc_id, pair[0], pair[1],
                cost_function_change(frequency_1, frequency_2),
                CGArcType("\"transfer\""), directed=True))
            arc_id += 1
            cgn.addEdge(CGEdge(
                arc_id, pair[1], pair[0],
                cost_function_change(frequency_2, frequency_1),
                CGArcType("\"transfer\""), directed=True))
            arc_id += 1


    return cgn


def _build_cg_graph_without_transfer_arcs(
        line_concept: List[Line], directed: bool, model_drive: str,
        wait_time: float, model_change: str, change_penalty: int,
        min_change_time: float, period_length: int) -> Graph[CGNode, CGEdge]:

    cgn, line_stop_nodes_by_line, _ = _create_line_nodes_and_arcs(
        line_concept, directed, model_drive, wait_time)


    # Create station nodes with associated boarding and alighting arcs

    node_id = len(cgn.getNodes()) + 1
    arc_id = len(cgn.getEdges()) + 1
    station_nodes: Dict[int, CGNode] = {}
    warned_boarding = False  # flag to prevent that warning is shown multiple times
    warned_alighting = False # flag to prevent that warning is shown multiple times

    def cost_function_boarding_arc(frequency: int) -> float:
        """compute the cost of the boarding arc

        :param frequency: the frequency of the line
        :type frequency: int
        :raises ConfigInvalidValueException: if model_change is invalid
        :return: the cost of the boarding arc
        :rtype: float
        """
        nonlocal warned_boarding
        model = model_change.upper()
        if model == "MINIMAL_CHANGING_TIME":
            boarding_time = min_change_time / 2
        elif model in {"FORMULA_1", "FORMULA_3"}:
            adjustment_factor = 1 if model == "FORMULA_1" else 2
            boarding_time = period_length / frequency / adjustment_factor
            if not warned_boarding:
                logger.warning("Passenger departure adjustment time considered!")
                warned_boarding = True
        else:
            raise ConfigInvalidValueException(["ean_model_weight_change"])
        return max(0.0, boarding_time + change_penalty - wait_time)

    def cost_function_alighting_arc(frequency: int) -> float:
        """compute the cost of the alighting arc

        :param frequency: the frequency of the line
        :type frequency: int
        :raises ConfigInvalidValueException: if model_change is invalid
        :return: the cost of the alighting arc
        :rtype: float
        """
        nonlocal warned_alighting
        model = model_change.upper()
        if model == "MINIMAL_CHANGING_TIME":
            alighting_time = min_change_time / 2
        elif model == "FORMULA_1":
            alighting_time = period_length / frequency
            if not warned_alighting:
                logger.warning("Passenger arrival adjustment time considered!")
                warned_alighting = True
        elif model == "FORMULA_3":
             alighting_time = 0
        else:
            raise ConfigInvalidValueException(["ean_model_weight_change"])
        return alighting_time

    for line in line_concept:
        for line_stop_node in line_stop_nodes_by_line[line.line_id]:
            stop_id = line_stop_node.stop_id

            if stop_id not in station_nodes:
                stop_node = CGNode(node_id, stop_id, 0, CGNodeType.PLATFORM)
                cgn.addNode(stop_node)
                station_nodes[stop_id] = stop_node
                node_id += 1

            stop_node = station_nodes[stop_id]
            # Boarding arc
            cgn.addEdge(CGEdge(arc_id, stop_node, line_stop_node,
                               cost_function_boarding_arc(line.frequency),
                               CGArcType.BOARD, directed=True))
            arc_id += 1
            # Alighting edge
            cgn.addEdge(CGEdge(arc_id, line_stop_node, stop_node,
                               cost_function_alighting_arc(line.frequency),
                               CGArcType.ALIGHT, directed=True))
            arc_id += 1

    return cgn


def _create_line_nodes_and_arcs(
        line_concept: List[Line], directed: bool, model_drive: str,
        wait_time: float) -> Tuple[Graph[CGNode, CGEdge],
                                   Mapping[int, List[CGNode]],
                                   Mapping[int, List[CGNode]]]:
    """helper function to create the nodes and arcs corresponding to the lines.
    These are common to both variants of the change-and-go network.

    :param line_concept: the underlying line concept
    :type line_concept: List[Line]
    :param directed: whether the graph is directed
    :type directed: bool
    :param model_drive: model to estimate driving times
    :type model_drive: str
    :param wait_time: (estimated) waiting time of vehicles at stations
    :type wait_time: float
    :return: the created graph, a dictionary mapping every line id to the list
        of associated nodes, and a dictionary mapping every stop id to the list
        of associated nodes
    :rtype: Tuple[Graph[CGNode, CGEdge], Mapping[int, List[CGNode]], 
                  Mapping[int, List[CGNode]]]
    """

    cost_function_drive = determine_cost_function_drive(model_drive)

    cgn = SimpleDictGraph[CGNode, CGEdge]()

    node_id = 1
    arc_id = 1
    nodes_by_stop: MutableMapping[int, List[CGNode]] = defaultdict(list)
    nodes_by_line: MutableMapping[int, List[CGNode]] = defaultdict(list)

    # generate line nodes and line arcs
    for line in line_concept:
        line_path = line.getLinePath()
        stops = line_path.getNodes()
        links = line_path.getEdges()
        prev_node: Optional[CGNode] = None

        for i, stop in enumerate(stops):

            # generate line node
            line_node = CGNode(node_id, stop.stop_id, line.line_id,
                               CGNodeType.LINE_STOP)
            cgn.addNode(line_node)
            nodes_by_stop[stop.stop_id].append(line_node)
            nodes_by_line[line.line_id].append(line_node)
            node_id += 1

            # generate line arc if possible
            if i > 0:
                assert prev_node is not None
                link = links[i - 1]
                arc = CGEdge(arc_id, prev_node, line_node,
                             cost_function_drive(link) + wait_time,
                             CGArcType("\"line\""), directed=True)
                cgn.addEdge(arc)
                arc_id += 1

                if not directed:
                    arc = CGEdge(arc_id, line_node, prev_node,
                                 cost_function_drive(link) + wait_time,
                                 CGArcType("\"line\""), directed=True)
                    cgn.addEdge(arc)
                    arc_id += 1

            prev_node = line_node

    return cgn, nodes_by_line, nodes_by_stop


def compute_wait_time(model: str, min_wait_time: int, max_wait_time: int):
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
        raise ConfigInvalidValueException(["ean_model_weight_wait"])
    return wait_time


def determine_cost_function_drive(model: str) -> Callable[[Link], float]:
    """
    Determine the cost function for driving based on the given model.

    :param model: the model to use for determining the cost function
    :type model: str
    :return: the cost function for driving
    :rtype: Callable[[Link], float]
    """
    def average_drive_time(link: Link):
        return (link.getUpperBound() + link.getLowerBound()) / 2
    if model.upper() == "AVERAGE_DRIVING_TIME":
        return average_drive_time
    elif model.upper() == "MINIMAL_DRIVING_TIME":
        return Link.getLowerBound
    elif model.upper() == "MAXIMAL_DRIVING_TIME":
        return Link.getUpperBound
    elif model.upper() == "EDGE_LENGTH":
        return Link.getLength
    else:
        raise ConfigInvalidValueException(["ean_model_weight_drive"])
