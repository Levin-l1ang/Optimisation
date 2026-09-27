import logging
import sys

from common.io.net import NetReader
from common.util.constants import CONFIG_SECTION_HEADER, CONFIG_SYSTEM_FREQUENCY_HEADER, CONFIG_MIN_WAIT_TIME_HEADER, \
    CONFIG_PERIOD_LENGTH_HEADER, CONFIG_POST_TURNOVER_HEADER, CONFIG_PRE_TURNOVER_HEADER, \
    CONFIG_TIME_UNITS_PER_MINUTE_HEADER, CONFIG_TRANSFER_PENALTY_HEADER, CONFIG_TSYS_FOR_ADAPTING_HEADER, \
    CONFIG_WALK_TIME_UTILITY_HEADER, CONFIG_DATASET_NAME_HEADER, CONFIG_MIN_CHANGE_TIME_HEADER, \
    VEHICLE_UNITS_SECTION_HEADER, VEHICLE_UNITS_SHORT_NAME_HEADER, VEHICLE_UNITS_CAPACITY_HEADER, \
    VEHICLE_UNITS_SERVICE_TIME_COST_HEADER, VEHICLE_UNITS_SERVICE_LENGTH_COST_HEADER, \
    VEHICLE_UNITS_EMPTY_TIME_COST_HEADER, VEHICLE_UNITS_EMPTY_LENGTH_COST_HEADER, VEHICLE_UNITS_VEHICLE_COST_HEADER
from core.exceptions.exceptions import LinTimException
from core.io.config import ConfigReader
from core.util.config import Config

