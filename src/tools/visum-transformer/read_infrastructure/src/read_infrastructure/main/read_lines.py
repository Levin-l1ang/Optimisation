import logging
import sys

from core.io.config import ConfigReader
from core.io.lines import LineWriter
from core.io.ptn import PTNReader
from core.util.config import Config
from common.io.net import NetReader
from read_infrastructure.util.lines import find_lines

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_infrastructure_file")
    undirected = Config.getBooleanValueStatic("ptn_is_undirected")
    if not undirected:
        raise RuntimeError("Line reading is currently not implemented for directed networks!")
    cost_factor_line = Config.getDoubleValueStatic("lpool_costs_fixed")
    cost_factor_edge = Config.getDoubleValueStatic("lpool_costs_edges")
    cost_factor_length = Config.getDoubleValueStatic("lpool_costs_length")
    logging.info("Begin reading input files")
    ptn = PTNReader.read()
    net = NetReader.parse_file(net_file_name)
    logging.info("Finished reading input files")
    logging.info("Begin reading lines from visum file")
    pool = find_lines(net, ptn, cost_factor_line, cost_factor_length, cost_factor_edge)
    logging.info("Finished reading lines from visum file")
    logging.info("Begin writing output files")
    LineWriter.write(pool, write_line_concept=False)
    logging.info("Finished writing output files")
    exit(0)

