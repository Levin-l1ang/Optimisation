import logging
from typing import Dict, Tuple, List

from common.model.net import Net
from common.util.constants import TIMETABLE_SECTION_HEADER, TIMETABLE_DEPARTURE_HEADER, TIMETABLE_HOUR_TO_CONSIDER, \
    TIMETABLE_JOURNEY_NUMBER_HEADER, TIMETABLE_LINE_NAME_HEADER, TIMETABLE_LINE_DIRECTION_HEADER, \
    TIMETABLE_NODE_NUMBER_HEADER, TIMETABLE_ARRIVAL_HEADER, TIMETABLE_INDEX_HEADER
from core.exceptions.exceptions import LinTimException
from core.model.graph import Graph
from core.model.lines import Line, LinePool
from core.model.ptn import Link, Stop


logger = logging.getLogger(__name__)


def reconstruct_lines(pool: LinePool, ptn: Graph[Stop, Link], timetable_net_file: Net) -> Dict[str, Tuple[Line, str]]:
    line_dict = {}
    timetable_section = timetable_net_file.get_section(TIMETABLE_SECTION_HEADER)
    last_vehicle_journey_number = -1
    name_to_visum_lines = {}  # type: Dict[str, Dict[str, List[str]]]
    for timetable_item in timetable_section.get_rows():
        vehicle_journey_number = timetable_section.get_entry_from_row(timetable_item, TIMETABLE_JOURNEY_NUMBER_HEADER)
        # Can we just skip the timetable item?
        departure_string = timetable_section.get_entry_from_row(timetable_item, TIMETABLE_DEPARTURE_HEADER)
        if not departure_string:
            departure_string = timetable_section.get_entry_from_row(timetable_item, TIMETABLE_ARRIVAL_HEADER)
        departure_hour = int(departure_string.split(":")[0])
        if last_vehicle_journey_number != vehicle_journey_number and (departure_hour != TIMETABLE_HOUR_TO_CONSIDER or
                                                                      int(timetable_section.get_entry_from_row(timetable_item, TIMETABLE_INDEX_HEADER)) != 1):
            continue
        name = timetable_section.get_entry_from_row(timetable_item, TIMETABLE_LINE_NAME_HEADER)
        direction = timetable_section.get_entry_from_row(timetable_item, TIMETABLE_LINE_DIRECTION_HEADER)
        # Start a new line if necessary
        if vehicle_journey_number != last_vehicle_journey_number:
            if name not in name_to_visum_lines:
                name_to_visum_lines[name] = {}
            if direction not in name_to_visum_lines[name]:
                name_to_visum_lines[name][direction] = []
            else:
                # We have a new trip, but the corresponding direction is already present. Skip!
                continue
        # Now add the current read node to the line dictionary
        node = timetable_section.get_entry_from_row(timetable_item, TIMETABLE_NODE_NUMBER_HEADER)
        name_to_visum_lines[name][direction].append(node)
        last_vehicle_journey_number = vehicle_journey_number
    # Now we have a list of nodes for each line name and direction, try to find the correct LinTim line
    used_lines = set()
    for name in name_to_visum_lines.keys():
        forward_direction = name_to_visum_lines[name][">"]
        # Transform to list of stops
        forward_stops = [ptn.get_node_by_function(Stop.getShortName, node_name) for node_name in forward_direction]
        # Search for the corresponding line
        for line in pool.getLines():
            if line in used_lines:
                continue
            if forward_stops == line.getLinePath().getNodes():
                line_dict[name] = (line, ">")
                used_lines.add(line)
                break
            if not line.directed and "<" in name_to_visum_lines[name] and forward_stops[::-1] == line.getLinePath().getNodes():
                line_dict[name] = (line, "<")
                used_lines.add(line)
                break
        # Did we find a line?
        if name not in line_dict:
            logger.error("Could not find all lines! There seems to be no line in the linepool correspondend to " + name)
            raise LinTimException("Could not reconstruct timetable")
    return line_dict
