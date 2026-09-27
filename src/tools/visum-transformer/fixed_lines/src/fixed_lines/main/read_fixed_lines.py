import logging
import sys

from fixed_lines.io.lines import write_line_capacities, write_line_names
from fixed_lines.util import lines

from common.io.net import NetReader
from core.exceptions.input_exceptions import InputFileException
from core.io.config import ConfigReader
from core.io.lines import LineReader, LineWriter
from core.io.ptn import PTNReader
from core.model.lines import LinePool
from core.util.config import Config

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_fixed_lines_file")
    veh_units_net_file_name = Config.getStringValueStatic("filename_visum_vehicle_attributes_file")
    fixed_lines_file_name = Config.getStringValueStatic("filename_lc_fixed_lines")
    line_capacity_file_name = Config.getStringValueStatic("filename_lc_fixed_line_capacities")
    line_capacity_header = Config.getStringValueStatic("line_capacities_header")
    line_names_file_name = Config.getStringValueStatic("filename_lc_fixed_line_names")
    line_names_header = Config.getStringValueStatic("line_names_header")
    hour_to_consider = Config.getIntegerValueStatic("visum_hour_to_consider")
    period_length = Config.getIntegerValueStatic("period_length")
    time_units_per_minute = Config.getIntegerValueStatic("time_units_per_minute")
    logging.info("Begin reading input files")
    ptn = PTNReader.read()
    net = NetReader.parse_file(net_file_name)
    veh_units_net = NetReader.parse_file(veh_units_net_file_name)
    logging.info("Finished reading input files")
    logging.info("Begin reading fixed lines from visum file")
    try:
        current_pool = LineReader.read(ptn, read_frequencies=False)
    except InputFileException:
        logger.debug("No old pool present, create new pool from fixed lines")
        current_pool = LinePool()
    fixed_lines, capacities, line_to_name_dict = lines.get_fixed_lines_from_visum(net, veh_units_net, ptn,
                                                                                  hour_to_consider, period_length,
                                                                                  time_units_per_minute)
    line_names = lines.merge_pools(current_pool, fixed_lines, ptn.isDirected(), capacities, line_to_name_dict)
    logging.info("Finished reading fixed lines from visum file")
    logging.info("Begin writing output files")
    logging.debug("Trying to write {} lines".format(len(current_pool.getLines())))
    LineWriter.write(current_pool, write_line_concept=False)
    LineWriter.write(fixed_lines, write_pool=False, write_costs=False, concept_file_name=fixed_lines_file_name)
    write_line_capacities(capacities, line_capacity_file_name, line_capacity_header)
    write_line_names(line_names, line_names_file_name, line_names_header)
    logging.info("Finished writing output files")
    exit(0)

