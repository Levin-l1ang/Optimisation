import logging
import sys

from common.io.net import NetReader
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.util.config import Config
from read_demand.io.lintim_demand import write_demand_zones, write_demand_paths, write_demand
from read_demand.util.zone_demand import get_demand
from read_demand.util.infrastructure import get_zones, get_zone_paths

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    if len(sys.argv) != 2:
        logger.fatal("Program takes exactly one argument, the name of the config to read")
        exit(1)
    ConfigReader.read(sys.argv[1])
    infrastructure_file_name = Config.getStringValueStatic("filename_visum_infrastructure_file")
    visum_demand_file_name = Config.getStringValueStatic("filename_visum_demand_file")
    demand_od_file_name = Config.getStringValueStatic("filename_od_demand_file")
    demand_paths_file_name = Config.getStringValueStatic("filename_demand_paths_file")
    demand_zones_file_name = Config.getStringValueStatic("filename_demand_zones_file")
    demand_od_header = Config.getStringValueStatic("od_demand_header")
    demand_paths_header = Config.getStringValueStatic("od_paths_header")
    demand_zones_header = Config.getStringValueStatic("od_zones_header")
    time_units_per_minute = Config.getIntegerValueStatic("time_units_per_minute")
    logging.info("Begin reading input files")
    ptn = PTNReader.read()
    net = NetReader.parse_file(infrastructure_file_name)
    demand_data = NetReader.parse_file(visum_demand_file_name)
    logging.info("Finished reading input files")
    logging.info("Begin computing lintim demand files")
    zones = get_zones(net)
    paths = get_zone_paths(net, zones, ptn, time_units_per_minute)
    demand = get_demand(demand_data, zones)
    logging.info("Finished computing lintim demand files")
    logging.info("Begin writing output files")
    write_demand_zones(list(zones.values()), demand_zones_file_name, demand_zones_header)
    write_demand_paths(paths, demand_paths_file_name, demand_paths_header)
    write_demand(demand, demand_od_file_name, demand_od_header)
    logging.info("Finished writing output files")
    exit(0)