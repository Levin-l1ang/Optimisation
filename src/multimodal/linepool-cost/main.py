import logging
import sys
import subprocess

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.exceptions import LinTimException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.model.ptn import Stop, Link
from core.model.graph import Graph


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

    line_modalities = []
    for modality in modalities:
        if modality_category[modality].lower() == "line-based":
            line_modalities.append(modality)
    if len(line_modalities) == 0:
        raise LinTimException("No modality of category line-based given!")

    logger.info("Finished reading configuration")
    logger.info(f"Found {len(line_modalities)} line-based modalities")


    logger.info("Begin generating cost files for line pools for all line-based modalities")
    for modality in line_modalities:

        logger.debug(f"Run subrocess make mm-data modality={modality}")
        subprocess.run(['make', 'mm-data', f'modality={modality}'])
        logger.debug(f"Run subrocess make lpool-line-pool-cost for modality {modality}")
        subprocess.run(['make', 'lpool-line-pool-cost'])
        logger.debug(f"Finished generating line pool cost file for modality {modality}")
        subprocess.run(['make', 'mm-exit'])

    logger.info("Finished generating line pools for all line-based modalities")
