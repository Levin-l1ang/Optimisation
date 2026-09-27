import math
from typing import List

from core.io.csv import CsvWriter
from core.model.graph import Edge, Node
from core.model.infrastructure_network import InfraNode, InfraPath


class Stop(Node):
    """
    Template implementation of a stop in a PTN.
    """

    def __init__(
        self,
        stopId: int,
        shortName: str,
        longName: str,
        xCoordinate: float,
        yCoordinate: float,
        longitude: float = 0,
        latitude: float = 0,
        modality: str = '',
        infrastructure_node: InfraNode = None
    ) -> None:
        """
        Create a new Stop given the information of a LinTim stop.
        :param stopId: the id of the stop. Needs to be unique for any graph,
        this stop may be part of.
        :param shortName: the short name of the stop. This is a short
        representation of the stop. Need not be unique.
        :param longName: the long name of the stop. This is a longer
        representation of the stop.
        :param xCoordinate: the x-coordinate of the stop. This should be the
        result of some projection. Using the euclidean distance on the
        coordinates of the stop should result in a length given in
        kilometers.
        :param yCoordinate: the y-coordinate of the stop.  This should be the
        result of some projection. Using the euclidean distance on the
        coordinates of the stop should result in a length given in
        kilometers.
        :param longitude: the longitude of the stop. This should be the correct geo coordinate, if there is one.
        :param latitude: the latitude of the stop. This should be the correct geo coordinate, if there is one.
        :param modality: The modality this node belongs to.
        :param infrastructure_node: The corresponding node of the infrastructure network, if there is one.
        """
        self.stop_id = stopId
        self.short_name = shortName
        self.long_name = longName
        self.x_coord = xCoordinate
        self.y_coord = yCoordinate
        self.latitude = latitude
        self.longitude = longitude
        self.station = True
        self.modality = modality
        self.infrastructure_node = infrastructure_node

    def getId(self) -> int:
        return self.stop_id

    def setId(self, newId: int) -> None:
        self.stop_id = newId

    def getShortName(self) -> str:
        """
        Get the short name of the stop. This is a short representation of the
        stop. Need not be unique.
        :return: the short name
        """
        return self.short_name

    def getLongName(self) -> str:
        """
        Get the long name of the stop. This is a longer representation of the
        stop.
        :return: the long name
        """
        return self.long_name

    def getXCoordinate(self) -> float:
        """
        Get the x-coordinate of the stop. Is corresponding to the
        longitude coordinate of the stop but may be the result of some projection.
        :return: the x-coordinate
        """
        return self.x_coord

    def getYCoordinate(self) -> float:
        """
        Get the y-coordinate of the stop. Is corresponding to the
        latitude coordinate of the stop but may be the result of some projection.
        :return: the y-coordinate
        """
        return self.y_coord

    def getLatitude(self) -> float:
        """
        Get the latitude of the stop. May not be available, then this will return 0.
        :return: the latitude
        """
        return self.latitude

    def getLongitude(self) -> float:
        """
        Get the longitude of the stop. May not be available, then this will return 0.
        :return: the longitude
        """
        return self.longitude

    def setLatitude(self, latitude: float) -> None:
        """
        Set the latitude for the stop.
        :param latitude: the new latitude
        """
        self.latitude = latitude

    def setLongitude(self, longitude: float) -> None:
        """
        Set the longitude for the stop.
        :param longitude: the new longitude
        """
        self.longitude = longitude

    def isStation(self) -> bool:
        """
        Get whether this stop is actually a station. E.g. it may be the case
        that the stop is just a candidate in a
        stop location problem and not a built station (at least at the time of
        creation). Is initially set to true.
        :return: whether the stop is a station
        """
        return self.station

    def setStation(self, isStation: bool) -> None:
        """
        Set whether this stop is actually a station. E.g. it may be the case
        that the stop is just a candidate in a
        stop location problem and not a built station (at least at the time of
        creation).
        :param isStation: the new value
        """
        self.station = isStation

    def getModality(self) -> str:
        """
        Get the Modality of the node. This is a short representation of the
        modality. Need not be unique.
        :return: modality
        """
        return self.modality

    def setInfrastructureNode(self, infra_node: InfraNode, max_distance: float = 0) -> bool:
        """
        Set the infrastructure node that corresponds to the stop. The ids of the
        infrastructure node and the stop must be identical and the distance of the coordinates
        must be at most max_distance, which is zero by default.

        :param infra_node: the infrastructure node to set
        :type infra_node: InfraNode
        :param infra_node: the maximal allowed distance of the coordinates of the stop and the infrastructure node, defaults to 0
        :type infra_node: float, optional
        :return: True, if the node was set successfully, False otherwise
        :rtype: bool
        """
        if infra_node.getId() == self.getId() and math.sqrt((self.getXCoordinate()-infra_node.getXCoordinate())**2 + (self.getYCoordinate()-infra_node.getYCoordinate())**2) <= max_distance:
            self.infrastructure_node = infra_node
            return True
        else:
            return False

    def getInfrastructureNode(self) -> InfraNode:
        """
        Get the correpsonding infrastrucure node of the stop

        :return: the infrastructure node
        :rtype: InfraNode
        """
        return self.infrastructure_node

    def toCsvStrings(self, conversion_factor_coordinates: float = 1) \
            -> List[str]:
        """
        Return a string list, representing the stop for a LinTim csv file.
        :param conversion_factor_coordinates: coordinates are divided by this
            number.
        :return: the csv representation of this stop
        """
        return [
            str(self.stop_id), self.short_name, self.long_name,
            CsvWriter.shortenDecimalValueForOutput(
                self.x_coord / conversion_factor_coordinates,
            ),
            CsvWriter.shortenDecimalValueForOutput(
                self.y_coord / conversion_factor_coordinates,
            ),
        ]

    def __hash__(self) -> int:
        result = self.stop_id
        result = 31 * result + self.short_name.__hash__()
        result = 31 * result + self.long_name.__hash__()
        result = 31 * result + self.x_coord.__hash__()
        result = 31 * result + self.y_coord.__hash__()
        return result

    def __str__(self) -> str:
        return 'Stop ' + ', '.join(self.toCsvStrings()) + ', ' + self.modality

    def __eq__(self, o: object) -> bool:
        if not isinstance(o, Stop):
            return False
        return (
            o.getId() == self.getId() and
            self.getShortName() == o.getShortName() and
            self.getLongName() == o.getLongName() and
            self.getXCoordinate() == o.getXCoordinate() and
            self.getYCoordinate() == o.getYCoordinate() and
            self.getLatitude() == o.getLatitude() and
            self.getLongitude() == o.getLongitude() and
            self.isStation() == o.isStation() and
            self.modality == o.modality
        )

    def __ne__(self, other):
        return not self.__eq__(other)


