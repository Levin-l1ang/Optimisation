import logging
from typing import List, Dict

import math

from common.model.net import Net
from common.util.constants import ZONE_SECTION_HEADER, ZONE_NODE_HEADER, ZONE_XCOORD_HEADER, ZONE_YCOORD_HEADER, \
    LINK_SECTION_HEADER, LINK_NUMBER_HEADER, LINK_FROM_NODE_HEADER, LINK_TO_NODE_HEADER, LINK_LENGTH_HEADER, \
    LINK_WALK_TIME_HEADER, SECONDS_PER_MINUTE
from core.model.graph import Graph
from core.model.ptn import Stop, Link
from read_demand.model.zone import Zone, ZonePath

logger = logging.getLogger(__name__)


def get_zones(net: Net) -> Dict[int, Zone]:
    zone_section = net.get_section(ZONE_SECTION_HEADER)
    zones = {}
    for row in zone_section.get_rows():
        zone_id = int(zone_section.get_entry_from_row(row, ZONE_NODE_HEADER))
        zone_x_coord = float(zone_section.get_entry_from_row(row, ZONE_XCOORD_HEADER))
        zone_y_coord = float(zone_section.get_entry_from_row(row, ZONE_YCOORD_HEADER))
        zones[zone_id] = Zone(zone_id, zone_x_coord, zone_y_coord)
    return zones


def get_zone_paths(net: Net, zones: Dict[int, Zone], ptn: Graph[Stop, Link], time_units_per_minute: int) -> List[ZonePath]:
    link_section = net.get_section(LINK_SECTION_HEADER)
    paths = []
    for row in link_section.get_rows():
        link_id = int(link_section.get_entry_from_row(row, LINK_NUMBER_HEADER))
        from_zone_id = int(link_section.get_entry_from_row(row, LINK_FROM_NODE_HEADER))
        if from_zone_id not in zones:
            continue
        to_stop_id = int(link_section.get_entry_from_row(row, LINK_TO_NODE_HEADER))
        if not ptn.getNode(to_stop_id):
            logging.warning("Found a foot path not ending at a stop, link no is {}".format(link_id))
        length = float(link_section.get_entry_from_row(row, LINK_LENGTH_HEADER).split("km")[0])
        duration = int(math.ceil(int(link_section.get_entry_from_row(row, LINK_WALK_TIME_HEADER).split("s")[0])/SECONDS_PER_MINUTE*time_units_per_minute))
        paths.append(ZonePath(link_id, from_zone_id, to_stop_id, length, duration))
    return paths