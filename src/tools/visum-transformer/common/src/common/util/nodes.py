from typing import Dict

from common.model.net import Net
from common.util.constants import STOPPOINT_SECTION_HEADER, STOPPOINT_NODE_HEADER, NODE_SECTION_HEADER, \
    NODE_NUMBER_HEADER, NODE_XCOORD_HEADER, NODE_YCOORD_HEADER, ZONE_SECTION_HEADER, ZONE_NODE_HEADER, \
    ZONE_XCOORD_HEADER, ZONE_YCOORD_HEADER
from core.model.infrastructure import InfrastructureNode


def extract_nodes(net: Net) -> Dict[int, InfrastructureNode]:
    # First, read all stop points so that we know where to build stops
    stop_section = net.get_section(STOPPOINT_SECTION_HEADER)
    stop_nodes = []
    for row in stop_section.get_rows():
        stop_nodes.append(int(float(stop_section.get_entry_from_row(row, STOPPOINT_NODE_HEADER))))
    # Now we can read the actual nodes
    node_section = net.get_section(NODE_SECTION_HEADER)
    node_dict = {}
    index = 1
    for row in node_section.get_rows():
        node_number = int(float(node_section.get_entry_from_row(row, NODE_NUMBER_HEADER)))
        x_coord = node_section.get_entry_from_row(row, NODE_XCOORD_HEADER)
        y_coord = node_section.get_entry_from_row(row, NODE_YCOORD_HEADER)
        stop_possible = node_number in stop_nodes
        new_node = InfrastructureNode(index, str(node_number), float(x_coord), float(y_coord), stop_possible)
        index += 1
        node_dict[node_number] = new_node
    if ZONE_SECTION_HEADER in net.sections:
        zone_section = net.get_section(ZONE_SECTION_HEADER)
        for row in zone_section.get_rows():
            node_number = zone_section.get_entry_from_row(row, ZONE_NODE_HEADER)
            if int(float(node_number)) in node_dict:
                continue
            x_coord = zone_section.get_entry_from_row(row, ZONE_XCOORD_HEADER)
            y_coord = zone_section.get_entry_from_row(row, ZONE_YCOORD_HEADER)
            new_node = InfrastructureNode(index, str(node_number), float(x_coord), float(y_coord), False)
            index += 1
            node_dict[int(float(node_number))] = new_node
    return node_dict