if __name__ == '__main__':
    logger = logging.getLogger(__name__)
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one arguments, the name of the config to read")
        exit(1)
    logger.info("Begin reading configuration")
    ConfigReader.read(sys.argv[1])
    visum_config_file_name = Config.getStringValueStatic("filename_visum_config_file")
    vehicle_attributes_file_name = Config.getStringValueStatic("filename_visum_vehicle_attributes_file")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input files")
    visum_config = NetReader.parse_file(visum_config_file_name)
    vehicle_attributes = NetReader.parse_file(vehicle_attributes_file_name)
    logger.info("Finished reading input files")

    logger.info("Building up LinTim config from VISUM data")
    # First, extract the dataset specific information
    config_section = visum_config.get_section(CONFIG_SECTION_HEADER)
    row = config_section.get_row(0)
    system_frequency = config_section.get_entry_from_row(row, CONFIG_SYSTEM_FREQUENCY_HEADER)
    min_change_time = config_section.get_entry_from_row(row, CONFIG_MIN_CHANGE_TIME_HEADER)
    min_wait_time = config_section.get_entry_from_row(row, CONFIG_MIN_WAIT_TIME_HEADER)
    period_length = config_section.get_entry_from_row(row, CONFIG_PERIOD_LENGTH_HEADER)
    min_turnover_time = int(config_section.get_entry_from_row(row, CONFIG_POST_TURNOVER_HEADER)) \
                        + int(config_section.get_entry_from_row(row, CONFIG_PRE_TURNOVER_HEADER))
    time_units_per_minute = config_section.get_entry_from_row(row, CONFIG_TIME_UNITS_PER_MINUTE_HEADER)
    change_penalty = config_section.get_entry_from_row(row, CONFIG_TRANSFER_PENALTY_HEADER)
    sys_for_adapting = config_section.get_entry_from_row(row, CONFIG_TSYS_FOR_ADAPTING_HEADER)
    walk_time_utility = config_section.get_entry_from_row(row, CONFIG_WALK_TIME_UTILITY_HEADER)
    scenario_name = config_section.get_entry_from_row(row, CONFIG_DATASET_NAME_HEADER)
    # Now, extract the vehicle specific information from the attributes
    vehicle_section = vehicle_attributes.get_section(VEHICLE_UNITS_SECTION_HEADER)
    tsys_index = -1
    for index, row in enumerate(vehicle_section.get_rows()):
        if vehicle_section.get_entry_from_row(row, VEHICLE_UNITS_SHORT_NAME_HEADER) == sys_for_adapting:
            tsys_index = index
    if tsys_index == -1:
        raise LinTimException(f"The transportation system to adapt ({sys_for_adapting}) "
                              f"could not be found in the vehicle attributes file {vehicle_attributes_file_name}")
    tsys_row = vehicle_section.get_row(tsys_index)
    capacity = vehicle_section.get_entry_from_row(tsys_row, VEHICLE_UNITS_CAPACITY_HEADER)
    service_time_cost = vehicle_section.get_entry_from_row(tsys_row, VEHICLE_UNITS_SERVICE_TIME_COST_HEADER)
    service_length_cost = vehicle_section.get_entry_from_row(tsys_row, VEHICLE_UNITS_SERVICE_LENGTH_COST_HEADER)
    empty_time_cost = vehicle_section.get_entry_from_row(tsys_row, VEHICLE_UNITS_EMPTY_TIME_COST_HEADER)
    empty_length_cost = vehicle_section.get_entry_from_row(tsys_row, VEHICLE_UNITS_EMPTY_LENGTH_COST_HEADER)
    vehicle_cost = vehicle_section.get_entry_from_row(tsys_row, VEHICLE_UNITS_VEHICLE_COST_HEADER)
    # Now replace the contents of the config
    index_to_insert = -6 # Default, above the last section of the config
    with open(sys.argv[1]) as lintim_config_file:
        lines = lintim_config_file.readlines()
    if not lines:
        raise LinTimException(f"Could not read config file {sys.argv[1]}")
    for index, line in enumerate(lines):
        if ";" in line:
            config_key = line.split(";")[0]
            if config_key == "ptn_name":
                lines[index] = f"ptn_name; {scenario_name}\n"
            elif config_key == "period_length":
                lines[index] = f"period_length; {period_length}\n"
            elif config_key == "time_units_per_minute":
                lines[index] = f"time_units_per_minute; {time_units_per_minute}\n"
            elif config_key == "ean_change_penalty":
                lines[index] = f"ean_change_penalty; {change_penalty}\n"
            elif config_key == "ean_default_minimal_waiting_time":
                lines[index] = f"ean_default_minimal_waiting_time; {min_wait_time}\n"
            elif config_key == "ean_default_maximal_waiting_time":
                lines[index] = f"ean_default_maximal_waiting_time; {int(min_wait_time) * 3}\n"
            elif config_key == "ean_default_minimal_change_time":
                lines[index] = f"ean_default_minimal_change_time; {min_change_time}\n"
            elif config_key == "ean_default_maximal_change_time":
                lines[index] = f"ean_default_maximal_change_time; {int(period_length) + int(min_change_time) - 1}\n"
            elif config_key == "ean_default_maximal_change_time":
                lines[index] = f"time_units_per_minute; {time_units_per_minute}\n"
            elif config_key == "gen_passengers_per_vehicle":
                lines[index] = f"gen_passengers_per_vehicle; {capacity}\n"
        elif line == "# === State / Experiments / Automatization ==================================":
            # If this section exists, we want to insert the new values there
            index_to_insert = index - 2
    # Now insert the values, that are not currently present
    lines.insert(index_to_insert, f"lc_common_frequency_divisor; {system_frequency}\n")
    lines.insert(index_to_insert, f"vs_eval_cost_factor_empty_trips_length; {empty_length_cost}\n")
    lines.insert(index_to_insert, f"vs_eval_cost_factor_empty_trips_duration; {empty_time_cost}\n")
    lines.insert(index_to_insert, f"vs_eval_cost_factor_full_trips_length; {service_length_cost}\n")
    lines.insert(index_to_insert, f"vs_eval_cost_factor_full_trips_duration; {service_time_cost}\n")
    lines.insert(index_to_insert, f"vs_vehicle_costs; {vehicle_cost}\n")
    lines.insert(index_to_insert, f"visum_tsyscode; {sys_for_adapting}\n")
    lines.insert(index_to_insert, f"gen_walking_utility; {walk_time_utility}\n")
    lines.insert(index_to_insert, f"vs_turn_over_time; {min_turnover_time}\n")
    logger.info("Finished building LinTim config from VISUM data")

    logger.info("Begin writing output files")
    with open(sys.argv[1], "w") as config_file:
        config_file.writelines(lines)
    logger.info("Finished writing output files")
