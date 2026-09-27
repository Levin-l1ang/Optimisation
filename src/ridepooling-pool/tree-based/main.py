import logging
import sys
import networkx as nx

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputFileException, InputUnsupportedModalityCategoryException
from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.model.ridepooling import RidepoolingPool, RidepoolingArea
from core.io.ridepooling import RidepoolingPoolWriter, RidepoolingPoolReader
from core.util.networkx import convert_graph_to_networkx

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    modality_category = config.getStringValue("modality_category")
    if modality_category.lower() != "ridepooling":
        logger.error("Can only create a ridepooling pool, if modality is of modality category ridepooling!")
        raise InputUnsupportedModalityCategoryException(modality_category)

    cost = config.getDoubleValue("rpool_costs_fixed")
    period_length = config.getIntegerValue("period_length")
    min_edges = config.getIntegerValue("rpool_min_edges")
    max_edges = config.getIntegerValue("rpool_max_edges")
    vehicle_capacity = config.getIntegerValue("gen_passengers_per_vehicle")
    factor = config.getDoubleValue("rpool_load_factor")
    compute_distribution = config.getBooleanValue("rpool_compute_vehicle_frequencies")
    extend_rpool = config.getBooleanValue("rpool_extend_existing_pool")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    isn = InfrastructureNetworkReader.read(read_loads=True)
    ptn = PTNReader.read(read_loads=False, read_ptn_infrastructure_map=True, infrastructure_network=isn)
    if max_edges==-1:
        max_edges=len(ptn.getEdges())

    if extend_rpool:
        try:
            rpool = RidepoolingPoolReader.read(ptn, read_number_vehicles=False, read_vehicle_frequencies=True)
        except InputFileException:
            logger.error("Impossible to extend a ridepooling pool without an existing one! Program assumes rpool_extend_existing_pool to false.")
            rpool = RidepoolingPool(cost)
    else:
        rpool = RidepoolingPool(cost)
    logger.info("Finished reading input data")


    logger.info("Begin computing ridepooling pool")

    # prepare map from infrastructure link to PTN edge
    # Algorithm only works correctly, if no two PTN edges (both of the ridepooling modality) share an infrastructure link and all infrastructure links have a corresnponding PTN-edge using it
    edge_to_infra = {}
    for edge in ptn.getEdges():
        for link in edge.getUnderlyingInfrastructure().getEdges():
            if link in edge_to_infra:
                logger.error("This model can only be used if each infrastructure link is used by exactly one PTN-edge!")
                raise AlgorithmStoppingCriterionException("tree-based ridepooling pool generation")
            edge_to_infra[link] = edge

    area_id = rpool.getNextAreaId()

    G = convert_graph_to_networkx(isn, multi_graph=True, weight_function=lambda e: e.getLoad())
    T = nx.maximum_spanning_tree(G)

    tree_links = [link for link in isn.getEdges() if T.has_edge(link.getLeftNode().getId(), link.getRightNode().getId()) or T.has_edge(link.getRightNode().getId(), link.getLeftNode().getId())]

    for node in isn.getNodes():
        # iterate over all leafe nodes of max spanning tree
        if T.degree(node.getId()) == 1:
            area = RidepoolingArea(area_id)
            added_nodes = []
            for link in isn.getIncidentEdges(node):
                # add all incident edges, that are not in MST
                if not T.has_edge(link.getLeftNode().getId(), link.getRightNode().getId()):
                    area.addLink(edge_to_infra[link])
                    if edge.getLeftNode().getId() == node.getId():
                        added_nodes.append(edge.getRightNode())
                    elif edge.getRightNode().getId() == node.getId():
                        added_nodes.append(edge.getLeftNode())
                    else:
                        added_nodes.append(edge.getLeftNode())
                        added_nodes.append(edge.getRightNode())
            for stop1 in added_nodes:
                for stop2 in added_nodes:
                    # add all PTN edges, that connect two newly added stops, but the underlying infrastructure of the edge is not completely part of the MST
                    new_edge = ptn.get_edge_by_nodes(stop1, stop2)
                    if new_edge and not all(links in tree_links for links in new_edge.getUnderlyingInfrastructure().getEdges()):
                        area.addLink(new_edge)
            if len(area.getEdges())>=min_edges and len(area.getEdges())<=max_edges:
                if compute_distribution:
                    area.computeVehicleFrequenciesByEulerCircle(period_length, vehicle_capacity, use_ptn_load=False)
                rpool.addArea(area)
                area_id = rpool.getNextAreaId()

    logger.info("Finished computing ridepooling pool")


    logger.info("Begin writing output data")
    if compute_distribution:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=True)
    else:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=False)
    logger.info("Finished writing output data")