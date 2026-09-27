import sys
import logging

from core.io.config import ConfigReader
from core.io.csv import CsvWriter
from core.io.infrastructure import InfrastructureWriter
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.infrastructure import WalkingEdge, InfrastructureEdge
from core.util.config import Config
from common.io.net import NetReader
from read_infrastructure.util.infrastructure import read_walking_edges, read_infrastructure_edges
from common.util.nodes import extract_nodes

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    logger.info("Begin reading config")
    ConfigReader.read(sys.argv[1])
    net_file_name = Config.getStringValueStatic("filename_visum_infrastructure_file")
    walking_file_name = Config.getStringValueStatic("filename_visum_walk_file")
    forbidden_edge_file = Config.getStringValueStatic("filename_forbidden_infrastructure_edges_file")
    undirected = Config.getBooleanValueStatic("ptn_is_undirected")
    time_units_per_minute = Config.getIntegerValueStatic("time_units_per_minute")
    conversion_length = Config.getIntegerValueStatic("gen_conversion_length")
    walking_is_directed = Config.getBooleanValueStatic("sl_walking_is_directed")
    syscode = Config.getStringValueStatic("visum_tsyscode")
    read_forbidden_edges = Config.getStringValueStatic("visum_read_forbidden_edges")
    logger.info("Finished reading config")

    logger.info("Begin reading input files")
    net = NetReader.parse_file(net_file_name)
    walking = NetReader.parse_file(walking_file_name)
    logger.info("Finished reading input files")

    logger.info("Begin creating infrastructure from VISUM file")
    node_dict = extract_nodes(net)
    infrastructure_network = SimpleDictGraph()
    walking_network = SimpleDictGraph()
    for node in node_dict.values():
        infrastructure_network.addNode(node)
        walking_network.addNode(node)
    logger.debug(f"Found {len(infrastructure_network.getNodes())} nodes")
    forbidden_edges = read_infrastructure_edges(net, infrastructure_network, walking_network, node_dict, undirected, time_units_per_minute,
                              conversion_length, syscode, walking_is_directed, read_forbidden_edges)
    logger.debug(f"Found {len(infrastructure_network.getEdges())} infrastructure edges")
    walking_edges = read_walking_edges(walking, walking_network, node_dict, walking_is_directed, time_units_per_minute)
    logger.debug(f"Found {len(walking_network.getEdges())} walking edges")
    logger.info("Finished creating PTN")

    logger.info("Begin writing output files")
    InfrastructureWriter.write(infrastructure_network, write_walking_edges=False)
    CsvWriter.writeListStatic(Config.getStringValueStatic("filename_walking_edge_file"), walking_edges, WalkingEdge.toCsvStrings, header=Config.getStringValueStatic("walking_edge_header"))
    if read_forbidden_edges:
        logger.debug(f"Read {len(forbidden_edges)} forbidden edges")
        CsvWriter.writeListStatic(forbidden_edge_file,
                                  forbidden_edges,
                                  lambda l: l.toCsvStrings(),
                                  InfrastructureEdge.getId,
                                  "edge-id"
                                  )
    logger.info("Finished writing output files")
    exit(0)



