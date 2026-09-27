import logging

from core.model.graph import Graph
from core.model.lines import LinePool, Line
from core.model.ptn import Link, Stop
from common.model.net import Net
from common.util.constants import LINE_SECTION_HEADER, LINE_ROUTE_ITEMS_SECTION_HEADER, \
    LINE_ROUTE_ITEMS_DIRECTION_HEADER, LINE_ROUTE_ITEMS_LINE_NAME_HEADER, LINE_ROUTE_ITEMS_NODE_HEADER

logger = logging.getLogger(__name__)

def find_lines(net: Net, ptn: Graph[Stop, Link], cost_factor_line: float, cost_factor_length: float,
               cost_factor_edge: float) -> LinePool:
    stops_per_line = {}  # Dict[str, List[Stop]]
    stops_by_name = {}  # Dict[str, Stop]
    lines = {}  # Dict[str, Line]
    for stop in ptn.getNodes():
        stops_by_name[stop.getShortName()] = stop
    index = 1
    for row in net.get_section(LINE_SECTION_HEADER).get_rows():
        name = row[0]
        stops_per_line[name] = []
        lines[name] = Line(index, False, cost=cost_factor_line)
        index += 1
    line_route_section = net.get_section(LINE_ROUTE_ITEMS_SECTION_HEADER)
    for row in line_route_section.get_rows():
        if line_route_section.get_entry_from_row(row, LINE_ROUTE_ITEMS_DIRECTION_HEADER) is "<":
            continue
        name = line_route_section.get_entry_from_row(row, LINE_ROUTE_ITEMS_LINE_NAME_HEADER)
        stop_name = line_route_section.get_entry_from_row(row, LINE_ROUTE_ITEMS_NODE_HEADER)
        stops_per_line[name].append(stops_by_name[stop_name])
    for line_name, stop_names in stops_per_line.items():
        logger.debug("Found line {} with stops {}".format(line_name, stop_names))
        for left_stop, right_stop in zip(stop_names, stop_names[1:]):
            link = get_link(ptn, left_stop, right_stop)
            lines[line_name].addLink(link, True, cost_factor_length, cost_factor_edge)
    result = LinePool()
    for line in lines.values():
        result.addLine(line)
    return result


def get_link(ptn: Graph[Stop, Link], left_stop: Stop, right_stop: Stop) -> Link:
    candidates = ptn.getOutgoingEdges(left_stop)
    for candidate in candidates:
        if candidate.getRightNode() == right_stop or (not candidate.isDirected() and candidate.getLeftNode() == right_stop):
            return candidate
    raise RuntimeError("Could not find link between {} and {}".format(left_stop, right_stop))