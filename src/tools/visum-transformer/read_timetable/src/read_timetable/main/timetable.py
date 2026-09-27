import logging
import sys

from common.io.net import NetReader
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.lines import LineReader
from core.io.periodic_ean import PeriodicEANReader, PeriodicEANWriter
from core.io.ptn import PTNReader
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.util.config import Config
from common.util.line_reconstructor import reconstruct_lines
from read_timetable.util.timetable_reconstructor import reconstruct_timetable

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    ConfigReader.read(sys.argv[1])
    logger.info("Begin reading configuration")
    timetable_file_name = Config.getStringValueStatic("filename_visum_timetable_file")
    period_length = Config.getIntegerValueStatic("period_length")
    time_units_per_minute = Config.getIntegerValueStatic("time_units_per_minute")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    timetable_net = NetReader.parse_file(timetable_file_name)
    ptn = PTNReader.read(ptn=SimpleDictGraph())
    line_concept = LineReader.read(ptn)
    ean = PeriodicEANReader.read(periodic_ean=SimpleDictGraph())[0]
    logger.info("Finished reading input data")

    logger.info("Begin reconstructing timetable data")
    line_dict = reconstruct_lines(line_concept, ptn, timetable_net)
    reconstruct_timetable(line_dict, timetable_net, ean, ptn, period_length, time_units_per_minute)
    logger.info("Finished reconstructing timetable data")

    logger.info("Begin writing output data")
    PeriodicEANWriter.write(ean, write_events=False, write_activities=False, write_timetable=True)
    logger.info("Finished writing output data")