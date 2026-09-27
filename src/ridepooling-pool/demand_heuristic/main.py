import logging
import sys
from typing import List

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputFileException, InputUnsupportedModalityCategoryException
from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.ridepooling import RidepoolingPoolWriter, RidepoolingPoolReader
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.model.ridepooling import RidepoolingPool, RidepoolingArea
from core.model.ptn import Stop, Link
from core.model.graph import Graph
from core.model.infrastructure_network import InfraLink

logger = logging.getLogger(__name__)

def explore_edges(edges: List[InfraLink], area: RidepoolingArea, factor: float, train_capacity: int, isn: Graph[Stop, Link], max_edges: int, edge_to_infra: dict[InfraLink, Link]):
        edges_to_explore = set()
        for edge in edges:
            if len(area.getEdges()) >= max_edges:
                return area
            if edge.getLoad() <= factor*train_capacity and not area.includesEdge(edge_to_infra[edge]):
                area.addLink(edge_to_infra[edge])
                for e in isn.getIncidentEdges(edge.getLeftNode()) + isn.getIncidentEdges(edge.getRightNode()):
                    if edge_to_infra[e] not in area.getEdges():
                        edges_to_explore.add(e)
        if edges_to_explore != set():
            explore_edges(list(edges_to_explore), area, factor, train_capacity, isn, max_edges, edge_to_infra)
        return area


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
    write_stretch = config.getBooleanValue("rpool_write_stretch")
    line_modality = config.getStringValue("rpool_respected_line_modality")
    line_modality_category = config.getStringValue("modality_category", modality=line_modality)
    if line_modality_category.lower() != "line-based":
        logger.warning(f"The modality specified in rpool_respected_line_modality should be of modality category line-based but is of category {line_modality_category}!")
    train_capacity = config.getIntegerValue("gen_passengers_per_vehicle", modality=line_modality)
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
    # Algorithm only works correctly, if no two PTN edges (both of the ridepooling modality) share an infrastructure link and all infrastructure links have a corresponding PTN-edge using it
    edge_to_infra = {}
    for edge in ptn.getEdges():
        for link in edge.getUnderlyingInfrastructure().getEdges():
            if link in edge_to_infra:
                logger.error("This model can only be used if each infrastructure link is used by exactly one PTN-edge!")
                raise AlgorithmStoppingCriterionException("tree-based ridepooling pool generation")
            edge_to_infra[link] = edge

    area_id = rpool.getNextAreaId()

    for edge in isn.getEdges():
        if edge.getLoad() <= factor*train_capacity:
            area = RidepoolingArea(area_id, edges=[edge_to_infra[edge]])
            area = explore_edges(isn.getIncidentEdges(edge.getLeftNode()) + isn.getIncidentEdges(edge.getRightNode()), area, factor, train_capacity, isn, max_edges, edge_to_infra)
            if len(area.getEdges())>=min_edges and all(not area.compareEdges(other) for other in rpool.getAreas()):
                if compute_distribution:
                    area.computeVehicleFrequenciesByEulerCircle(period_length, vehicle_capacity, use_ptn_load=False)
                rpool.addArea(area)
                area_id = rpool.getNextAreaId()

    logger.info("Finished computing ridepooling pool")


    logger.info("Begin writing output data")
    if compute_distribution:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=True, write_stretch_factors=write_stretch)
    else:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=False, write_stretch_factors=write_stretch)
    logger.info("Finished writing output data")