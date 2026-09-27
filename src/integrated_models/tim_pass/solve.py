import od_activator
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from tim_pass_cycle_generic import CycleTimPassGenericModel
from tim_pass_generic import TimPassGenericModel
from read_csv import *
import sys

from tim_pass_helper import TimPassParameters

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    # Get Parameters from config file
    parameters = TimPassParameters(config)
    # Preprocessing does not work if global_n_time_slices is not 1
    if parameters.global_n_time_slices != 1 and parameters.use_preprocessing:
        raise LinTimException("Preprocessing does not work if global_n_time_slices is not set to 1!")
    logger.info("Finished reading configuration")
    logger.info("Begin reading input data")
    ptn = Ptn()
    read_ptn(ptn, parameters)
    od = OD()
    read_od_matrix(parameters.od_file_name, ptn, od, parameters.global_n_time_slices,
                   period_length=parameters.period_length)
    line_pool = LinePool()
    read_line_pool(parameters.line_concept_file_name, line_pool, ptn, parameters.directed, read_line_concept=True,
                   restrict_to_frequency_1=False)
    ean = Ean(ptn, line_pool, od, ean_parameters=parameters, multiple_frequencies=True)
    od_activator.activate_od_pairs(od, parameters, ean)
    logger.debug(f"Number of active od pairs: {len(od.get_active_od_pairs())}")
    n_od_pairs_without_transfer = len([od_pair for od_pair in od.get_all_od_pairs()
                                       if od_pair.get_total_passengers() >0 and not od_pair.transfer_in_shortest_paths])
    logger.debug(f"Number of OD pairs without transfer: {n_od_pairs_without_transfer}")
    logger.info("Finished reading input data")
    logger.info("Begin execution of integrated timetabling passenger routing model")
    if parameters.use_cycle_base_formulation:
        model = CycleTimPassGenericModel(ean, ptn, line_pool, od, parameters)
    else:
        model = TimPassGenericModel(ean, ptn, line_pool, od, parameters)
    model.create_model()
    model.solve()
    logger.info("Finished execution of integrated timetabling passenger routing model")
    logger.info("Begin writing output data")
    if model.is_feasible:
        model.write_output()
    logger.info("Finished writing output data")