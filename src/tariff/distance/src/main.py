import logging
import sys

from algorithm import compute_optimal_beeline_distance_tariff, compute_optimal_network_distance_tariff
from core.model.tariff_types import TariffModelType, TariffObjectiveType, TariffWeightType, TariffRoutingType
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.exceptions.data_exceptions import DataRoutingIncompleteException
from core.io.config import ConfigReader, ConfigWriter
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.io.tariff import PriceMatrixReader, PriceMatrixWriter
from core.model.impl.mapOD import MapOD
from core.model.routing import Routing
from core.io.routing import RoutingReader, RoutingWriter
from core.io.statistic import Statistic, StatisticWriter
from core.solver.solver_parameters import SolverParameters

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    parameters = SolverParameters(config, "taf_")

    taf_type_string = config.getStringValue("taf_model")
    if taf_type_string.lower() == "beeline_distance" or taf_type_string.lower() == "\"beeline_distance\"":
        taf_type = TariffModelType.BEELINE_DISTANCE
    elif taf_type_string.lower() == "network_distance" or taf_type_string.lower() == "\"network_distance\"":
        taf_type = TariffModelType.NETWORK_DISTANCE

    taf_objective_string = config.getStringValue("taf_objective")
    weight_objective_string = config.getStringValue("taf_weights_objective")

    if weight_objective_string.lower() == "od" or weight_objective_string.lower() == "\"od\"":
        weight_objective = TariffWeightType.OD
    elif weight_objective_string.lower() == "unit" or weight_objective_string.lower() == "\"unit\"":
        weight_objective = TariffWeightType.UNIT
    elif weight_objective_string.lower() == "reference_inverse" or weight_objective_string.lower() == "\"reference_inverse\"":
        weight_objective = TariffWeightType.REFERENCE_INVERSE
    else:
        raise ConfigInvalidValueException("taf_weights_objective")

    if taf_objective_string.lower() == "sum_absolute_deviation" or taf_objective_string.lower() == "\"sum_absolute_deviation\"":
        taf_obj_type = TariffObjectiveType.SUM_ABSOLUTE_DEVIATION
    elif taf_objective_string.lower() == "max_absolute_deviation" or taf_objective_string.lower() == "\"max_absolute_deviation\"":
        taf_obj_type = TariffObjectiveType.MAX_ABSOLUTE_DEVIATION
    else:
        raise ConfigInvalidValueException("taf_objective")

    reference_matrix_filename = config.getStringValue("filename_tariff_reference_price_matrix_file")
    routing_type_string = config.getStringValue("taf_routing_generation")
    paths_filename = config.getStringValue("filename_routing_ptn_input")
    properties_filename = config.getStringValue("filename_tariff_properties_file")

    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    od = ODReader.read(MapOD(), -1)
    reference_price_matrix = PriceMatrixReader.read(ptn, reference_matrix_filename)

    if taf_type == TariffModelType.NETWORK_DISTANCE:
        if routing_type_string.lower() == "fastest-paths" or routing_type_string.lower() == "\"fastest-paths\"":
            routing_type = TariffRoutingType.FASTEST_PATHS
            routing = Routing(ptn).fillShortestPaths(lambda e: e.getLowerBound())
        elif routing_type_string.lower() == "read-all" or routing_type_string.lower() == "\"read-all\"":
            routing_type = TariffRoutingType.READ_ALL
            routing = RoutingReader.read(ptn, routing_file_name=paths_filename)
            if not routing.isComplete():
                raise DataRoutingIncompleteException
        elif routing_type_string.lower() == "read-partial-fill" or routing_type_string.lower() == "\"read-partial-fill\"":
            routing_type = TariffRoutingType.READ_PARTIAL_FILL
            routing = RoutingReader.read(ptn, routing_file_name=paths_filename).fillShortestPaths(lambda e: e.getLowerBound())
        else:
            raise ConfigInvalidValueException("taf_routing_generation")

    logger.info("Finished reading input data")


    logger.info("Begin computing tariff")

    if weight_objective == TariffWeightType.UNIT:
        weights = [1 for origin in ptn.getNodes() for destination in ptn.getNodes() if origin != destination]
    elif weight_objective == TariffWeightType.REFERENCE_INVERSE:
        if reference_price_matrix.hasNonDiagonalZeros():
            logger.error("There are zero values in the reference-price-matrix, cannot use reference_inverse weights!")
            raise ZeroDivisionError
        weights = [1/reference_price_matrix.getValue(origin.getId(), destination.getId()) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin != destination]
    elif weight_objective == TariffWeightType.OD:
        weights = [od.getValue(origin.getId(), destination.getId()) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin != destination]

    if taf_type == TariffModelType.BEELINE_DISTANCE:
        fix, factor, price_matrix = compute_optimal_beeline_distance_tariff(reference_price_matrix, ptn, weights, taf_obj_type, parameters)
    elif taf_type == TariffModelType.NETWORK_DISTANCE:
        fix, factor, price_matrix = compute_optimal_network_distance_tariff(reference_price_matrix, ptn, routing, weights, taf_obj_type, parameters)
    logger.info("Finished computing tariff")


    logger.info("Begin writing output data")
    PriceMatrixWriter.write(price_matrix)
    if taf_type == TariffModelType.NETWORK_DISTANCE: # for beeline write no routing, since not relevant for this model
        RoutingWriter.write(routing)

    #write fixed and factor costs to State Config
    state_str = config.getStringValue("filename_state_config")
    to_append = {"taf_fixed_costs": fix, "taf_factor_costs": factor}
    ConfigWriter.write(to_append, state_str, config)

    # write tariff properties to statistic file
    properties = Statistic()
    properties.setValue("taf_model", taf_type.name)
    if taf_type == TariffModelType.NETWORK_DISTANCE:
        properties.setValue("no_elongation", True)
    elif taf_type == TariffModelType.BEELINE_DISTANCE:
        properties.setValue("no_elongation", False)

    properties.setValue("no_stopover", True)
    StatisticWriter.write(properties, properties_filename, config, False)

    logger.info("Finished writing output data")
