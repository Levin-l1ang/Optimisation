import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.model.ridepooling import RidepoolingPool
from core.io.ridepooling import RidepoolingPoolWriter

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    modalities = config.getStringListValue("modalities_all")
    modality_category = {}
    for modality in modalities:
        modality_category[modality] = config.getStringValue("modality_category", modality=modality)
    rpool_modalities = []
    for modality in modalities:
        if modality_category[modality].lower() == "ridepooling":
            rpool_modalities.append(modality)
    logger.info("Finished reading configuration")


    logger.info("Begin writing empty stretch factors file")
    rpool = RidepoolingPool()
    if len(rpool_modalities)>0:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=False, write_stretch_factors=True, write_pool=False, config=config, modality=modality)
    else:
        RidepoolingPoolWriter.write(rpool, write_ride_concept=False, write_distribution=False, write_stretch_factors=True, write_pool=False, config=config)
    logger.info("Finished writing empty stretch factors file")