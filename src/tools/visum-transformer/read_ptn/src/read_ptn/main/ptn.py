import logging
import sys

from core.io.config import ConfigReader
from core.io.infrastructure import InfrastructureReader
from core.io.ptn import PTNWriter
from core.util.config import Config
from common.io.net import NetReader
from read_ptn.io.forbidden_links import write_forbidden_links
from read_ptn.util.ptn import transform_ptn

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    logger.info("Begin reading config")
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_infrastructure_file")
    undirected = Config.getBooleanValueStatic("ptn_is_undirected")
    syscode = Config.getStringValueStatic("visum_tsyscode")
    respect_nodes = Config.getBooleanValueStatic("visum_respect_nodes_when_reading_ptn")
    time_units_per_minute = Config.getIntegerValueStatic("time_units_per_minute")
    logger.info("Finished reading config")

    logger.info("Begin reading input files")
    net = NetReader.parse_file(net_file_name)
    infrastructure = None
    if respect_nodes:
        infrastructure = InfrastructureReader.read(read_walking_edges=False)[0]
    logger.info("Finished reading input files")

    logger.info("Begin creating PTN from VISUM file")
    ptn, forbidden_links = transform_ptn(net, undirected, syscode, infrastructure, time_units_per_minute)
    logger.info("Finished creating PTN")

    logger.info("Begin writing output files")
    PTNWriter.write(ptn)
    if forbidden_links:
        write_forbidden_links(forbidden_links, Config.getStringValueStatic("filename_forbidden_links_file"), "#link-id")
    logger.info("Finished writing output files")
    exit(0)



