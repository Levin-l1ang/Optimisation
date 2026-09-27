import logging
import sys

import price_matrix_helper
from core.model.tariff import PriceMatrix
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.exceptions.data_exceptions import DataRoutingIncompleteException
from core.io.config import ConfigReader
from core.io.ptn import PTNReader
from core.io.tariff import PriceMatrixWriter, ZonePriceReader, ZoneReader
from core.io.routing import RoutingReader, RoutingWriter
from core.model.routing import Routing
from core.model.tariff_types import TariffZoneCountingType, TariffRoutingType
from core.io.statistic import Statistic, StatisticWriter
import zone_tariff

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    command_flag = sys.argv[2]
    make_reference = False
    if command_flag == "true": # call came by make taf-tariff-reference-price-matrix
        make_reference = True

    model = config.getStringValue("taf_model")
    filename_price_matrix = config.getStringValue("filename_tariff_price_matrix_file")
    reference_matrix_filename = config.getStringValue("filename_tariff_reference_price_matrix_file")
    routing_type_string = config.getStringValue("taf_routing_generation")
    paths_filename = config.getStringValue("filename_routing_ptn_input")
    properties_filename = config.getStringValue("filename_tariff_properties_file")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    logger.info("Finished reading input data")


    logger.info("Begin computing price matrix")
    write_routing = False

    properties = Statistic()

#### FLAT #########################################################################################

    if model.lower() == "flat" or model.lower() == "\"flat\"":
        price = config.getDoubleValue("taf_flat_price")
        price_matrix = PriceMatrix()
        for origin in ptn.getNodes():
            for destination in ptn.getNodes():
                if origin != destination:
                    price_matrix.setValue(origin.getId(), destination.getId(), price)
                else:
                    price_matrix.setValue(origin.getId(), destination.getId(), 0)
        properties.setValue("taf_model", "FLAT")
        if price >= 0:
            properties.setValue("no_stopover", True)
        elif price < 0:
            properties.setValue("no_stopover", False)
        properties.setValue("no_elongation", True)


#### NETWORK_DISTANCE #####################################################################################

    elif model.lower() == "network_distance" or model.lower() == "\"network_distance\"":
        fix = config.getDoubleValue("taf_fixed_costs")
        factor = config.getDoubleValue("taf_factor_costs")

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

        paths_lengths = routing.getPathLengths()
        price_matrix = price_matrix_helper.compute_price_matrix_network_distance(ptn, paths_lengths, fix, factor)
        write_routing = True

        properties.setValue("taf_model", "NETWORK_DISTANCE")
        if factor >= 0:
            properties.setValue("no_elongation", True)
        elif factor < 0:
            properties.setValue("no_elongation", False)
        if fix >= 0:
            properties.setValue("no_stopover", True)
        elif fix < 0:
            properties.setValue("no_stopover", False)

#### BEELINE_DISTANCE ######################################################################################

    elif model.lower() == "beeline_distance" or model.lower() == "\"beeline_distance\"":
        fix = config.getDoubleValue("taf_fixed_costs")
        factor = config.getDoubleValue("taf_factor_costs")
        price_matrix = price_matrix_helper.compute_price_matrix_beeline_distance(ptn, fix, factor)
        properties.setValue("taf_model", "BEELINE_DISTANCE")
        properties.setValue("no_elongation", False)
        if fix >= 0:
            properties.setValue("no_stopover", True)
        elif fix < 0:
            properties.setValue("no_stopover", False)


#### ZONE #########################################################################################

    elif model.lower() == "zone" or model.lower() == "\"zone\"":
        zones = ZoneReader.read(ptn)
        zone_prices = ZonePriceReader.read()

        counting_type_string = config.getStringValue("taf_zone_counting")
        if counting_type_string.lower() == "single" or counting_type_string.lower() == "\"single\"":
            counting_type = TariffZoneCountingType.SINGLE
        elif counting_type_string.lower() == "multiple" or counting_type_string.lower() == "\"multiple\"":
            counting_type = TariffZoneCountingType.MULTIPLE
        else:
            raise ConfigInvalidValueException("taf_zone_counting")

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

        price_matrix = price_matrix_helper.compute_price_matrix_zone(ptn, counting_type, routing, zones, zone_prices)

        write_routing = True

        properties.setValue("taf_model", "ZONE")
        properties.setValue("taf_zone_counting", counting_type.name)
        properties.setValue("no_elongation", zone_tariff.test_no_elongation(zone_prices))
        properties.setValue("no_stopover", zone_tariff.test_no_stopover(zone_prices, counting_type))
        properties.setValue("connected_zones", zone_tariff.test_connected_zones(zones, ptn))

    else:
        raise ConfigInvalidValueException("taf_model")

    logger.info("Finished computing price matrix")


    logger.info("Begin writing output data")
    if make_reference:
        PriceMatrixWriter.write(price_matrix, reference_matrix_filename)
    else:
        PriceMatrixWriter.write(price_matrix, filename_price_matrix)
    if write_routing:
        RoutingWriter.write(routing)

    # write tariff properties to statistic file
    StatisticWriter.write(properties, properties_filename, config, False)
    logger.info("Finished writing output data")
