import logging
import sys
import statistics
import weightedstats

from core.model.tariff_types import TariffObjectiveType, TariffWeightType
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.io.config import ConfigReader, ConfigWriter
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.io.tariff import PriceMatrixReader, PriceMatrixWriter
from core.model.impl.mapOD import MapOD
from core.solver.generic_solver_interface import SolverType
from core.io.statistic import StatisticWriter, Statistic
from core.solver.solver_parameters import SolverParameters

from price_matrix_helper import compute_price_matrix_flat
from algorithm import compute_flat_max_abs_weighted

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    taf_type_string = config.getStringValue("taf_objective")
    weight_objective_string = config.getStringValue("taf_weights_objective")

    parameters = SolverParameters(config, "taf_")
    properties_filename = config.getStringValue("filename_tariff_properties_file")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    od = ODReader.read(MapOD(), -1)
    reference_price_matrix = PriceMatrixReader.read(ptn, config.getStringValue("filename_tariff_reference_price_matrix_file"))
    logger.info("Finished reading input data")


    logger.info("Begin computing tariff")

    if weight_objective_string.lower() == "od" or weight_objective_string.lower() == "\"od\"":
        weight_objective = TariffWeightType.OD
    elif weight_objective_string.lower() == "unit" or weight_objective_string.lower() == "\"unit\"":
        weight_objective = TariffWeightType.UNIT
    elif weight_objective_string.lower() == "reference_inverse" or weight_objective_string.lower() == "\"reference_inverse\"":
        weight_objective = TariffWeightType.REFERENCE_INVERSE
    else:
        raise ConfigInvalidValueException("taf_weights_objective")

    if taf_type_string.lower() == "sum_absolute_deviation" or taf_type_string.lower() == "\"sum_absolute_deviation\"":
        taf_opt_type = TariffObjectiveType.SUM_ABSOLUTE_DEVIATION
    elif taf_type_string.lower() == "max_absolute_deviation" or taf_type_string.lower() == "\"max_absolute_deviation\"":
        taf_opt_type = TariffObjectiveType.MAX_ABSOLUTE_DEVIATION
    else:
        raise ConfigInvalidValueException("taf_objective")

    reference_prices_weights = []
    for origin in ptn.getNodes():
        for destination in ptn.getNodes():
            if origin.getId() != destination.getId():
                reference_prices_weights.append((reference_price_matrix.getValue(origin.getId(), destination.getId()), od.getValue(origin.getId(), destination.getId())))

    reference_prices = [ref for (ref, od) in reference_prices_weights]

    if weight_objective == TariffWeightType.OD:
        weights = [od for (ref, od) in reference_prices_weights]
    elif weight_objective == TariffWeightType.REFERENCE_INVERSE:
        if reference_price_matrix.hasNonDiagonalZeros():
            logger.error("There are zero values in the reference-price-matrix, cannot use reference_inverse weights!")
            raise ZeroDivisionError
        weights = [1/ref for (ref, od) in reference_prices_weights]

#### UNIT WEIGHTS #################################################################################
    if weight_objective == TariffWeightType.UNIT:
        if taf_opt_type == TariffObjectiveType.MAX_ABSOLUTE_DEVIATION:
            p_max = max(reference_prices)
            p_min = min(reference_prices)
            price = (p_max - p_min)/2
        elif taf_opt_type == TariffObjectiveType.SUM_ABSOLUTE_DEVIATION:
            price = statistics.median(reference_prices)

#### WEIGHTS OD or REFERENCE-INVERSE ##############################################################
    elif weight_objective == TariffWeightType.OD or weight_objective == TariffWeightType.REFERENCE_INVERSE:
        if taf_opt_type == TariffObjectiveType.MAX_ABSOLUTE_DEVIATION:
            price = compute_flat_max_abs_weighted(reference_prices, weights, parameters)

        elif taf_opt_type == TariffObjectiveType.SUM_ABSOLUTE_DEVIATION:
            price = weightedstats.weighted_median(reference_prices, weights)

    logger.info(f"flat price: {price}")

    price_matrix = compute_price_matrix_flat(ptn, price)

    logger.info("Finished computing tariff")


    logger.info("Begin writing output data")
    PriceMatrixWriter.write(price_matrix)

    #write price to State Config
    state_str = config.getStringValue("filename_state_config")
    to_append = {"taf_flat_price": price}
    ConfigWriter.write(to_append, state_str, config)

    # write tariff properties to statistic file
    properties = Statistic()
    properties.setValue("taf_model", "FLAT")
    properties.setValue("no_elongation", True)
    properties.setValue("no_stopover", True)
    StatisticWriter.write(properties, properties_filename, config, False)

    logger.info("Finished writing output data")
