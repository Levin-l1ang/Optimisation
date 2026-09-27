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
    vehicle_capacity = config.getIntegerValue("gen_passengers_per_vehicle")
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
            rpool = RidepoolingPool()
    else:
        rpool = RidepoolingPool()
    logger.info("Finished reading input data")


    logger.info("Begin computing ridepooling pool")
    area = RidepoolingArea(len(rpool.getAreas())+1)
    for edge in ptn.getEdges():
        area.addLink(edge)
    if compute_distribution:
        area.computeVehicleFrequenciesByEulerCircle(period_length, vehicle_capacity, use_ptn_load)
    rpool.addArea(area)
    rpool.setCost(cost)

    logger.info("Finished computing ridepooling pool")

    logger.info("Begin writing output data")
    if compute_distribution:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=True, write_stretch_factors=write_stretch)
    else:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=False, write_stretch_factors=write_stretch)
    logger.info("Finished writing output data")