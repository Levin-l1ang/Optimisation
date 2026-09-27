import logging
import sys

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.exceptions.exceptions import LinTimException
from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.ridepooling import RidepoolingPoolWriter, RidepoolingPoolReader
from core.io.lines import LineReader, LineWriter
from core.io.infrastructure_network import InfrastructureNetworkReader
from core.solver.solver_parameters import SolverParameters

from algorithm import solve_rideconcept_alpha, solve_rideconcept_beta

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    parameters = SolverParameters(config, "mm_lc_rc_")
    rc_model = config.getStringValue("mm_lc_rc_model")
    period = config.getIntegerValue("period_length")

    modalities = config.getStringListValue("modalities_all")
    fixed_concepts = config.getStringListValue("fixed_concept_modalities")
    modality_category = {}
    vehicle_capacity = {}
    infra_capacity_weight = {}
    for modality in modalities:
        modality_category[modality] = config.getStringValue("modality_category", modality=modality)
        vehicle_capacity[modality] = config.getIntegerValue("gen_passengers_per_vehicle", modality=modality)
        infra_capacity_weight[modality] = config.getDoubleValue("gen_infrastructure_capacity_weight", modality=modality)

    read_stretch = {}
    rpool_modalities = []
    line_modalities = []
    for modality in modalities:
        if modality_category[modality].lower() == "line-based":
            line_modalities.append(modality)
        elif modality_category[modality].lower() == "ridepooling":
            rpool_modalities.append(modality)
            read_stretch[modality] = config.getBooleanValue("rc_read_stretch_factors", modality=modality)

    logger.info("Finished reading configuration")
    logger.info(f"Found {len(line_modalities)} line-based modalities and {len(rpool_modalities)} ridepooling modalities")


    logger.info("Begin reading input data")
    isn = InfrastructureNetworkReader.read(read_loads=True)

    ptn = {}
    lpool = {}
    rpool = {}
    for modality in line_modalities:
        ptn[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=True, infrastructure_network=isn)
        if modality in fixed_concepts:
            lpool[modality] = LineReader.read(ptn[modality], read_frequencies=True, modality=modality)
        else:
            lpool[modality] = LineReader.read(ptn[modality], read_frequencies=False, modality=modality)
    for modality in rpool_modalities:
        ptn[modality] = PTNReader.read(modality=modality, read_ptn_infrastructure_map=True, infrastructure_network=isn)
        if rc_model.lower() == "cost":
            if modality in fixed_concepts:
                rpool[modality] = RidepoolingPoolReader.read(ptn[modality], read_number_vehicles=True, read_vehicle_frequencies=True, modality=modality)
            else:
                rpool[modality] = RidepoolingPoolReader.read(ptn[modality], read_number_vehicles=False, read_vehicle_frequencies=True, modality=modality)
        elif rc_model.lower() == "cost-beta":
            if modality in fixed_concepts:
                rpool[modality] = RidepoolingPoolReader.read(ptn[modality], read_number_vehicles=True, read_vehicle_frequencies=False, read_stretch_factors=read_stretch[modality], modality=modality)
            else:
                rpool[modality] = RidepoolingPoolReader.read(ptn[modality], read_number_vehicles=False, read_vehicle_frequencies=False, read_stretch_factors=read_stretch[modality], modality=modality)
        else:
            raise ConfigInvalidValueException("rc_model")

    logger.info("Finished reading input data")


    logger.info("Begin computing line concepts and ridepooling concepts")

    if rc_model.lower() == "cost":
        feasible = solve_rideconcept_alpha(isn, lpool, rpool, parameters, vehicle_capacity, infra_capacity_weight, line_modalities, rpool_modalities)
    elif rc_model.lower() == "cost-beta":
        feasible = solve_rideconcept_beta(isn, lpool, rpool, parameters, vehicle_capacity, infra_capacity_weight, line_modalities, rpool_modalities, period)

    if not feasible:
        raise AlgorithmStoppingCriterionException("integrated line concept and ridepooling concept")

    logger.info("Finished computing line concepts and ridepooling concepts")


    logger.info("Begin writing output data")
    for modality in rpool_modalities:
        RidepoolingPoolWriter.write(rpool[modality], write_pool=False, write_ride_concept=True, write_distribution=True, modality=modality)
    for modality in line_modalities:
        LineWriter.write(lpool[modality], write_pool=False, write_costs=False, write_line_concept=True, modality=modality)
    logger.info("Finished writing output data")