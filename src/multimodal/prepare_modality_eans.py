import os
import sys
import subprocess
import logging

from core.io.config import ConfigReader
from core.util.config import Config


logger = logging.getLogger(__name__)


def prepare_scheduled_modalities(modalities: list[str], config: Config) -> None:
    """
    Prepare all scheduled modalities for EAN joining by running necessary make commands for each modality.
    :param modalities: list of scheduled modalities to prepare
    :param config: Configuration object
    """
    after_config = config.getStringValue("filename_after_config")
    state_config = config.getStringValue("filename_state_config")

    # Create temporary config file
    tmp_config = "basis/Temp-Config.cnf"
    with open(f"{tmp_config}", 'w') as tmp:
        tmp.write("load_generator_write_multimodal_od_split; true\n")

    # tell after config to read temp config
    include_line = f"include_if_exists; \"{tmp_config}\"\n"
    if os.path.exists(after_config):
        with open(after_config, 'a') as f:
            f.write(include_line)
    else:
        with open(after_config, 'w') as f:
            f.write(include_line)

    subprocess.run(['make', 'mm-ptn-regenerate-load']).check_returncode()
    makes = [
        'ptn-regenerate-load',
        'lpool-line-pool',
        'lc-line-concept',
        'ean',
        'tim-timetable',
    ]
    for modality in modalities:
        logger.info(f'Preparing modality "{modality}"')
        subprocess.run(
            [
                'make',
                'mm-data',
                f'modality={modality}',
            ]
        ).check_returncode()
        for make in makes:
            logger.info(f'Calling "make {make}"')
            subprocess.run(['make', make]).check_returncode()

    os.remove(tmp_config) # Remove temporary config
    os.remove(state_config) # Remove state config



def run():
    logger.info('Started to prepare all modalities for EAN joining')
    config = ConfigReader.read(sys.argv[-1])
    scheduled_modalities: list[str] = []
    all_modalities = config.getStringListValue("modalities_all")
    for mode in all_modalities:
        category = config.getStringValue(f"{mode}.modality_category")
        if category == "line-based":
            scheduled_modalities.append(mode)
        elif category == "nonscheduled":
            continue
    logger.info(f'All scheduled modalities: {scheduled_modalities}')
    prepare_scheduled_modalities(scheduled_modalities, config)


if __name__ == '__main__':
    run()
