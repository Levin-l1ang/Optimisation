import od_activator
from core.io.config import ConfigReader
from lin_tim_pass_veh_generic import LinTimPassVehGenericModel
from lin_tim_pass_veh_gurobi import *
from lin_tim_pass_veh_helper import LinTimPassVehParameters
from read_csv import *
import sys

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        logger.fatal("No config file name given to read!")
        exit(1)
    # Config
    config = ConfigReader.read(sys.argv[1])
    parameters = LinTimPassVehParameters(config)
    logger.info("Finished reading configuration")
    logger.info("Begin reading input data")
    # Read PTN
    ptn = Ptn()
    read_ptn(ptn, parameters)

    # Read load
    if parameters.check_lower_frequencies or parameters.check_upper_frequencies:
        read_load(parameters.load_file_name, ptn, parameters.directed)

    # Read line pool
    line_pool = LinePool()
    read_line_pool(parameters.line_pool_file_name, line_pool, ptn, parameters.directed,
                   system_frequency=parameters.system_frequency, read_costs=True,
                   line_cost_file_name=parameters.line_cost_file_name)
    # Read OD-matrix
    od = OD()
    read_od_matrix(parameters.od_file_name, ptn, od, parameters.global_n_time_slices,
                   parameters.period_length)
    logger.info("Finished reading input data")
    logger.info("Begin execution of integrated line planning timetabling model")
    od_activator.activate_od_pairs(od, parameters)
    turnaround_data = TurnaroundData(ptn, parameters.vs_depot_index, parameters.vs_turn_over_time)
    multiple_frequencies = (parameters.system_frequency != 1)
    ean = Ean(ptn, line_pool, od, parameters, multiple_frequencies)
    model = LinTimPassVehGenericModel(ean, ptn, line_pool, od, turnaround_data, parameters)
    model.create_model()
    model.solve()
    logger.info("Finished execution of integrated line planning timetabling model")
    logger.info("Begin writing output data")
    if model.is_feasible:
        model.write_output()
    logger.info("Finished writing output data")




