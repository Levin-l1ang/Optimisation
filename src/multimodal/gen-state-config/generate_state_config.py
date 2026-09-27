"""Generate state config for multimodal data.

Expects the last command line argument to be the path to the used config file.
State-Config must be empty before calling this.
"""
import logging
import re
import sys
from typing import Iterator

from core.io.config import ConfigReader
from core.util.mm_utilities import add_modality_prefix

logger = logging.getLogger(__name__)


def generate_filename_renames(
    modality: str,
    data: dict[str, str],
) -> Iterator[str]:
    """Generate filename renames with a modality prefix.

    :param modality: Modality string to use as a prefix separated by a dot.
    :param data: Config key-value pairs that will be filtered for filenames.
    :return: Iterator for the renames to be written to a file.
    """
    # pattern for filename keys in the config
    key_pattern = re.compile(r'^filename_.+$|^default_.+_file$')
    # do not rename values for these config keys
    protected_keys = [
        'filename_node_file',
        'filename_od_nodes_file',
        'filename_state_config',
        'filename_private_config',
        'filename_infrastructure_link_file',
        'filename_infrastructure_node_file',
        'filename_infrastructure_node_coordinates_file',
        'filename_infrastructure_od_file',
        'filename_infrastructure_load_file'
    ]
    for key, value in data.items():
        if key in protected_keys:
            continue
        if re.match(key_pattern, key):
            new_path = add_modality_prefix(modality, value)
            yield f'{key}; "{new_path}"\n'


def generate_key_renames(modality: str, data: dict[str, str]) -> Iterator[str]:
    """Generate new keys for keys with a modality prefix.

    :param modality: Modality prefix without the dot that will be removed.
    :param data: Config key-value pairs tha will be filtered for prefixed keys.
    :return: Iterator for the renamed key-value pairs to be written to a file.
    """
    # pattern for the config key modality prefix to be removed
    prefix_pattern = re.compile(rf'^{modality}\.')
    for key, value in data.items():
        if re.match(prefix_pattern, key):
            new_key = key.removeprefix(f'{modality}.')
            yield f'{new_key}; {value}\n'


def run():
    """Run the script."""
    logger.info('Starting multimodal state config generation.')
    config = ConfigReader.read(sys.argv[1])
    # Modality may be passed from make by including "modality=<modality>"
    # in the make call. This would override what we have set in the config file.
    if len(sys.argv) == 3:
        modality = sys.argv[2]
    else:
        logger.error("Missing modality parameter for make mm-data, the call should be \"make mm-data modality=<modality>\"")
    logger.info(f'{modality=}')
    filename_state_config: str = config.getStringValue('filename_state_config')

    with open(filename_state_config, 'w+', encoding='UTF-8') as file:
        file.writelines(generate_filename_renames(modality, config.data))
        file.writelines(generate_key_renames(modality, config.data))

    logger.info('Multimodal state config generated.')


if __name__ == '__main__':
    run()
