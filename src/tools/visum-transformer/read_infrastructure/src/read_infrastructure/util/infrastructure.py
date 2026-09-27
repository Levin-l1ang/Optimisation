import logging
from typing import Dict, List

from core.exceptions.exceptions import LinTimException
from core.model.graph import Graph
from core.model.infrastructure import InfrastructureNode, InfrastructureEdge, WalkingEdge
from core.model.ptn import Stop
from common.model.net import Net
from common.util.constants import LINK_SECTION_HEADER, \
    LINK_FROM_NODE_HEADER, LINK_TO_NODE_HEADER, LINK_LENGTH_HEADER, LINK_MIN_TIME_HEADER, \
    LINK_VEHICLE_TYPE_HEADER, SECONDS_PER_MINUTE, \
    WALK_BACKWARD_VALUE_HEADER, WALK_FORWARD_VALUE_HEADER, WALK_DESTINATION_HEADER, \
    WALK_ORIGIN_HEADER, WALK_SECTION_HEADER, STOPPOINT_SECTION_HEADER, LINK_WALK_TIME_HEADER

logger = logging.getLogger(__name__)


def read_infrastructure_edges(net: Net, infrastructure_graph: Graph[InfrastructureNode, InfrastructureEdge],
                              walking_graph: Graph[InfrastructureNode, WalkingEdge],
                              node_by_name: Dict[int, InfrastructureNode],
                              undirected: bool, time_units_per_minute: int, conversion_length: float,
                              syscode: str, directed_walking: bool, read_forbidden_edges: bool) -> List[InfrastructureEdge]:
    link_section = net.get_section(LINK_SECTION_HEADER)
    link_min_time_header = LINK_MIN_TIME_HEADER.replace("B", syscode)
    add_walking = link_section.has_entry(LINK_WALK_TIME_HEADER)
    logger.debug("Add walking infrastructure edges")
    added_edges = []
    forbidden_edges = []
    index = 1
    walking_index = 1
    for row in link_section.get_rows():
        sys_set = link_section.get_entry_from_row(row, LINK_VEHICLE_TYPE_HEADER)
        if syscode not in sys_set and not read_forbidden_edges:
            continue
        from_node = node_by_name[int(float(link_section.get_entry_from_row(row, LINK_FROM_NODE_HEADER)))]
        to_node = node_by_name[int(float(link_section.get_entry_from_row(row, LINK_TO_NODE_HEADER)))]
        if (to_node, from_node) in added_edges and undirected:
            continue
        length = float(link_section.get_entry_from_row(row, LINK_LENGTH_HEADER).split("km")[0]) / conversion_length
        if syscode not in sys_set:
            # Find another vehicle type to read the time from
            # TODO: Do we want to set a fixed vehicle type to read here? There may be multiple...
            for header_entry in link_section.header:
                if header_entry.startswith('T_PUTSYS'):
                    min_duration = link_section.get_entry_from_row(row, header_entry)
                    if min_duration:
                        break
            if not min_duration:
                raise RuntimeError(f"Did not find duration in row {row}")
        else:
            min_duration = link_section.get_entry_from_row(row, link_min_time_header)
        min_duration_in_sec = int(float(min_duration.split("s")[0]))
        min_duration_time_units = int(min_duration_in_sec * time_units_per_minute / SECONDS_PER_MINUTE)
        max_duration = int(min_duration_time_units * 1.5)
        new_edge = InfrastructureEdge(index, from_node, to_node, length, min_duration_time_units, max_duration,
                                      not undirected)
        index += 1
        infrastructure_graph.addEdge(new_edge)
        added_edges.append((from_node, to_node))
        if syscode not in sys_set:
            forbidden_edges.append(new_edge)
        if add_walking and "Walk" in sys_set:
            walking_duration_in_sec = int(float(link_section.get_entry_from_row(row, LINK_WALK_TIME_HEADER).split("s")[0]))
            walking_duration_in_time_units = int(walking_duration_in_sec * time_units_per_minute / SECONDS_PER_MINUTE)
            new_walking_edge = WalkingEdge(walking_index, from_node, to_node, walking_duration_in_time_units, directed_walking)
            walking_index += 1
            walking_graph.addEdge(new_walking_edge)
    return forbidden_edges


def read_walking_edges(net: Net, graph: Graph[Stop, WalkingEdge], node_by_name: Dict[int, InfrastructureNode],
                       directed: bool, time_units_per_minute: int) -> List[WalkingEdge]:
    walk_time_section = net.get_section(WALK_SECTION_HEADER)
    walking_output = []
    # Add the already present edges
    for edge in graph.getEdges():
        walking_output.append(edge)
    # First, determine the header for the walk time
    if len(walk_time_section.header) > 3:
        logger.warning("Demand file has more than three columns, its not clear which column will be chosen as "
                       "the demand column!")
    walk_header = -1
    for index, header in enumerate(walk_time_section.header):
        if header != WALK_ORIGIN_HEADER and header != WALK_DESTINATION_HEADER:
            walk_header = index
            break
    if walk_header == -1:
        raise LinTimException("Unable to find walk time header, abort!")
    index = len(graph.getEdges()) + 1
    for row in walk_time_section.get_rows():
        try:
            from_node = node_by_name[int(float(walk_time_section.get_entry_from_row(row, WALK_ORIGIN_HEADER)))]
        except KeyError:
            logger.warning(f"Could not find from_node for {row}, skip")
            continue
        try:
            to_node = node_by_name[int(float(walk_time_section.get_entry_from_row(row, WALK_DESTINATION_HEADER)))]
        except KeyError:
            logger.warning(f"Could not find to_node for {row}, skip")
            continue
        # Check if the edges are not directed and we already read the other direction
        if not directed and graph.get_edge_by_nodes(to_node, from_node):
            continue
        walk_time = int(float(row[walk_header]) * time_units_per_minute)
        edge = WalkingEdge(index, from_node, to_node, walk_time, directed)
        index += 1
        walking_output.append(edge)
    return walking_output