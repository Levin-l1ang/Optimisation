import logging
import sys

from common.io.net import NetReader
from common.util.nodes import extract_nodes
from core.io.config import ConfigReader
from core.io.od import ODWriter
from core.util.config import Config
from read_demand.util.node_demand import read_demand

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    logging.info("Begin reading config")
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_infrastructure_file")
    od_file_name = Config.getStringValueStatic("filename_visum_od_file")
    logging.info("Finished reading config")

    logger.info("Begin reading input files")
    net = NetReader.parse_file(net_file_name)
    od_data = NetReader.parse_file(od_file_name)
    logging.info("Finished reading input files")

    logging.info("Begin creating node demand from VISUM file")
    node_dict = extract_nodes(net)
    od = read_demand(od_data, node_dict)
    logging.info("Finished creating node demand from VISUM file")

    logging.info("Begin writing output files")
    ODWriter.writeNodeOd(od)
    logging.info("Finished writing output files")