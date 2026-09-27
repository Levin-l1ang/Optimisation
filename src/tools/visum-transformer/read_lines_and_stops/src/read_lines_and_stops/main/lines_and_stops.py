import logging
import sys

from common.io.net import NetReader
from core.algorithm.dijkstra import Dijkstra
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.infrastructure import InfrastructureReader
from core.io.lines import LineWriter
from core.io.ptn import PTNWriter
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.infrastructure import InfrastructureNode, InfrastructureEdge
from core.model.lines import Line, LinePool
from core.model.ptn import Stop, Link
from core.util.config import Config
from read_lines_and_stops.util.headers import JOURNEY_SECTION_HEADER, LINE_NAME_HEADER, \
    VEHICLE_JOURNEY_NUMBER_HEADER, DEPARTURE_HEADER, DIRECTION_HEADER, LINE_INDEX_HEADER, NODE_INDEX_HEADER

logger = logging.getLogger()


def get_edge(graph, left_node, right_node):
    for candidate in graph.getOutgoingEdges(left_node):
        if candidate.getRightNode() == right_node or (not candidate.isDirected() and candidate.getLeftNode() == right_node):
            return candidate
    return None


if __name__ == '__main__':
    logger.info("Start reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    ConfigReader.read(sys.argv[1])
    logger.info("Finished reading configuration")

    logger.info("Start reading input files")
    net = NetReader.parse_file(Config.getStringValueStatic("filename_visum_timetable_file"))
    infrastructure, _ = InfrastructureReader.read(read_walking_edges=False)
    logger.info("Finished reading input files")

    logger.info("Start reconstructing lines and stops")
    ptn = SimpleDictGraph()
    section = net.get_section(JOURNEY_SECTION_HEADER)
    read_lines = {}
    line_occurences = {}
    first_journey_number = {}
    next_stop_id = 1
    # Read node sequences
    for row in section.get_rows():
        direction = section.get_entry_from_row(row, DIRECTION_HEADER)
        if direction == "<":
            continue
        line_name = section.get_entry_from_row(row, LINE_NAME_HEADER)
        journey_number = int(section.get_entry_from_row(row, VEHICLE_JOURNEY_NUMBER_HEADER))
        if line_name not in read_lines:
            read_lines[line_name] = []
            line_occurences[line_name] = set()
            first_journey_number[line_name] = journey_number
        departure_hour = section.get_entry_from_row(row, DEPARTURE_HEADER).split(":")[0]
        line_index = int(section.get_entry_from_row(row, LINE_INDEX_HEADER))
        if line_index == 1 and departure_hour and int(departure_hour) == 5:
            line_occurences[line_name].add(journey_number)
        node_index = int(section.get_entry_from_row(row, NODE_INDEX_HEADER))
        if journey_number == first_journey_number[line_name]:
            # Check if a stop already exists
            stop = ptn.get_node_by_function(Stop.getShortName, str(node_index))
            if not stop:
                node = infrastructure.get_node_by_function(InfrastructureNode.getName, str(node_index))
                stop = Stop(next_stop_id, node.getName(), node.getName(), node.getXCoordinate(), node.getYCoordinate())
                next_stop_id += 1
                ptn.addNode(stop)
            read_lines[line_name].append(stop)

    # Construct lines
    next_line_id = 1
    next_link_id = 1
    pool = LinePool()
    for name, stop_list in read_lines.items():
        line = Line(next_line_id, False)
        next_line_id += 1
        pool.addLine(line)
        stop_pairs = [(stop_list[i], stop_list[i + 1]) for i in range(len(stop_list) - 1)]
        for stop_pair in stop_pairs:
            left_stop = stop_pair[0]
            right_stop = stop_pair[1]
            # First, check if edge is already in the ptn
            link = get_edge(ptn, left_stop, right_stop)
            if not link:
                # Create a new edge, based on the shortest path in the infrastructure network
                source_node = infrastructure.get_node_by_function(InfrastructureNode.getName, left_stop.getShortName())
                target_node = infrastructure.get_node_by_function(InfrastructureNode.getName, right_stop.getShortName())
                dijkstra = Dijkstra(infrastructure, source_node, InfrastructureEdge.getLength)
                length = dijkstra.computeShortestPath(target_node)
                path = dijkstra.getPath(target_node)
                lower_bound = sum([edge.getLowerBound() for edge in path.getEdges()])
                upper_bound = sum([edge.getUpperBound() for edge in path.getEdges()])
                link = Link(next_link_id, left_stop, right_stop, length, lower_bound, upper_bound, False)
                ptn.addEdge(link)
                next_link_id += 1
            line.addLink(link)
        line.setFrequency(len(line_occurences[name]))
    logger.info("Finished reconstructing lines and stops")

    logger.info("Begin writing output files")
    PTNWriter.write(ptn)
    LineWriter.write(pool)
    logger.info("Finished writing output files")
