import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.exceptions.input_exceptions import InputUnsupportedModalityCategoryException
from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.ridepooling import RidepoolingPoolWriter, RidepoolingPoolReader
from core.solver.solver_parameters import SolverParameters

from algorithm import solve_rideconcept_alpha, solve_rideconcept_beta

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])

    modality_category = config.getStringValue("modality_category")
    if modality_category.lower() != "ridepooling":
        logger.error("Can only compute a ridepooling concept, if modality is of modality category ridepooling!")
        raise InputUnsupportedModalityCategoryException(modality_category)

    parameters = SolverParameters(config, "rc_")
    capacity_ridepool = config.getIntegerValue("gen_passengers_per_vehicle")
    rc_model = config.getStringValue("rc_model")
    read_stretch = config.getBooleanValue("rc_read_stretch_factors")
    period = config.getIntegerValue("period_length")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read(read_loads=True)

    if rc_model.lower() == "cost":
        rpool = RidepoolingPoolReader.read(ptn, read_number_vehicles=False, read_vehicle_frequencies=True)
    elif rc_model.lower() == "cost-beta":
        rpool = RidepoolingPoolReader.read(ptn, read_number_vehicles=False, read_vehicle_frequencies=False, read_stretch_factors=read_stretch)
    else:
        raise ConfigInvalidValueException("rc_model")

    logger.info("Finished reading input data")


    logger.info("Begin computing ridepooling concept")

    if rc_model.lower() == "cost":
        feasible = solve_rideconcept_alpha(ptn, rpool, parameters, capacity_ridepool)
    elif rc_model.lower() == "cost-beta":
        feasible = solve_rideconcept_beta(ptn, rpool, parameters, capacity_ridepool, period)

    if not feasible:
        raise AlgorithmStoppingCriterionException("ridepooling concept")

    logger.info("Finished computing ridepooling concept")


    logger.info("Begin writing output data")
    if rc_model.lower() == "cost":
        RidepoolingPoolWriter.write(rpool, write_pool=False, write_ride_concept=True, write_distribution=True)
    elif rc_model.lower() == "cost-beta":
        RidepoolingPoolWriter.write(rpool, write_pool=False, write_ride_concept=True, write_distribution=True)
    logger.info("Finished writing output data")