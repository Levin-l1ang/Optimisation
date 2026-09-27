import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.input_exceptions import InputUnsupportedModalityCategoryException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
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
        logger.error("Can only compute ridepooling vehicle frequencies, if modality is of modality category ridepooling!")
        raise InputUnsupportedModalityCategoryException(modality_category)
    capacity_ridepooling = config.getIntegerValue("gen_passengers_per_vehicle")
    period_length = config.getDoubleValue("period_length")
    use_ptn_load= config.getBooleanValue("rpool_use_ptn_load")
    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    if not use_ptn_load:
        isn = InfrastructureNetworkReader.read(read_loads=True)
        ptn = PTNReader.read(read_loads=True, read_ptn_infrastructure_map=True, infrastructure_network=isn)
    else:
        ptn = PTNReader.read(read_loads=True)
    rpool = RidepoolingPoolReader.read(ptn, read_number_vehicles=False, read_vehicle_frequencies=False, ridepool_file_name=config.getStringValue("filename_rpool_file"))
    logger.info("Finished reading input data")


    logger.info("Begin computing distribution")
    rpool.computeVehicleFrequenciesByEulerCircle(period_length,capacity_ridepooling, use_ptn_load)
    logger.info("Finished computing distribution")


    logger.info("Begin writing output")
    RidepoolingPoolWriter.write(rpool, write_pool=True, write_distribution=True, write_ride_concept=False)
    logger.info("Finished writing output")