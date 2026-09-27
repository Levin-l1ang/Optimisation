import logging
import sys

from core.io.config import ConfigReader
from core.io.ptn import PTNWriter, PTNReader
from common.io.net import NetReader
from core.util.config import Config
from read_ptn.util.ptn import create_links

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    logging.info("Begin reading config")
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_additional_edges_file")
    time_units_per_minute = Config.getIntegerValueStatic("time_units_per_minute")
    logging.info("Finished reading config")

    logger.info("Begin reading input files")
    net = NetReader.parse_file(net_file_name)
    current_ptn = PTNReader.read()
    logging.info("Finished reading input files")

    logging.info("Begin creating PTN from VISUM file")
    create_links(net, current_ptn, not current_ptn.isDirected(), time_units_per_minute)
    logging.info("Finished creating PTN")

    logging.info("Begin writing output files")
    PTNWriter.write(current_ptn)
    logging.info("Finished writing output files")
    exit(0)



