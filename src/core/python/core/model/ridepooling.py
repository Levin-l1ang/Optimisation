import logging
import math
from typing import Dict, List
import networkx as nx

from core.model.ptn import Link

from core.exceptions.data_exceptions import DataRidepoolingAreaInconsistencyException

class RidepoolingArea:
    """
    A class for representing a ridepooling area.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, area_id: int, nb_vehicles: int = 0, edges: List[Link]=[], distribution: Dict[int, float] = {}, stretch_factors: Dict[int, float] = {}):
        """
        Constructor for a ridepooling area with given information. For every edge a stretch factor for the edge length
        (usually minimal durations) is initialized (Default value 1). Optionally, for every edge a vehicle frequency can be specified,
        stating how often a single vehicle in this area is expected to traverse this edge in one period. Those values are only initialized if needed for later computations.

        :param area_id: the id of the area
        :type area_id: int
        :param nb_vehicles: number of vehicles operating in the area, defaults to 0
        :type nb_vehicles: int, optional
        :param edges: list of edges contained in the area, defaults to []
        :type edges: List[Link], optional
        :param distribution:  dictionary of vehicle frequencies, stating how often a single vehicle in this area is expected to traverse this edge in one period, defaults to {}. If not given, the values will be set to -1 to represent that no vehicle frequency has been set or calculated yet.
        :type distribution: Dict[int, float], optional
        :param stretch_factors: dictionary of stretch factor for the edge lengths (usually minimal durations), defaults to {}. If not given, all stretch factors will be set to 1.
        :type stretch_factors: Dict[int, float], optional
        :raises DataRidepoolingAreaInconsistencyException: if the given vehicle frequencies or stretch factors are inconsistent with the given edge set
        """

        self.area_id = area_id
        self.nb_vehicles = nb_vehicles
        self.edges = []
        self.stretch_factor = {}                # Exists always for every area and edge with default value 1. Dict[int (edgeid), float]
        self.distribution = {}                  # -1=value not set. Dict[int (edgeid), float]

        if distribution:
            for edge in edges:
                if edge.getId() not in distribution:
                    self.logger.error(f"Given distribution does not fit with given Edge set! Edge {edge.getId()} not in distribution.")
                    raise DataRidepoolingAreaInconsistencyException(area_id)
            for edge_id in distribution.keys():
                if edge_id not in [e.getId() for e in edges]:
                    self.logger.error(f"Given distribution does not fit with given Edge set! Edge {edge_id} not in given edge set.")
                    raise DataRidepoolingAreaInconsistencyException(area_id)

        if stretch_factors:
            for edge in edges:
                if edge.getId() not in stretch_factors:
                    self.logger.error(f"Given stretch factors do not fit with given Edge set! Edge {edge.getId()} not in stretch factors.")
                    raise DataRidepoolingAreaInconsistencyException(area_id)
            for edge_id in stretch_factors.keys():
                if edge_id not in [e.getId() for e in edges]:
                    self.logger.error(f"Given stretch factors does not fit with given Edge set! Edge {edge_id} not in given edge set.")
                    raise DataRidepoolingAreaInconsistencyException(area_id)

        for link in edges[:-1]:
            self.addLink(link)
            if stretch_factors:
                self.setStretchFactor(link.getId(), stretch_factors[link.getId()])
            if distribution:
                self.setStretchFactor(link.getId(), distribution[link.getId()])

        if edges:
            self.addLink(edges[-1])
            if stretch_factors:
                self.setStretchFactor(edges[-1].getId(), stretch_factors[edges[-1].getId()])
            if distribution:
                self.setStretchFactor(edges[-1].getId(), distribution[edges[-1].getId()])


    def addLink(self, link: Link, distribution: float = -1, stretch_factor: float = 1) -> bool:
        """
        Method to add a new link to the ridepooling area

        :param link: the link to add
        :type link: Link
        :param distribution: vehicle frequency of one vehicle on this edge. Default of -1 denotes value not set.
        :type distribution: float, optional
        :param stretch_factor: factor to stretch the length (usually minimal duration) of this edge, defaults to 1
        :type stretch_factor: float, optional
        :return: whether the link could be added
        :rtype: bool
        """

        if link in self.edges:
            self.logger.debug("Link {} is already contained in ridepooling area {}! Not added.".format(link.getId(), self.getId()))
            return False
        else:
            self.edges.append(link)
            self.edges.sort(key=lambda e: e.getId())

            self.stretch_factor[link.getId()] = stretch_factor
            self.distribution[link.getId()] = distribution
            return True

    def getId(self) -> int:
        """
        Gets the id of the area.
        :return: area id
        """
        return self.area_id

    def getNumberOfVehicles(self) -> int:
        """
        Gets the number of vehicles operating in the area
        :param nb_vehicles: number of vehicles
        """
        return self.nb_vehicles

    def getEdges(self) -> List[Link]:
        """
        Gets the edges belonging to the area.
        :return: the edges of the area
        """
        return self.edges

    def getStretchFactor(self, edge_id: int) -> float:
        """
        Gets the factor for the length (usually minimal duration) of the specified edge.
        :param edge_id: the Id of the edge to get the stretch factor
        :return: the stretch factor of the edge
        """
        return self.stretch_factor[edge_id]

    def getAreaStretchFactors(self) -> Dict[int, float]:
        """
        Gets the stretch factors for the lengths (usually minimal duration) of all edges of the area as dictionary.
        :return: dictionary of the stretch factors
        """
        return self.stretch_factor

    def getVehicleFrequency(self, edge_id: int) -> float:
        """
        Gets the vehicle frequency in this area on a specific edge.
        :param edge_id: id of the edge in this area
        :return: the vehicle frequency
        """
        return self.distribution[edge_id]

    def getVehicleFrequencies(self) -> Dict[int, float]:
        """
        Gets the vehicle frequency in this area of all edges as dictionary.
        :return: dictionary of the vehicle frequency
        """
        return self.distribution

    def setNumberOfVehicles(self, nb_vehicles: int) -> None:
        """
        Sets the number of vehicles operating in the area
        :param nb_vehicles: number of vehicles
        """
        self.nb_vehicles = nb_vehicles

    def setStretchFactor(self, edge_id: int, stretch: float) -> bool:
        """
        Set the stretch factor for the length (usually minimal duration) of a specific edge in this area to the given factor.
        :param edge_id: id of the edge in the area
        :param stretch_factor: stretch factor to set
        :return: Whether the value was set succesfully or not
        """
        if edge_id not in [e.getId() for e in self.edges]:
            return False
        self.stretch_factor[edge_id] = stretch
        return True

    def setDistribution(self, edge_id: int, distribution: float) -> bool:
        """
        Set the vehicle frequency in this area on a specific edge to the given factor.
        :param edge_id: id of the edge in this area
        :param distribution: distributin factor to set
        :return: Whether the value was set succesfully or not
        """
        if edge_id not in [e.getId() for e in self.edges]:
            return False
        self.distribution[edge_id] = distribution
        return True

    def compareEdges(self, other) -> bool:
        """
        Method to compare the edge sets of areas.
        :param other: area to compare
        :return: whether the edge sets are equal
        """
        if not isinstance(other, RidepoolingArea):
            return False
        return set(self.getEdges()) == set(other.getEdges())

    def isConnected(self) -> bool:
        """
        Method to test if the area is strongly connected.
        :return: whether the area is strongly connected
        """
        if len(self.getEdges()) == 0:
            return True
        if self.getEdges()[0].isDirected():
            G = nx.DiGraph([(edge.getLeftNode().getId(), edge.getRightNode().getId()) for edge in self.getEdges()])
        else:
            G = nx.DiGraph([(edge.getLeftNode().getId(), edge.getRightNode().getId()) for edge in self.getEdges()]
                           +[(edge.getRightNode().getId(), edge.getLeftNode().getId()) for edge in self.getEdges()])
        return nx.is_strongly_connected(G)

    def includesEdge(self, link: Link) -> bool:
        """
        Method to test if a given edge is yet included in the area.
        :param link: link to test
        :return: whether the edge is included or not
        """
        return link in self.edges

    def computeVehicleFrequenciesByEulerCircle(self, period_length: int, vehicle_capacity: int, use_ptn_load: bool) -> None:
        """
        Method to compute the vehicle frequency for each edge in the area by constructing
        an Euler circle in a graph with multiple parallels. Note that the float values are rounded to 5 decimals.
        :param period_length: the period length to consider
        :param vehicle_capacity: capacity (or average occupancy) of one ridepooling vehicle
        :param use_ptn_load: whether the load of the PTN edges of the maximal load of the underlying infrastructure path should be used
        """
        load = {}
        if use_ptn_load:
            load = {edge: edge.getLoad() for edge in self.getEdges()}
        else:
            load = {edge: max([link.getLoad() for link in edge.getUnderlyingInfrastructure().getEdges()]) for edge in self.getEdges()}
        sum_of_weighted_durations = sum([edge.getLowerBound() * 2 * (math.ceil(load[edge]/vehicle_capacity) + 1)//2 for edge in self.getEdges()])
        for edge in self.getEdges():
            self.distribution[edge.getId()] = round(2 * (math.ceil(load[edge]/vehicle_capacity) + 1)//2 * period_length/sum_of_weighted_durations, 5)

    def __eq__(self, other):
        if not isinstance(other, RidepoolingArea):
            return False
        return (self.getId() == other.getId()
                and math.isclose(self.getNumberOfVehicles(), other.getNumberOfVehicles())
                and math.isclose(self.getCost(), other.getCost())
                and self.getEdges() == other.getEdges())

    def __ne__(self, other):
        return not self.__eq__(other)

    def __hash__(self) -> int:
        return self.area_id

    def __str__(self):
        return ("Area " + str(self.getId()) + "\nEdge; stretch-factor; vehicle-frequency-of-edge; number-of-vehicles\n"
                + "\n".join(["{}; {}; {}; {}".format(edge.getId(), self.getStretchFactor(edge.getId()), self.getVehicleFrequency(edge.getId()), self.getNumberOfVehicles())
                             for edge in self.getEdges()]))


class RidepoolingPool:
    """
    A class to represent the ridepooling pool.
    """

    def __init__(self, cost: float = 0):
        """Constructor of a new empty ridepooling pool. Consists of a list of RidepoolingAreas.

        :param cost: costs of a single ridepooling vehicle, defaults to 0
        :type cost: float, optional
        """
        self.rpool: dict[int, RidepoolingArea] = {}
        self.costs = cost

    def addArea(self, area: RidepoolingArea, allow_multiple_areas: bool = False) -> bool:
        """
        Method to add a ridepooling area, if not already an area with the same id is in the
        pool. By default, areas with the same edge set as an already existing area can only be included, if they differ in the distribution values or stretch factors.
        :param area: the ridepooling area to add
        :param allow_multiple_areas: whether it is allowed to add multiple areas with the same edge set, the same vehicle frequency, and the same stretch factors.
        :return: whether the area could be added.
        """
        if area.getId() in self.rpool:
            return False
        if not allow_multiple_areas:
            for other in self.rpool.values():
                if set(area.getEdges()) == set(other.getEdges()) and area.getVehicleFrequencies() == other.getVehicleFrequencies() and area.getAreaStretchFactors() == other.getAreaStretchFactors():
                    return False
        self.rpool[area.getId()] = area
        return True

    def removeArea(self, area_id: int) -> bool:
        """
        Method to remove area with given id, if it exists in pool.
        :param area_id: id of the area to remove
        :return: whether an area was removed
        """
        if area_id not in self.rpool:
            return False
        del self.rpool[area_id]
        return True

    def getAreas(self) -> List[RidepoolingArea]:
        """
        Gets a list of the ridepooling areas. This is a copy, i.e., removing or adding
        areas to the list will not change the ridepooling.
        :return: the areas in the pool
        """
        return list(self.rpool.values())

    def getArea(self, area_id: int) -> RidepoolingArea:
        """
        Gets the ridepooling area for a given id or raises KeyError if it is not in the
        pool.
        :param area_id: id of the area to get
        :return: the area with the given id
        """
        return self.rpool[area_id]

    def getRideConcept(self) -> List[RidepoolingArea]:
        """
        Method to get a list of all ridepooling areas with number of vehicles > 0.
        :return: a list of all ridepooling areas with number of vehicles > 0
        """
        return [area for area in self.rpool.values() if area.getNumberOfVehicles() > 0]

    def getCost(self) -> float:
        """
        Gets the cost of one vehicle operating in an area in the ridepooling pool
        :return: cost of one vehicle operating in the area
        """
        return self.costs

    def setCost(self, cost: float) -> None:
        """
        Sets the cost of one vehicle operating in an area in the ridepooling pool
        :param cost: cost of one vehicle operating in the area
        """
        self.costs = cost

    def testConnected(self) -> int:
        """
        Method to test if all areas in the ridepooling pool are strongly connected.
        :return: the id of the area which is not strongly connected, or -1 of all areas are strongly connected.
        """
        for area in self.rpool.values():
            if not area.isConnected():
                return area.getId()
        return -1

    def computeVehicleFrequenciesByEulerCircle(self, period_length: int, vehicle_capacity: int, use_ptn_load: bool) -> None:
        """
        Method to compute the vehicle frequency for each edge in every ridepooling area by constructing
        an Euler circle in a graph with multiple parallels. Note that the float values are rounded to 5 decimals.
        :param period_length: the period length to consider
        :param vehicle_capacity: capacity (or average occupancy) of one ridepooling vehicle
        :param use_ptn_load: whether the load of the PTN edges of the maximal load of the underlying infrastructure path should be used
        """
        for area in self.getAreas():
            area.computeVehicleFrequenciesByEulerCircle(period_length, vehicle_capacity, use_ptn_load)

    def getVehicleFrequency(self, area_id: int, edge_id: int) -> float:
        """
        Gets the vehicle frequency in a specific area on a specific edge.
        :param area_id: the id of the area
        :param edge_id: id of the edge in this area
        :return: the vehicle frequency
        """
        return self.getArea(area_id).getVehicleFrequency(edge_id)

    def getVehicleFrequencies(self, area_id: int) -> Dict[int, float]:
        """
        Gets the vehicle frequency in a specific area of all edges as dictionary.
        :param area_id: the id of the area
        :return: dictionary of the vehicle frequency
        """
        return self.getArea(area_id).getVehicleFrequencies()

    def getStretchFactor(self, area_id: int, edge_id: int) -> float:
        """
        Gets the factor for the length (usually minimal duration) of the specified edge in the specified area.
        :param area_id: the id of the area
        :param edge_id: the Id of the edge to get the stretch factor
        :return: the stretch factor of the edge
        """
        return self.getArea(area_id).getStretchFactor(edge_id)

    def getAreaStretchFactors(self, area_id: int) -> Dict[int, float]:
        """
        Gets the stretch factors for the lengths (usually minimal duration) of all edges of a specific area as dictionary.
        :param area_id: the id of the area
        :return: dictionary of the stretch factors
        """
        return self.getArea(area_id).getAreaStretchFactors()

    def getNextAreaId(self) -> int:
        """Returns the next free area Id to be used to add new areas to the pool

        :return: next free area Id in the pool
        :rtype: int
        """
        return next(i for i in range(1, len(self.getAreas()) + 2) if i not in [a.getId() for a in self.getAreas()])

    def __eq__(self, other):
        if not isinstance(other, RidepoolingPool):
            return False
        return self.rpool == other.rpool

    def __ne__(self, other):
        return not self.__eq__(other)

    def __str__(self):
        return ("RidepoolingPool:\n"
                + "\n".join(["{}\n".format(str(area))
                             for area in self.getAreas()]))
