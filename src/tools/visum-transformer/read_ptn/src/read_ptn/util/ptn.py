import logging
import math
from typing import List, Dict

from common.model.net import Net
from common.util.constants import NODE_SECTION_HEADER, NODE_XCOORD_HEADER, NODE_NUMBER_HEADER, NODE_YCOORD_HEADER, \
    STOPPOINT_SECTION_HEADER, STOPPOINT_NUMBER_HEADER, STOPPOINT_NODE_HEADER, LINK_SECTION_HEADER, LINK_NUMBER_HEADER, \
    LINK_FROM_NODE_HEADER, LINK_TO_NODE_HEADER, LINK_VEHICLE_TYPE_HEADER, NON_FIXED_VSYSCODE, LINK_LENGTH_HEADER, \
    LINK_MIN_TIME_HEADER
from core.exceptions.exceptions import LinTimException
from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.infrastructure import InfrastructureNode, InfrastructureEdge
from core.model.ptn import Link, Stop


logger = logging.getLogger(__name__)


def transform_ptn(net: Net, undirected: bool, syscode: str,
                  infrastructure: Graph[InfrastructureNode, InfrastructureEdge],
                  time_units_per_minute: int) -> (Graph[Stop, Link], List[Link]):
    ptn = SimpleDictGraph()
    stops_by_node_number = create_stops(net, ptn, infrastructure)
    forbidden_links = create_links(net, ptn, undirected, syscode, stops_by_node_number, time_units_per_minute)
    return ptn, forbidden_links


def create_stops(net: Net, ptn: Graph[Stop, Link],
                 infrastructure: Graph[InfrastructureNode, InfrastructureEdge]) -> Dict[int, Stop]:
    nodes = {}
    if not infrastructure:
        node_section = net.get_section(NODE_SECTION_HEADER)
        # First read the nodes. They contain the information regarding the coordinates
        for row in node_section.get_rows():
            node_number = int(float(node_section.get_entry_from_row(row, NODE_NUMBER_HEADER)))
            x_coordinate = node_section.get_entry_from_row(row, NODE_XCOORD_HEADER)
            y_coordinate = node_section.get_entry_from_row(row, NODE_YCOORD_HEADER)
            nodes[node_number] = (x_coordinate, y_coordinate)
    # Now we can read the actual stops
    next_stop_id = 1
    stops_by_node_number = {}
    lintim_nodes_by_visum_id = {}
    if infrastructure:
        for node in infrastructure.getNodes():
            lintim_nodes_by_visum_id[int(float(node.getName()))] = node
    stop_point_section = net.get_section(STOPPOINT_SECTION_HEADER)
    for row in stop_point_section.get_rows():
        visum_stop_number = stop_point_section.get_entry_from_row(row, STOPPOINT_NUMBER_HEADER)
        node_number = stop_point_section.get_entry_from_row(row, STOPPOINT_NODE_HEADER)
        if infrastructure:
            corresponding_node = lintim_nodes_by_visum_id[int(float(node_number))]
            node_number = corresponding_node.getId()
            x_coordinate = corresponding_node.getXCoordinate()
            y_coordinate = corresponding_node.getYCoordinate()
        else:
            corresponding_node = nodes[int(float(node_number))]
            x_coordinate = corresponding_node[0]
            y_coordinate = corresponding_node[1]
        new_stop = Stop(next_stop_id, visum_stop_number, str(node_number), float(x_coordinate), float(y_coordinate))
        next_stop_id += 1
        stops_by_node_number[int(float(visum_stop_number))] = new_stop
        ptn.addNode(new_stop)
    return stops_by_node_number


def create_links(net: Net, ptn: Graph[Stop, Link], undirected: bool, syscode: str,
                 stops_by_node_number: Dict[int, Stop],
                 time_units_per_minute: int) -> List[Link]:
    link_section = net.get_section(LINK_SECTION_HEADER)
    read_links = []
    forbidden_tuples = []
    forbidden_links = []
    non_fixed_vsyscode = NON_FIXED_VSYSCODE.replace("B", syscode)
    link_min_time_header = LINK_MIN_TIME_HEADER.replace("B", syscode)
    next_link_id = 1
    for row in link_section.get_rows():
        from_node_id = link_section.get_entry_from_row(row, LINK_FROM_NODE_HEADER)
        to_node_id = link_section.get_entry_from_row(row, LINK_TO_NODE_HEADER)
        vehicle_types = link_section.get_entry_from_row(row, LINK_VEHICLE_TYPE_HEADER).split(",")
        # In some cases we want to skip. First, when we already read this and it was not forbidden
        if (from_node_id, to_node_id) in read_links and (from_node_id, to_node_id) not in forbidden_tuples:
            continue
        # The same for an undirected network and the backwards direction
        if undirected and (to_node_id, from_node_id) in read_links and (to_node_id, from_node_id) not in forbidden_tuples:
            continue
        # Have we already read it and it was forbidden? Than we only read again if the new version is not forbidden
        if (((from_node_id, to_node_id) in read_links) or (undirected and (to_node_id, from_node_id) in read_links)) and non_fixed_vsyscode not in vehicle_types:
            continue
        length = link_section.get_entry_from_row(row, LINK_LENGTH_HEADER)
        min_time = link_section.get_entry_from_row(row, link_min_time_header)
        # Transform the data for LinTim
        try:
            from_node = stops_by_node_number[int(float(from_node_id))]
        except KeyError:
            raise LinTimException(f"Could not find stop point with id {from_node_id}!")
        try:
            to_node = stops_by_node_number[int(float(to_node_id))]
        except KeyError:
            raise LinTimException(f"Could not find stop point with id {to_node_id}!")
        length = float(length.split("km")[0])
        # Find the correct vehicle type, if there are multiple
        if "," in min_time:
            # Take the non fixed entry if it is present. Otherwise, just take the first
            if NON_FIXED_VSYSCODE not in vehicle_types:
                min_time = link_section.get_entry_from_row(row, link_min_time_header).split(",")[0]
            else:
                vsys_index = vehicle_types.index(NON_FIXED_VSYSCODE)
                min_time = link_section.get_entry_from_row(row, link_min_time_header).split(",")[vsys_index]
        # We have a splitted min time format, lets check if we need to process further
        if NON_FIXED_VSYSCODE not in vehicle_types:
            # Find another vehicle type to read the time from
            # TODO: Do we want to set a fixed vehicle type to read here? There may be multiple...
            for header_entry in link_section.header:
                if header_entry.startswith('T_PUTSYS'):
                    min_time = link_section.get_entry_from_row(row, header_entry)
                    if min_time:
                        break
        min_time_seconds = int(float(min_time.split("s")[0]))
        min_time_time_units = int(math.ceil(min_time_seconds*time_units_per_minute/60))
        new_link = Link(next_link_id, from_node, to_node, length, min_time_time_units, math.ceil(min_time_time_units*1.25), not undirected)
        next_link_id += 1
        ptn.addEdge(new_link)
        if NON_FIXED_VSYSCODE not in vehicle_types:
            forbidden_tuples.append((from_node_id, to_node_id))
            forbidden_links.append(new_link)
        read_links.append((from_node_id, to_node_id))
    return forbidden_links