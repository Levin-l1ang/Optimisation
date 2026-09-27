import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputFileException, InputUnsupportedModalityCategoryException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.model.ridepooling import RidepoolingPool, RidepoolingArea
from core.io.ridepooling import RidepoolingPoolWriter, RidepoolingPoolReader
from core.io.infrastructure_network import InfrastructureNetworkReader

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
    write_stretch = config.getBooleanValue("rpool_write_stretch")
    use_ptn_load= config.getBooleanValue("rpool_use_ptn_load")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    if not use_ptn_load:
        isn = InfrastructureNetworkReader.read(read_loads=True)
        ptn = PTNReader.read(read_loads=False, read_ptn_infrastructure_map=True, infrastructure_network=isn)
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
    #
    # For each node in the PTN, define a ridepooling area that contains all edges adjacent to the node.
    # To use this, select "node_based" for the config parameter "rpool_model".
    #

    if max_edges == -1:
        max_edges = len(ptn.getEdges())

    area_id = rpool.getNextAreaId()

    for node in ptn.getNodes():
        area = RidepoolingArea(area_id, edges=[])
        for edge in ptn.getEdges():
            left = edge.getLeftNode()
            right = edge.getRightNode()
            if left == node or right == node:
                area.addLink(edge)
        if (len(area.getEdges())>=min_edges and len(area.getEdges())<=max_edges):
            if compute_distribution:
                area.computeVehicleFrequenciesByEulerCircle(period_length, vehicle_capacity, use_ptn_load)
            rpool.addArea(area)
            area_id = rpool.getNextAreaId()

    logger.info("Finished computing ridepooling pool")


    logger.info("Begin writing output data")
    if compute_distribution:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=True, write_stretch_factors=write_stretch)
    else:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=False, write_stretch_factors=write_stretch)
    logger.info("Finished writing output data")