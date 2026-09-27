import logging
import sys
from typing import Dict, Tuple

from core.io.config import ConfigReader
from core.io.lines import LineReader
from core.io.periodic_ean import PeriodicEANReader
from core.io.ptn import PTNReader
from core.model.lines import Line, LinePool
from core.util.config import Config
from fixed_times.io.fixed_timetable import write_fixed_timetable
from common.io.net import NetReader
from fixed_times.io.lines import read_line_names
from fixed_times.util.times import get_fixed_times

logger = logging.getLogger(__name__)


def find_fixed_lines_in_pool(whole_pool: LinePool, line_names: Dict[int, Tuple[str, str, str]]) -> Dict[Tuple[str, str, str], Line]:
    """
    Find the fixed line in the line pool and store them by their name from the config
    :param ptn: the ptn
    :param whole_pool: the pool with all lines
    :return: a dict containing the fixed lines, stored by their name
    """
    result: Dict[Tuple[str, str, str], Line] = {}
    for line in whole_pool.getLines():
        if line.getId() in line_names and line.getFrequency() > 0:
            result[line_names[line.getId()]] = line
    return result


if __name__ == '__main__':
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_fixed_times_file")
    fixed_timetable_file_name = Config.getStringValueStatic("filename_tim_fixed_times")
    line_names_file_name = Config.getStringValueStatic("filename_lc_fixed_line_names")
    period_length = Config.getIntegerValueStatic("period_length")
    hour_to_consider = Config.getIntegerValueStatic("visum_hour_to_consider")
    time_units_per_minute = Config.getIntegerValueStatic("time_units_per_minute")
    logging.info("Begin reading input files")
    ptn = PTNReader.read()
    pool = LineReader.read(ptn, read_frequencies=True, read_costs=False)
    ean, _ = PeriodicEANReader.read()
    net = NetReader.parse_file(net_file_name)
    line_names = read_line_names(line_names_file_name)
    logging.info("Finished reading input files")
    logging.info("Begin transforming visum files")
    line_dict = find_fixed_lines_in_pool(pool, line_names)
    fixed_ean = get_fixed_times(net, ean, line_dict, ptn.isDirected(), period_length, hour_to_consider, time_units_per_minute)
    logger.debug("Found fixed ean:")
    logger.debug(fixed_ean)
    logging.info("Finished transforming visum files")
    logging.info("Begin writing output files")
    write_fixed_timetable(fixed_ean)
    logging.info("Finished writing output files")
    exit(0)
