import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputFileException, InputUnsupportedModalityCategoryException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.model.ridepooling import RidepoolingPool, RidepoolingArea
from core.io.ridepooling import RidepoolingPoolWriter, RidepoolingPoolReader
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.model.ptn import Link, Stop
from core.model.graph import Graph

logger = logging.getLogger(__name__)


def extend_area(area: RidepoolingArea, edge: Link, rpool: RidepoolingPool, ptn: Graph[Stop, Link], min_edges: int, max_edges: int, period_length: int, vehicle_capacity: int, use_ptn_load: bool):

    new_area = RidepoolingArea(rpool.getNextAreaId(), edges=area.getEdges()+[edge])
    if compute_distribution:
        new_area.computeVehicleFrequenciesByEulerCircle(period_length, vehicle_capacity, use_ptn_load)

    if len(new_area.getEdges()) >= min_edges:
        rpool.addArea(new_area)

    if len(new_area.getEdges()) < max_edges:
        new_incident_edges = [e for e in ptn.getIncidentEdges(edge.getLeftNode())+ptn.getIncidentEdges(edge.getRightNode()) if e not in new_area.getEdges()]

        for e in new_incident_edges:
            extend_area(new_area, e, rpool, ptn, min_edges, max_edges, period_length, vehicle_capacity, use_ptn_load)



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
    vehicle_capacity = config.getIntegerValue("gen_passengers_per_vehicle")
    min_edges = config.getIntegerValue("rpool_min_edges")
    max_edges = config.getIntegerValue("rpool_max_edges")
    compute_distribution = config.getBooleanValue("rpool_compute_vehicle_frequencies")
    extend_rpool = config.getBooleanValue("rpool_extend_existing_pool")
    write_stretch = config.getBooleanValue("rpool_write_stretch")
    use_ptn_load= config.getBooleanValue("rpool_use_ptn_load")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    if not use_ptn_load:
        isn = InfrastructureNetworkReader.read(read_loads=True)
        ptn = PTNReader.read(read_loads=True, read_ptn_infrastructure_map=True, infrastructure_network=isn)
    else:
        ptn = PTNReader.read(read_loads=True)
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
    if max_edges==-1:
        max_edges=len(ptn.getEdges())

    Edges = ptn.getEdges()
    idx = rpool.getNextAreaId()

    for edge in Edges:
        area = RidepoolingArea(idx)
        extend_area(area, edge, rpool, ptn, min_edges, max_edges, period_length, vehicle_capacity, use_ptn_load)
        idx = rpool.getNextAreaId()

    logger.info("Finished computing ridepooling pool")


    logger.info("Begin writing output data")
    if compute_distribution:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=True, write_stretch_factors=write_stretch)
    else:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=False, write_stretch_factors=write_stretch)
    logger.info("Finished writing output data")


