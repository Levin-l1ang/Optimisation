import logging
import sys

from core.io.config import ConfigReader
from ean_data import Ean
from lin_veh_to_tim_generic import LinVehToTimGenericModel
from lin_veh_to_tim_gurobi import solve
from lin_veh_to_tim_helper import LinVehToTimParameters
from line_data import LinePool
from ptn_data import Ptn
from read_csv import read_ptn, read_line_pool, read_ean, read_vehicle_schedule
from vehicle_schedule import VehicleSchedule
from vs_helper import TurnaroundData

logger = logging.getLogger(__name__)


if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        logger.fatal("No config file name given to read!")
        exit(1)
    config = ConfigReader.read(sys.argv[1])
    parameters = LinVehToTimParameters(config)
    logger.info("Finished reading configuration")
    logger.info("Begin reading input data")
    # Read PTN
    ptn = Ptn()
    read_ptn(ptn, parameters)
    # Read line pool
    line_pool = LinePool()
    read_line_pool(parameters.line_concept_file_name, line_pool, ptn, parameters.directed, system_frequency=1,
                   read_line_concept=True)
    # Read EAN
    ean = Ean()
    read_ean(parameters, ptn, line_pool, ean, parameters.set_starting_timetable)
    # Read Vehicle Schedule
    vehicle_schedule = VehicleSchedule(line_pool)
    read_vehicle_schedule(parameters.vehicle_file_name, line_pool, vehicle_schedule, True, parameters.em_earliest_time,
                          parameters.em_latest_time)
    logger.info("Finished reading input data")

    logger.info("Begin execution of integrated timetabling vehicle scheduling model")
    turnaround_data = TurnaroundData(ptn, parameters.vs_depot_index, parameters.vs_turn_over_time)

    # solve(parameters, ean, line_pool, vehicle_schedule, turnaround_data)

    model = LinVehToTimGenericModel(parameters, ean, line_pool, vehicle_schedule, turnaround_data)
    model.create_model()
    model.solve()
    if model.is_feasible:
        model.write_output()

