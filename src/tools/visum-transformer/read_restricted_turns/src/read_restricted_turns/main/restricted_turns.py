import logging
import sys

from common.io.net import NetReader
from common.util.constants import TURNS_SECTION, TURNS_IS_FORBIDDEN_HEADER, TURNS_FROM_NODE_HEADER, \
    TURNS_VIA_NODE_HEADER, TURNS_TO_NODE_HEADER
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.infrastructure import InfrastructureReader
from core.io.restricted_turns import RestrictedTurnWriter
from core.model.infrastructure import InfrastructureNode

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        raise ConfigNoFileNameGivenException()
    logger.info("Begin reading configuration")
    config = ConfigReader.read(sys.argv[1])
    restricted_turn_file = config.getStringValue("filename_visum_restricted_turns_file")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input files")
    infrastructure = InfrastructureReader.read(read_walking_edges=False)[0]
    restricted_turns_net = NetReader.parse_file(restricted_turn_file)
    logger.info("Finished reading input files")

    logger.info("Begin parsing restricted turns")
    restricted_turns = set()
    section = restricted_turns_net.get_section(TURNS_SECTION)
    for row in section.get_rows():
        if int(section.get_entry_from_row(row, TURNS_IS_FORBIDDEN_HEADER).strip()) == 0:
            continue
        from_node = infrastructure.get_node_by_function(InfrastructureNode.getName, section.get_entry_from_row(row, TURNS_FROM_NODE_HEADER))
        via_node = infrastructure.get_node_by_function(InfrastructureNode.getName, section.get_entry_from_row(row, TURNS_VIA_NODE_HEADER).strip())
        to_node = infrastructure.get_node_by_function(InfrastructureNode.getName, section.get_entry_from_row(row, TURNS_TO_NODE_HEADER).strip())
        try:
            first_link = infrastructure.get_edge_by_nodes(from_node, via_node)
            second_link = infrastructure.get_edge_by_nodes(via_node, to_node)
        except KeyError:
            raise RuntimeError(f"Did not find infrastructure edge between nodes {from_node}, {via_node} and {to_node}")
        restricted_turns.add((first_link.getId(), second_link.getId()))
    logger.info("Finished parsing restricted turns")

    logger.info("Begin writing output files")
    RestrictedTurnWriter.write(restricted_turns, is_infrastructure=True)
    logger.info("Finished writing output files")
