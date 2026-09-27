import logging
import sys

from common.io.net import NetReader
from common.util.constants import STOPPOINT_SECTION_HEADER, STOPPOINT_NODE_HEADER, STOPPOINT_IS_TERMINAL, \
    STOPPOINT_IS_TRANSFER
from core.io.config import ConfigReader
from core.io.csv import CsvWriter
from core.io.ptn import PTNReader
from core.util.config import Config

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    logger.info("Begin reading config")
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_additional_edges_file")
    terminal_file_name = Config.getStringValueStatic("filename_terminals_file")
    terminal_header = Config.getStringValueStatic("terminals_header")
    change_station_header = Config.getStringValueStatic("change_station_header")
    change_station_file_name = Config.getStringValueStatic("filename_change_station_file")
    logger.info("Finished reading config")

    logger.info("Begin reading input files")
    net = NetReader.parse_file(net_file_name)
    ptn = PTNReader.read(read_links=False)
    logger.info("Finished reading input files")

    logger.info("Begin finding terminals and change stations")
    stop_section = net.get_section(STOPPOINT_SECTION_HEADER)
    change_stations = []
    terminals = []
    warned_missing_transfers = False
    stops_by_node = {}
    for stop in ptn.getNodes():
        stops_by_node[stop.getShortName()] = stop.getId()
    for row in stop_section.get_rows():
        node_number = stop_section.get_entry_from_row(row, STOPPOINT_NODE_HEADER)
        is_terminal = float(stop_section.get_entry_from_row(row, STOPPOINT_IS_TERMINAL))
        try:
            is_change = float(stop_section.get_entry_from_row(row, STOPPOINT_IS_TRANSFER))
        except ValueError:
            if not warned_missing_transfers:
                logger.warning(f"Did not find {STOPPOINT_IS_TRANSFER}, will set all stops as transfer nodes")
                warned_missing_transfers = True
            is_change = 1
        if is_terminal == 1:
            terminals.append(stops_by_node[node_number])
            change_stations.append(stops_by_node[node_number])
        elif is_change == 1:
            change_stations.append(stops_by_node[node_number])
    logger.info("Finished finding terminals and change stations")

    logger.info("Begin writing output files")
    terminal_writer = CsvWriter(terminal_file_name, terminal_header)
    for terminal in terminals:
        terminal_writer.writeLine([str(terminal)])
    terminal_writer.close()
    change_station_writer = CsvWriter(change_station_file_name, change_station_header)
    for change in change_stations:
        change_station_writer.writeLine([str(change)])
    change_station_writer.close()
    logger.info("Finished writing output files")
