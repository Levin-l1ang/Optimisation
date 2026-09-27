import sys
import logging
from collections import defaultdict

import zone_tariff
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigInvalidValueException
from core.exceptions.input_exceptions import InputFileException
from core.exceptions.data_exceptions import DataRoutingIncompleteException
from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.io.tariff import PriceMatrixReader, PriceMatrixWriter, ZonePriceWriter, ZonePriceReader, ZoneWriter, ZoneReader
from core.model.tariff import Zone
from core.model.impl.mapOD import MapOD
from core.model.tariff_types import TariffWeightType, TariffObjectiveType, TariffZoneCountingType, TariffZoneSymmetryOption, TariffRoutingType
from core.io.routing import RoutingReader, RoutingWriter
from core.model.routing import Routing
from core.io.statistic import Statistic, StatisticWriter
from core.solver.solver_parameters import SolverParameters

logger = logging.getLogger(__name__)

if __name__ == '__main__':
    logger.info("Begin reading configuration")
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    config = ConfigReader.read(sys.argv[1])
    parameters = SolverParameters(config, "taf_")
    counting_type_string = config.getStringValue("taf_zone_counting")
    upper_bound_zones = config.getIntegerValue("taf_zone_n_zones")
    if upper_bound_zones <= 0:
        logger.error("The number of zones must be at least one!")
        raise ConfigInvalidValueException("taf_zone_n_zones")
    enforce_all_zones = config.getBooleanValue("taf_zone_enforce_all_zones")
    connected_zones = config.getBooleanValue("taf_zone_connected")
    no_elongation = config.getBooleanValue("taf_zone_enforce_no_elongation")
    no_stopover = config.getBooleanValue("taf_zone_enforce_no_stopover")
    only_zones = config.getBooleanValue("taf_zone_only_zones")
    only_prices = config.getBooleanValue("taf_zone_only_prices")
    reference_matrix_filename = config.getStringValue("filename_tariff_reference_price_matrix_file")
    weight_objective_string = config.getStringValue("taf_weights_objective")
    taf_objective_string = config.getStringValue("taf_objective")
    symmetry_breaking_string = config.getStringValue("taf_zone_symmetry_breaking")
    routing_type_string = config.getStringValue("taf_routing_generation")
    routing_filename = config.getStringValue("filename_routing_ptn_input")
    properties_filename = config.getStringValue("filename_tariff_properties_file")

    if taf_objective_string.lower() == "sum_absolute_deviation" or taf_objective_string.lower() == "\"sum_absolute_deviation\"":
        taf_obj_type = TariffObjectiveType.SUM_ABSOLUTE_DEVIATION
    elif taf_objective_string.lower() == "max_absolute_deviation" or taf_objective_string.lower() == "\"max_absolute_deviation\"":
        taf_obj_type = TariffObjectiveType.MAX_ABSOLUTE_DEVIATION
    else:
        raise ConfigInvalidValueException("taf_objective")

    if symmetry_breaking_string.lower() == "a" or symmetry_breaking_string.lower() == "\"a\"":
        symmetry_breaking = TariffZoneSymmetryOption.A
    elif symmetry_breaking_string.lower() == "b" or symmetry_breaking_string.lower() == "\"b\"":
        symmetry_breaking = TariffZoneSymmetryOption.B
    elif symmetry_breaking_string.lower() == "none" or symmetry_breaking_string.lower() == "\"none\"":
        symmetry_breaking = TariffZoneSymmetryOption.NONE
    else:
        raise ConfigInvalidValueException("taf_zone_symmetry_breaking")

    if counting_type_string.lower() == "single" or counting_type_string.lower() == "\"single\"":
        counting_type = TariffZoneCountingType.SINGLE
    elif counting_type_string.lower() == "multiple" or counting_type_string.lower() == "\"multiple\"":
        counting_type = TariffZoneCountingType.MULTIPLE
    else:
        raise ConfigInvalidValueException("taf_zone_counting")

    if weight_objective_string.lower() == "od" or weight_objective_string.lower() == "\"od\"":
        weight_objective = TariffWeightType.OD
    elif weight_objective_string.lower() == "unit" or weight_objective_string.lower() == "\"unit\"":
        weight_objective = TariffWeightType.UNIT
    elif weight_objective_string.lower() == "reference_inverse" or weight_objective_string.lower() == "\"reference_inverse\"":
        weight_objective = TariffWeightType.REFERENCE_INVERSE
    else:
        raise ConfigInvalidValueException("taf_weights_objective")

    logger.info("Finished reading configuration")


    logger.info("Begin reading input data")
    ptn = PTNReader.read()
    od = ODReader.read(MapOD(), -1)
    reference_prices = PriceMatrixReader.read(ptn, reference_matrix_filename)

    if routing_type_string.lower() == "fastest-paths" or routing_type_string.lower() == "\"fastest-paths\"":
        routing_type = TariffRoutingType.FASTEST_PATHS
        routing = Routing(ptn).fillShortestPaths(lambda e: e.getLowerBound())
    elif routing_type_string.lower() == "read-all" or routing_type_string.lower() == "\"read-all\"":
        routing_type = TariffRoutingType.READ_ALL
        routing = RoutingReader.read(ptn, routing_file_name=routing_filename)
        if not routing.isComplete():
            raise DataRoutingIncompleteException
    elif routing_type_string.lower() == "read-partial-fill" or routing_type_string.lower() == "\"read-partial-fill\"":
        routing_type = TariffRoutingType.READ_PARTIAL_FILL
        routing = RoutingReader.read(ptn, routing_file_name=routing_filename).fillShortestPaths(lambda e: e.getLowerBound())
    else:
        raise ConfigInvalidValueException("taf_routing_generation")

    if only_prices and only_zones:
        logger.error("It is not possible to optimize only prices and only zones simultaneously!")
        raise ConfigInvalidValueException(["taf_zone_only_prices", "taf_zone_only_zones"])
    if only_zones:
        try:
            given_prices = ZonePriceReader.read()
        except InputFileException:
            logger.error("To optimize only zones a Zone-Price file is needed!")
            raise ConfigInvalidValueException("taf_zone_only_prices")
    elif only_prices:
        try:
            given_zones = ZoneReader.read(ptn)
        except InputFileException:
            logger.error("To optimize only prices a Zone file is needed!")
            raise ConfigInvalidValueException("taf_zone_only_zones")
    logger.info("Finished reading input data")


    logger.info("Begin preparing data")

    if weight_objective == TariffWeightType.UNIT:
        weights = {(origin.getId(), destination.getId()): 1 for origin in ptn.getNodes() for destination in ptn.getNodes() if origin != destination}
    elif weight_objective == TariffWeightType.REFERENCE_INVERSE:
        if reference_prices.hasNonDiagonalZeros():
            logger.error("There are zero values in the reference-price-matrix, cannot use reference_inverse weights!")
            raise ZeroDivisionError
        weights = {(origin.getId(), destination.getId()): 1/reference_prices.getValue(origin.getId(), destination.getId()) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin != destination}
    elif weight_objective == TariffWeightType.OD:
        weights = {(origin.getId(), destination.getId()): od.getValue(origin.getId(), destination.getId()) for origin in ptn.getNodes() for destination in ptn.getNodes() if origin != destination}

    edges = ptn.getEdges()
    nodes = ptn.getNodes()
    number_of_nodes = len(nodes)

    if enforce_all_zones and upper_bound_zones > number_of_nodes:
        logger.error("No empty zones enforced and upper bound for zones is larger than number of nodes")
        raise ConfigInvalidValueException(["taf_zone_enforce_all_zones", "taf_zone_n_zones"])

    # list of edges in both directions
    adjacent_node_pairs = []
    for edge in edges:
        adjacent_node_pairs.append((edge.getLeftNode(), edge.getRightNode()))
        adjacent_node_pairs.append((edge.getRightNode(), edge.getLeftNode()))
    adjacent_node_pairs = list(dict.fromkeys(adjacent_node_pairs))

    # dictionary of paths between all nodes (as edge or node list), indexed by node Ids
    paths = routing.getPaths()
    paths_between_nodes = defaultdict(dict)
    for node1 in nodes:
        for node2 in nodes:
            if node1!=node2:
                if counting_type == TariffZoneCountingType.SINGLE:
                    # paths given as list of nodes
                    paths_between_nodes[node1.getId()][node2.getId()] = paths[node1][node2].getNodes()
                elif counting_type == TariffZoneCountingType.MULTIPLE:
                    # paths given as list of edges
                    paths_between_nodes[node1.getId()][node2.getId()] = [
                        (paths[node1][node2].getNodes()[k], paths[node1][node2].getNodes()[k + 1]) for k in range(len(paths[node1][node2].getNodes()) - 1)]

    # passenger demand (=OD-value) as dictionary indexed by pairs of node Ids
    number_passengers_between_nodes = {(node1.getId(), node2.getId()): 0 for node1 in nodes for node2 in nodes}
    for od_pair in od.getODPairs():
        number_passengers_between_nodes[(od_pair.getOrigin(), od_pair.getDestination())] = od_pair.getValue()

    logger.info("Finished preparing data")


    logger.info("Begin execution of zone tariff model")
    if only_prices:
        logger.info("Determine only prices for fixed zones")

        # Test if values of config parameters are compatible with given zones, if not display warning message
        if enforce_all_zones and len(given_zones) < upper_bound_zones:
            logger.warning("The config parameter taf_zone_enforce_all_zones is true, but there are less given zones than the value of taf_zone_n_zones.")
        given_zones_connected = zone_tariff.test_connected_zones(given_zones, ptn)
        if connected_zones and not given_zones_connected:
            logger.warning("The config parameter connected_zones is true, but the given zones are not connected.")

        (objective_value, zone_prices, price_matrix) = zone_tariff.onlyPrices(given_zones, counting_type, nodes, paths_between_nodes,
                                                                              no_elongation, no_stopover, reference_prices, weights, taf_obj_type,
                                                                              parameters)

        logger.info("Finished execution of zone tariff model")

        logger.info("Begin writing output data")
        ZonePriceWriter.write(zone_prices)
        PriceMatrixWriter.write(price_matrix)
        RoutingWriter.write(routing)
        # write tariff properties to statistic file
        properties = Statistic()
        properties.setValue("taf_model", "ZONE")
        properties.setValue("no_elongation", zone_tariff.test_no_elongation(zone_prices))
        properties.setValue("no_stopover", zone_tariff.test_no_stopover(zone_prices, counting_type))
        properties.setValue("connected_zones", given_zones_connected)
        properties.setValue("taf_zone_counting", counting_type.name)
        StatisticWriter.write(properties, properties_filename, config, False)
        logger.info("Finished writing output data")

    elif only_zones:
        logger.info("Determine only zones for fixed prices")

        # Test if value of config parameters are compatible with given prices, if not display warning message
        given_zones_no_elongation = zone_tariff.test_no_elongation(given_prices)
        if no_elongation and not given_zones_no_elongation:
            logger.warning("The no elongation property is not satisfied for the given prices, but the corresponding config parameter is set to true. Given prices are used anyway.")
        given_zones_no_stopover = zone_tariff.test_no_stopover(given_prices, counting_type)
        if no_stopover and not given_zones_no_stopover:
            logger.warning("The no stopover property is not satisfied for the given prices, but the corresponding config parameter is set to true. Given prices are used anyway.")

        (objective_value, zones, price_matrix) = zone_tariff.onlyZones(given_prices, enforce_all_zones, connected_zones,
                                                                       counting_type, nodes, paths_between_nodes, adjacent_node_pairs,
                                                                       reference_prices, upper_bound_zones, weights, taf_obj_type, symmetry_breaking,
                                                                       parameters)

        zone_list = []
        for item in zones:
            zone = Zone(item[0], set())
            for ind in item[1]:
                zone.addStop(ptn.getNode(ind))
            zone_list.append(zone)

        logger.info("Finished execution of zone tariff model")

        logger.info("Begin writing output data")
        ZoneWriter.write(zone_list)
        PriceMatrixWriter.write(price_matrix)
        RoutingWriter.write(routing)
        # write tariff properties to statistic file
        properties = Statistic()
        properties.setValue("taf_model", "ZONE")
        properties.setValue("no_elongation", given_zones_no_elongation)
        properties.setValue("no_stopover", given_zones_no_stopover)
        properties.setValue("connected_zones", zone_tariff.test_connected_zones(zone_list, ptn))
        properties.setValue("taf_zone_counting", counting_type.name)
        StatisticWriter.write(properties, properties_filename, config, False)
        logger.info("Finished writing output data")

    else:
        (objective_value, zones, zone_prices, price_matrix) = zone_tariff.solve(enforce_all_zones, connected_zones, no_elongation,
                                                              no_stopover, counting_type, nodes, paths_between_nodes,
                                                              adjacent_node_pairs, reference_prices, upper_bound_zones,
                                                              weights, taf_obj_type, symmetry_breaking,
                                                              parameters)
        zone_list = []
        for item in zones:
            zone = Zone(item[0], set())
            for ind in item[1]:
                zone.addStop(ptn.getNode(ind))
            zone_list.append(zone)

        logger.info("Finished execution of zone tariff model")


        logger.info("Begin writing output data")
        ZonePriceWriter.write(zone_prices)
        ZoneWriter.write(zone_list)
        PriceMatrixWriter.write(price_matrix)
        RoutingWriter.write(routing)
        # write tariff properties to statistic file
        properties = Statistic()
        properties.setValue("taf_model", "ZONE")
        properties.setValue("no_elongation", zone_tariff.test_no_elongation(zone_prices))
        properties.setValue("no_stopover", zone_tariff.test_no_stopover(zone_prices, counting_type))
        properties.setValue("connected_zones", zone_tariff.test_connected_zones(zone_list, ptn))
        properties.setValue("taf_zone_counting", counting_type.name)
        StatisticWriter.write(properties, properties_filename, config, False)
        logger.info("Finished writing output data")