class Link(Edge[Stop]):
    """
    A class representing an edge in a public transportation network (PTN).
    This class will contain all information that normally associated with a PTN
    edge in the LinTim context, i.e.,
    structural information as well as some passenger data.
    """

    def __init__(
        self,
        link_id: int,
        left_stop: Stop,
        right_stop: Stop,
        length: float,
        lower_bound: int,
        upper_bound: int,
        directed: bool,
        modality: str = "",
        underlying_infrastructure_links: InfraPath | None = None
    ) -> None:
        """
        Create a new Link, i.e., an edge in a Public Transportation Network.
        :param link_id: the id of the link, i.e., the id to reference the link.
                        Needs to be unique for a given graph
        :param left_stop: the left stop of the edge. This is the source of the
                          link, if the edge is directed
        :param right_stop: the right stop of the edge. This is the target of
                           the link if the edge is directed
        :param length: the length of the link, given in kilometers
        :param lower_bound: the lowerBound of the link, i.e., the minimal time
                            in minutes, a vehicle needs to traverse the edge.
        :param upper_bound: the upperBound of the link, i.e., the maximal time
                            in minutes, a vehicle needs to traverse the edge.
        :param directed: whether the link is directed
        """
        self.link_id = link_id
        self.left_stop = left_stop
        self.right_stop = right_stop
        self.length = length
        self.lower_bound = lower_bound
        self.upper_bound = upper_bound
        self.headway = 0
        self.directed = directed
        self.load = 0
        self.lower_frequency_bound = 0
        self.upper_frequency_bound = 0
        self.modality = modality
        self.underlying_infrastructure_links = underlying_infrastructure_links

    def getLeftNode(self) -> Stop:
        return self.left_stop

    def setId(self, new_id: int) -> None:
        self.link_id = new_id

    def getRightNode(self) -> Stop:
        return self.right_stop

    def getId(self) -> int:
        return self.link_id

    def isDirected(self) -> bool:
        return self.directed

    def getLength(self) -> float:
        """
        Get the length of the link, given in kilometers.
        :return: the length
        """
        return self.length

    def getLowerBound(self) -> int:
        """
        Get the lowerBound of the link, i.e., the minimal time in minutes, a
        vehicle needs to  traverse the edge.
        :return: the lower bound
        """
        return self.lower_bound

    def getUpperBound(self) -> int:
        """
        Get the upperBound of the link, i.e., the maximal time in minutes, a
        vehicle needs to traverse the edge.
        :return: the upper bound
        """
        return self.upper_bound

    def getLoad(self) -> float:
        """
        Get the load of the link, i.e., how many passengers traverse this link
        in the given period.
        :return: the load
        """
        return self.load

    def setLoad(self, new_load: float) -> None:
        """
        Set the load of the link, i.e., how many passengers traverse this link
        in the given period.
        :param new_load: the new load
        """
        self.load = new_load

    def getLowerFrequencyBound(self) -> int:
        """
        Get the lower frequency bound on the link, i.e., the minimal number of
        times a vehicle needs to traverse this
        link in a given period to serve the load.
        :return: the lower frequency bound
        """
        return self.lower_frequency_bound

    def setLowerFrequencyBound(self, new_bound: int) -> None:
        """
        Set the lower frequency bound on the link, i.e., the minimal number of
        times a vehicle needs to traverse this
        link in a given period to serve the load.
        :param new_bound: the lower frequency bound
        """
        self.lower_frequency_bound = new_bound

    def getUpperFrequencyBound(self) -> int:
        """
        Get the upper frequency bound on the link, i.e., the maximal number of
        times a vehicle may traverse this link
        in a given period.
        :return: the upper frequency bound
        """
        return self.upper_frequency_bound

    def setUpperFrequencyBound(self, new_bound: int) -> None:
        """
        Set the upper frequency bound on the link, i.e., the maximal number of
        times a vehicle may traverse this link
        in a given period.
        :param new_bound: the upper frequency bound
        """
        self.upper_frequency_bound = new_bound

    def setLoadInformation(
        self,
        load: float,
        lower_frequency_bound: int,
        upper_frequency_bound: int,
    ) -> None:
        """
        Set all information regarding the passenger load for the link.
        :param load: the new load of the link, i.e., how many passengers
                     traverse this link in the given period
        :param lower_frequency_bound: the lower frequency bound on the link,
                                      i.e., the minimal number of times a
                                      vehicle
        needs to traverse this link in a given period to serve the load
        :param upper_frequency_bound: the upper frequency bound on the link,
                                      i.e., the maximal number of times a
                                      vehicle
        may traverse this link in a given period
        """
        self.setLoad(load)
        self.setLowerFrequencyBound(lower_frequency_bound)
        self.setUpperFrequencyBound(upper_frequency_bound)

    def getHeadway(self):
        """
        Get the headway of the stop. This is the minimal time needed between
        two vehicle that serve this stop. Given
        in minutes. Is initially set to 0.
        :return: the headway of the stop
        """
        return self.headway

    def setHeadway(self, new_headway: int):
        """
        Set the headway of the stop. This is the minimal time needed between
        two vehicle that serve this stop. Should
        be given in minutes.
        :param new_headway: the new headway
        """
        self.headway = new_headway

    def getUnderlyingInfrastructure(self) -> InfraPath:
        """
        Get a path of infrastructure links representing the underlying infrastructure of this PTN link.
        This path is always undirected, as the infrastructure network is an undirected graph, even if the PTN is directed.

        :return: path of underlying infrastructure links
        :rtype: InfraPath
        """
        if not self.underlying_infrastructure_links:
            return InfraPath(self, self.modality)
        else:
            if self.underlying_infrastructure_links.checkEndpointsConsistency():
                return self.underlying_infrastructure_links
            else:
                self.underlying_infrastructure_links.reverse()
                return self.underlying_infrastructure_links

    def setUnderlyingInfrastructure(self, infrastructure_path: InfraPath) -> None:
        """
        Set the path of infrastructure links representing the underlying infrastructure of this PTN link

        :param infrastructure_path: path of underlying infrastructure links to set
        :type infrastructure_path: InfraPath
        """
        self.underlying_infrastructure_links = infrastructure_path

    def toCsvStrings(self, conversion_factor_length: float = 1) -> List[str]:
        """
        Return a string list, representing the link for a LinTim csv file.
        :param conversion_factor_length: the length is divided by this number.
        :return: the csv representation of this link
        """
        return [
            str(self.getId()),
            str(self.getLeftNode().getId()),
            str(self.getRightNode().getId()),
            CsvWriter.shortenDecimalValueForOutput(
                self.getLength() / conversion_factor_length,
            ),
            str(self.getLowerBound()),
            str(self.getUpperBound()),
        ]

    def toCsvLoadStrings(self) -> List[str]:
        """
        Return a string list, representing the link for a LinTim csv load file.
        :return: the csv load representation of this link
        """
        return [
            str(self.getId()),
            CsvWriter.shortenDecimalValueForOutput(self.getLoad()),
            str(self.getLowerFrequencyBound()),
            str(self.getUpperFrequencyBound()),
        ]

    def toCsvHeadwayStrings(self) -> List[str]:
        """
        Return a string list, representing the link for a LinTim csv headway
        file.
        :return: the csv headway representation of this link
        """
        return [str(self.getId()), str(self.getHeadway())]

    def __str__(self):
        return 'Link ' + ', '.join(self.toCsvStrings())

    def __eq__(self, o: object) -> bool:
        if not isinstance(o, Link):
            return False
        if not self.getId() == o.getId():
            return False
        if not math.isclose(self.getLength(), o.getLength()):
            return False
        if not self.getLowerBound() == o.getLowerBound():
            return False
        if not self.getUpperBound() == o.getUpperBound():
            return False
        if not math.isclose(self.getLoad(), o.getLoad()):
            return False
        if not self.getLowerFrequencyBound() == o.getLowerFrequencyBound():
            return False
        if not self.getUpperFrequencyBound() == o.getUpperFrequencyBound():
            return False
        if not self.getHeadway() == o.getHeadway():
            return False
        if not self.isDirected() == o.isDirected():
            return False
        if not self.modality == o.modality:
            return False
        # Check for the directed attribute and compare the edges
        if self.isDirected():
            return (
                self.getLeftNode() == o.getLeftNode()
                and self.getRightNode() == o.getRightNode()
            )
        else:
            return (
                (
                    self.getLeftNode() == o.getLeftNode()
                    and self.getRightNode() == o.getRightNode()
                ) or (
                    self.getLeftNode() == o.getRightNode()
                    and self.getRightNode() == o.getLeftNode()
                )
            )

    def __ne__(self, other):
        return not self.__eq__(other)

    def __hash__(self):
        return hash((self.link_id, self.left_stop, self.right_stop, self.length, self.lower_bound, self.upper_bound, self.directed))


class StationLimit:
    def __init__(self, stop_id: int, min_wait_time: int, max_wait_time: int, min_change_time: int, max_change_time: int):
        self.stop_id = stop_id
        self.min_wait_time = min_wait_time
        self.max_wait_time = max_wait_time
        self.min_change_time = min_change_time
        self.max_change_time = max_change_time

    def getStopId(self) -> int:
        return self.stop_id

    def getMinWaitTime(self) -> int:
        return self.min_wait_time

    def getMaxWaitTime(self) -> int:
        return self.max_wait_time

    def getMinChangeTime(self) -> int:
        return self.min_change_time

    def getMaxChangeTiem(self) -> int:
        return self.max_change_time

    def toCsvStrings(self) -> List[str]:
        return [
            str(self.getStopId()),
            str(self.getMinWaitTime()),
            str(self.getMaxWaitTime()),
            str(self.getMinChangeTime()),
            str(self.getMaxChangeTiem()),
        ]
