import logging
import math
import sys

from common.io.net import NetReader
from common.util.constants import STOPPOINT_SECTION_HEADER, STOPPOINT_NODE_HEADER, STOPPOINT_IS_TERMINAL, \
    STOPPOINT_IS_TRANSFER, STOPPOINT_MIN_DWELL_TIME, LINK_SECTION_HEADER, LINK_FROM_NODE_HEADER, LINK_TO_NODE_HEADER, \
    LINK_INTERNAL_LOAD_HEADER
from core.io.config import ConfigReader
from core.io.csv import CsvWriter
from core.io.infrastructure import InfrastructureReader
from core.io.ptn import PTNWriter
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.infrastructure import InfrastructureNode
from core.model.ptn import Link, Stop, StationLimit
from core.util.config import Config

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    logger.info("Begin reading config")
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_infrastructure_file")
    terminal_file_name = Config.getStringValueStatic("filename_terminals_file")
    change_station_file_name = Config.getStringValueStatic("filename_change_station_file")
    station_limit_file_name = Config.getStringValueStatic("filename_station_limit_file")
    additional_load_file_name = Config.getStringValueStatic("filename_additional_load_file")
    time_units_per_minute = Config.getIntegerValueStatic("time_units_per_minute")
    read_forbidden_edges = Config.getBooleanValueStatic("visum_read_forbidden_edges")
    forbidden_edges_file = Config.getStringValueStatic("filename_forbidden_infrastructure_edges_file")
    forbidden_links_file = Config.getStringValueStatic("filename_forbidden_links_file")
    logger.info("Finished reading config")

    logger.info("Begin reading input files")
    net = NetReader.parse_file(net_file_name)
    infrastructure_network = InfrastructureReader.read(read_walking_edges=False)[0]
    forbidden_edges = []
    if read_forbidden_edges:
        with open(forbidden_edges_file) as input_file:
            for line in input_file:
                line = line.split("#")[0].strip()
                if not line:
                    continue
                entries = line.split(";")
                forbidden_edges.append(int(entries[0]))
    logger.info("Finished reading input files")

    logger.info("Begin processing system routes")
    ptn = SimpleDictGraph()  # type: SimpleDictGraph[Stop, Link]
    stop_section = net.get_section(STOPPOINT_SECTION_HEADER)
    next_index = 1
    change_stations = []
    terminals = []
    station_limits = {}
    stops_by_node = {}
    forbidden_links = []
    for row in stop_section.get_rows():
        node_number = stop_section.get_entry_from_row(row, STOPPOINT_NODE_HEADER)
        lintim_node = infrastructure_network.get_node_by_function(InfrastructureNode.getName, node_number)
        if not lintim_node:
            logger.error(f"Could not find visum node number {node_number} in lintim nodes")
            raise RuntimeError()
        is_terminal = float(stop_section.get_entry_from_row(row, STOPPOINT_IS_TERMINAL)) == 1
        is_change = is_terminal or float(stop_section.get_entry_from_row(row, STOPPOINT_IS_TRANSFER)) == 1
        min_dwell_time = int(stop_section.get_entry_from_row(row, STOPPOINT_MIN_DWELL_TIME).split("s")[0])

        new_stop = Stop(next_index, node_number, str(lintim_node.getId()), lintim_node.getXCoordinate(), lintim_node.getYCoordinate())
        ptn.addNode(new_stop)
        stops_by_node[lintim_node] = new_stop
        if is_terminal:
            terminals.append(next_index)
        if is_change:
            change_stations.append(next_index)
        min_dwell_time = int(math.ceil(min_dwell_time / 60 * time_units_per_minute))
        if is_change or is_terminal:
            station_limits[next_index] = StationLimit(next_index, min_dwell_time, 2 * min_dwell_time, -1, -1)
        else:
            station_limits[next_index] = StationLimit(next_index, min_dwell_time, min_dwell_time, -1, -1)
        next_index += 1
    next_index = 1
    for edge in infrastructure_network.getEdges():
        left_node = edge.getLeftNode()
        right_node = edge.getRightNode()
        link = Link(next_index, stops_by_node[left_node], stops_by_node[right_node], edge.getLength(), edge.getLowerBound(), edge.getUpperBound(), edge.isDirected())
        ptn.addEdge(link)
        if edge.getId() in forbidden_edges:
            forbidden_links.append(link)
        next_index += 1
    # Read the domestic load information
    loads = {}
    link_section = net.get_section(LINK_SECTION_HEADER)
    for row in link_section.get_rows():
        from_node = link_section.get_entry_from_row(row, LINK_FROM_NODE_HEADER)
        to_node = link_section.get_entry_from_row(row, LINK_TO_NODE_HEADER)
        from_stop = ptn.get_node_by_function(Stop.getShortName, from_node)
        to_stop = ptn.get_node_by_function(Stop.getShortName, to_node)
        link = ptn.get_edge_by_nodes(from_stop, to_stop)
        try:
            load = float(link_section.get_entry_from_row(row, LINK_INTERNAL_LOAD_HEADER))
        except ValueError:
            load = 0
        if load > 0:
            loads[(link.getId(), from_stop.getId(), to_stop.getId())] = load

    logger.info("Finished processing system routes")
    logger.info("Begin writing output files")
    PTNWriter.write(ptn)
    terminal_writer = CsvWriter(terminal_file_name, "stop-id")
    for terminal in terminals:
        terminal_writer.writeLine([str(terminal)])
    terminal_writer.close()
    change_station_writer = CsvWriter(change_station_file_name, "stop-id")
    for change in change_stations:
        change_station_writer.writeLine([str(change)])
    change_station_writer.close()
    station_limit_writer = CsvWriter(station_limit_file_name, "stop-id; min-wait-time; max-wait-time; min-change-time; max-change-time")
    for stop_id in sorted(station_limits.keys()):
        station_limit_writer.writeLine(station_limits[stop_id].toCsvStrings())
    load_writer = CsvWriter(additional_load_file_name, Config.getStringValueStatic("additional_load_header"))
    for link_id, left_stop_id, right_stop_id in sorted(loads.keys()):
        load_writer.writeLine([str(link_id), str(left_stop_id), str(right_stop_id), str(loads[(link_id, left_stop_id, right_stop_id)])])
    if forbidden_links:
        CsvWriter.writeListStatic(forbidden_links_file,
                                  forbidden_links,
                                  lambda l: l.toCsvStrings(),
                                  Link.getId,
                                  "#link-id"
                                  )
    logger.info("Finished writing output files")
