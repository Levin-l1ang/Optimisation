from core.io.config import ConfigReader
from read_csv import *
import sys

from tim_veh_generic import TimVehGenericModel
from tim_veh_gurobi import TimVehGurobiModel
from tim_veh_helper import TimVehParameters
from vs_helper import TurnaroundData

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        logger.fatal("No config file name given to read!")
        exit(1)
    # Config
    config = ConfigReader.read(sys.argv[1])
    parameters = TimVehParameters(config)
    logger.info("Finished reading configuration")
    logger.info("Begin reading input data")
    # Read PTN
    ptn = Ptn()
    read_ptn(ptn, parameters)
    # Read line pool
    line_pool = LinePool()
    read_line_pool(parameters.line_concept_file_name, line_pool, ptn, parameters.directed,
                   restrict_to_frequency_1=False, read_line_concept=True)
    # Read EAN
    ean = Ean()
    read_ean(parameters, ptn, line_pool, ean, parameters.set_starting_timetable)
    logger.info("Finished reading input data")

    logger.info("Begin execution of integrated timetabling vehicle scheduling model")
    turnaround_data = TurnaroundData(ptn, parameters.vs_depot_index, parameters.vs_turn_over_time)
    model = TimVehGenericModel(ean, line_pool, turnaround_data, parameters)
    model.create_model()
    model.solve()
    logger.info("Finished execution of integrated timetabling vehicle scheduling model")
    logger.info("Begin writing output data")
    if model.is_feasible:
        model.write_output()
    logger.info("Finished writing output data")