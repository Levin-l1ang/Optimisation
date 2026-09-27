import logging
from typing import List, Dict
import networkx as nx
from collections import defaultdict
import math

from core.model.tariff import PriceMatrix, Zone, ZonePrices
from core.model.ptn import Link, Stop
from core.model.graph import Graph
from core.model.tariff_types import TariffZoneCountingType
from core.model.routing import Routing

logger = logging.getLogger(__name__)


### FLAT ###########################################################################################


def compute_price_matrix_flat(ptn: Graph[Stop, Link], price: float) -> PriceMatrix:
    """
    Computes price matrix for given flat tariff price.
    :param ptn: the ptn
    :param price: flat price
    :return: the price matrix
    """
    price_matrix = PriceMatrix()
    for origin in ptn.getNodes():
        for destination in ptn.getNodes():
            if origin.getId() != destination.getId():
                price_matrix.setValue(origin.getId(), destination.getId(), price)
            else:
                price_matrix.setValue(origin.getId(), destination.getId(), 0)
    return price_matrix


### NETWORK DISTANCE #######################################################################################


def compute_price_matrix_network_distance(ptn: Graph[Stop, Link], paths_lengths: Dict[Stop, Dict[Stop, float]], fix: float, factor: float) -> PriceMatrix:
    """
    Computes price matrix for given fixed and factor costs w.r.t. to given paths lengths.
    :param ptn: the ptn
    :param paths_lengths: Dictionary of the network distances between node pairs
    :param fix: fixed costs
    :param factor: factor costs
    :return: the price matrix
    """
    price_matrix = PriceMatrix()
    for origin in ptn.getNodes():
        origin_id = origin.getId()
        for destination in ptn.getNodes():
            destination_id = destination.getId()
            if origin_id != destination_id:
                distance = paths_lengths[origin][destination]
                price_matrix.setValue(origin_id, destination_id, round(fix + factor * distance, 2))
            else:
                price_matrix.setValue(origin_id, destination_id, 0)

    return price_matrix


### BEELINE DISTANCE #######################################################################################


def compute_price_matrix_beeline_distance(ptn: Graph[Stop, Link], fix: float, factor: float) -> PriceMatrix:
    """
    Computes price matrix for given fixed and factor costs for a beeline distance tariff.
    :param ptn: the ptn
    :param fix: fixed costs
    :param factor: factor costs
    :return: the price matrix
    """
    price_matrix = PriceMatrix()
    for origin in ptn.getNodes():
        origin_id = origin.getId()
        for destination in ptn.getNodes():
            destination_id = destination.getId()
            if origin_id != destination_id:
                distance = math.dist([origin.getXCoordinate(), origin.getYCoordinate()],[destination.getXCoordinate(), destination.getYCoordinate()])
                price_matrix.setValue(origin_id, destination_id, round(fix + factor * distance, 2))
            else:
                price_matrix.setValue(origin_id, destination_id, 0)

    return price_matrix


### ZONE ###################################################################################################


def compute_price_matrix_zone(ptn: Graph[Stop, Link], counting_type: TariffZoneCountingType, routing: Routing, zones: List[Zone], zone_prices: ZonePrices) -> PriceMatrix:
    """
    Computes the price for each od pair in a zone tariff along shortest paths for given zone information.
    :param ptn: The ptn
    :param counting_type: specifies the counting type, e.g. single counting or multiple counting
    :param zone: List of given zones
    :param zone_prices: List of given zone prices
    :return: the computed price matrix
    """
    nodes = ptn.getNodes()
    paths = routing.getPaths()

    # dictionary of paths between all nodes (as edge or node list), indexed by node Ids
    paths_between_nodes = defaultdict(dict)
    for node1 in nodes:
        for node2 in nodes:
            if counting_type == TariffZoneCountingType.SINGLE:
                # paths given as list of nodes
                paths_between_nodes[node1.getId()][node2.getId()] = paths[node1][node2].getNodes()
            elif counting_type == TariffZoneCountingType.MULTIPLE:
                # paths given as list of edges
                paths_between_nodes[node1.getId()][node2.getId()] = [
                    (paths[node1][node2].getNodes()[k], paths[node1][node2].getNodes()[k + 1]) for k in range(len(paths[node1][node2].getNodes()) - 1)]
            else:
                raise NotImplementedError

    zone_of_node = dict()
    for zone in zones:
        for stop in zone.getStops():
            zone_of_node[stop] = zone.getZoneId()

    # for each node pair determine number of zones visited on given path
    number_zones = {}
    for node1 in nodes:
        for node2 in nodes:
            number_zones[node1, node2] = 1
            if counting_type == TariffZoneCountingType.SINGLE:
                zone_set = set()
                for node in paths_between_nodes[node1.getId()][node2.getId()]:
                    zone_set.add(zone_of_node[node])
                number_zones[node1, node2] = len(zone_set)
            elif TariffZoneCountingType.MULTIPLE:
                for (start, end) in paths_between_nodes[node1.getId()][node2.getId()]:
                    if zone_of_node[start] != zone_of_node[end]:
                        number_zones[node1, node2] += 1
            else:
                raise NotImplementedError

    price_matrix = PriceMatrix()
    for origin in nodes:
        for destination in nodes:
            if origin.getId() != destination.getId():
                price_matrix.setValue(origin.getId(), destination.getId(), zone_prices.getPrice(number_zones[origin, destination]))
            else:
                price_matrix.setValue(origin.getId(), destination.getId(), 0)

    return price_matrix
