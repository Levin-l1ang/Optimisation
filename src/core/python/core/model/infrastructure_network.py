from typing import TYPE_CHECKING
from enum import Enum

from core.io.csv import CsvWriter
from core.model import graph
from core.model.graph import N
from core.exceptions.exceptions import LinTimException
from core.model.impl.list_path import ListPath
from core.exceptions.data_exceptions import DataPTNInfrastructureMapDirectionException, DataPTNInfrastructureMapModalityException

if TYPE_CHECKING:
    from core.model.ptn import Link


class DirectionType(Enum):
    """
    Enumeration of all possible direction types.

    FORWARDS: the edge can only be traversed from the left node to the right node
    BACKWARDS: the edge can only be traversed from the right node to the left node
    BOTH: the edge can be used in both directions
    """
    FORWARDS = "FORWARDS"
    BACKWARDS = "BACKWARDS"
    BOTH = "BOTH"



class InfraNode(graph.Node):
    """
    Class reprenting a node in the infrastructure network. A node is the smallest possible unit, may represent a
    stop, a demand point, an intersection, ...
    """

    def __init__(self, node_id: int, name: str, x_coord: float, y_coord: float, stop_for_modalities: list[str], latitude: float = 0, longitude: float = 0) -> None:
        """Create an infrastructure node. The id of an infrastructure node and the corresponding stops in the modality-specific PTN must be equal.

        :param node_id: id of the node. Must be unique for the graph.
        :type node_id: int
        :param x_coord: x coordinate of the node. When reading with the reader class, the coordinates are scaled such that euclidean distances are in kilometres.
        :type x_coord: float
        :param name: the name of the node
        :type name: str
        :param y_coord: y coordinate of the node. When reading with the reader class, the coordinates are scaled such that euclidean distances are in kilometres.
        :type y_coord: float
        :param stop_for_modalities: list of modalities given as strings for which this node is a possible stop
        :type stop_for_modalities: list[str]
        :param longitude: the longitude of the infrastructure node. This should be the correct geo coordinate, if there is one. defaults to 0
        :type longitude: float, optional
        :param latitude: the latitude of the infrastructure node. This should be the correct geo coordinate, if there is one. dfeaults to 0
        :type latitude: float, optional
        """
        self.node_id = node_id
        self.name = name
        self.x_coord = x_coord
        self.y_coord = y_coord
        self.latitude = latitude
        self.longitude = longitude
        self.modalities = stop_for_modalities

    def getId(self) -> int:
        """
        Get the id of the node

        :return: the id of the node
        :rtype: int
        """
        return self.node_id

    def setId(self, new_id: int) -> None:
        """
        Set the id of the node

        :param new_id: the id of the node
        :type new_id: int
        """
        self.node_id = new_id

    def getName(self) -> str:
        """
        Get the Name of the node

        :return: the Name of the node
        :rtype: str
        """
        return self.name

    def setName(self, name: str) -> None:
        """
        Set the Name of the node

        :param name: the Name of the node
        :type name: str
        """
        self.name = name

    def getXCoordinate(self) -> float:
        """
        Get the x coordinate of the node. When reading with the reader class, the coordinates are scaled such that euclidean distances are in kilometres.

        :return: the x coordinate
        :rtype: float
        """
        return self.x_coord

    def getYCoordinate(self) -> float:
        """
        Get the y coordinate of the node. When reading with the reader class, the coordinates are scaled such that euclidean distances are in kilometres.

        :return: the y coordinate
        :rtype: float
        """
        return self.y_coord

    def getModalities(self) -> list[str]:
        """
        list of all modalities (given as strings) for which this node can be used as a stop.

        :return: list of all modalities for which this node can be used as a stop.
        :rtype: list[str]
        """
        return self.modalities

    def addModality(self, modality: str) -> None:
        """
        Add a modality to the list of modalities of this node

        :param modality: the modality to add
        :type modality: str
        """
        self.modalities.append(modality)

    def addModalities(self, modalities: list[str]) -> None:
        """
        Add a list of modalities to the list of modalities of this node

        :param modality: list of modalities to add
        :type modality: list[str]
        """
        self.modalities += modalities

    def getLatitude(self) -> float:
        """
        Get the latitude of the infrastructure node. May not be available, then this will return 0.
        :return: the latitude
        """
        return self.latitude

    def getLongitude(self) -> float:
        """
        Get the longitude of the infrastructure node. May not be available, then this will return 0.
        :return: the longitude
        """
        return self.longitude

    def setLatitude(self, latitude: float) -> None:
        """
        Set the latitude for the infrastructure node.
        :param latitude: the new latitude
        """
        self.latitude = latitude

    def setLongitude(self, longitude: float) -> None:
        """
        Set the longitude for the infrastructure node.
        :param longitude: the new longitude
        """
        self.longitude = longitude

    def toCsvStrings(self, conversion_factor_coordinates: float = 1) -> list[str]:
        """
        Generate a list of strings to write the information about this node to a csv file.

        :param conversion_factor_coordinates: conversion factor for the coordinates, ensuring that euclidean distances are in kilometres, defaults to 1
        :type conversion_factor_coordinates: float, optional
        :return: list of strings to write to file
        :rtype: list[str]
        """
        return [str(self.getId()), self.name, CsvWriter.shortenDecimalValueForOutput(self.x_coord / conversion_factor_coordinates),
                CsvWriter.shortenDecimalValueForOutput(self.y_coord / conversion_factor_coordinates), '[' + ', '.join(self.getModalities()) + ']']

    def toCsvGeoStrings(self) -> list[str]:
        """
        Generate a list of strings to write the geo coordinates information about this node to a csv file.

        :return: list of strings to write to file
        :rtype: list[str]
        """
        return [str(self.getId()), str(self.getLatitude()), str(self.getLongitude())]

    def __eq__(self, o: object) -> bool:
        return isinstance(o, InfraNode) and \
               (self.node_id, self.x_coord, self.y_coord, self.modalities) == \
               (o.node_id, o.x_coord, o.y_coord, o.modalities)

    def __ne__(self, o: object) -> bool:
        return not self == o

    def __hash__(self) -> int:
        return hash(self.node_id)


class InfraLink(graph.Edge[InfraNode]):
    """
    Class representing an infrastructure link. These are the possible edges in the network, i.e., possible direct
    connections between two nodes. Infrastructure Links are always undirected. For each modality, a direction of the edge can be specified.
    Multiple infrastructe edges may be concatenated to form the underlying infrastructure of a PTN edge.
    """

    def __init__(self, link_id: int, left_node: InfraNode, right_node: InfraNode, length: float, capacity: float, modalities: list[str], direction: dict[str, DirectionType] = None) -> None:
        """
        Create a new infrastructure link

        :param link_id: the link id. Needs to be unique for a given graph
        :type link_id: int
        :param left_node: the left node of the link.
        :type left_node: InfraNode
        :param right_node: the right node of the link.
        :type right_node: InfraNode
        :param length: the length of the link
        :type length: float
        :param capacity: the capacity of the infrastructure link
        :type capacity: float
        :param modalities: list of modalities using this link
        :type modalities: list[str]
        """
        self.link_id = link_id
        self.left_node = left_node
        self.right_node = right_node
        self.length = length
        self.capacity = capacity
        self.load = 0
        self.modalities = modalities
        if not direction:
            self.direction: dict[str, DirectionType] = {}
            for modality in modalities:
                self.direction[modality] = DirectionType.BOTH
        else:
            if set(direction.keys()) != set(modalities):
                raise LinTimException(f"Can not create InfraLink! The modalities for which directions are given do not coincide with the modalities!")
            self.direction = direction

    def getId(self) -> int:
        """
        Get the id of the link

        :return: the id of the link
        :rtype: int
        """
        return self.link_id

    def setId(self, new_id: int) -> None:
        """
        Set the id of the link

        :param new_id: the id to set
        :type new_id: int
        """
        self.link_id = new_id

    def getLeftNode(self) -> InfraNode:
        """
        Get the left node

        :return: left node
        :rtype: InfraNode
        """
        return self.left_node

    def getRightNode(self) -> InfraNode:
        """
        Get the right node

        :return: right node
        :rtype: InfraNode
        """
        return self.right_node

    def isDirected(self) -> bool:
        """
        Whether the link is directed. Returns always False for an InfraLink.

        :return: whether the link is directed or not
        :rtype: bool
        """
        return False

    def getLength(self) -> float:
        """
        Get the length of the infrastructure link. When reading from file with the reader Class, this was scaled to be in kilometres.

        :return: the length in kilometres
        :rtype: float
        """
        return self.length

    def getCapacity(self) -> float:
        """
        Get the capacity of the infrastructure link

        :return: capacity of the infrastructure link
        :rtype: float
        """
        return self.capacity

    def getModalities(self) -> list[str]:
        """
        list of all modalities (given as strings) using this link

        :return: list of all modalities using this link
        :rtype: list[str]
        """
        return self.modalities

    def getModalitiesDirections(self) -> dict[str, DirectionType]:
        """
        Get the directions in which this link can be used by all modalities

        :return: dictionary of modality directions
        :rtype: dict[str, DirectionType]
        """
        return self.direction

    def getModalityDirection(self, modality: str) -> DirectionType:
        """
        Get the direction in which the given modality can use this edge.
        FORWARDS means only left node to right node
        BACKWARDS means only right node to left node
        BOTH means both directions.

        :param modality: the modality
        :type modality: str
        :return: the direction in which this modality can use the edge
        :rtype: DirectionType
        """
        return self.direction[modality]

    def setModalityDirection(self, modality: str, direction: DirectionType) -> None:
        """
        Set the direction in which the given modality can use this edge.
        FORWARDS means only left node to right node
        BACKWARDS means only right node to left node
        BOTH means both directions.

        :param modality: the modality
        :type modality: str
        :param direction: the direction in which this modality can use the edge
        :type direction: DirectionType
        """
        self.direction[modality] = direction

    def addModality(self, modality: str, direction: DirectionType) -> None:
        """
        Add a modality to the list of modalities of this link and set the corresponding direction in which this modality is allowed to use the link

        :param modality: the modality to add
        :type modality: str
        :param direction: the direction in which the modality can use the edge
        :type direction: DirectionType
        """
        self.modalities.append(modality)
        self.direction[modality] = direction

    def addModalities(self, modalities: list[str], directions: dict[str, DirectionType]) -> None:
        """
        Add a list of modalities to the list of modalities of this link and the directions, in which they can use the link

        :param modality: list of modalities to add
        :type modality: list[str]
        :param directions: the directions in which the modalities can use the edge
        :type direction: dict[str, DirectionType]
        """
        self.modalities += modalities
        for modality in modalities:
            self.direction[modality] = directions[modality]

    def getLoad(self) -> float:
        """
        Get the load of the infrastructure link

        :return: load of the infrastructure link
        :rtype: float
        """
        return self.load

    def setLoad(self, load: float) -> None:
        """
        Set the load of the infrastructure link

        :param load: load of the link
        :type load: float
        """
        self.load = load

    def toCsvStrings(self, conversion_factor_length: float = 1) -> list[str]:
        """
        Generate a list of strings to write the information about this link to a csv file.

        :param conversion_factor_length: conversion factor for the length, ensuring that it is in kilometres, defaults to 1
        :type: float, optional
        :return: the csv representation of this link
        :rtype: list[str]
        """
        return [str(self.getId()),
                str(self.getLeftNode().getId()),
                str(self.getRightNode().getId()),
                CsvWriter.shortenDecimalValueForOutput(self.getLength() / conversion_factor_length),
                CsvWriter.shortenDecimalValueForOutput(self.getCapacity()),
                '[' + ', '.join(self.getModalities()) + ']',
                '[' + ', '.join([self.direction[modality].name for modality in self.getModalities()]) + ']'
                ]

    def toCsvLoadStrings(self) -> list[str]:
        """
        Generate a list of strings to write the load information about this link to a csv file.

        :param conversion_factor_length: conversion factor for the length, ensuring that it is in kilometres, defaults to 1
        :type: float, optional
        :return: the csv representation of this link
        :rtype: list[str]
        """
        return [str(self.getId()),
                CsvWriter.shortenDecimalValueForOutput(self.getLoad())
                ]

    def __eq__(self, o: object) -> bool:
        return isinstance(o, InfraLink) and \
               (self.link_id, self.left_node, self.right_node,
                self.length, self.capacity, self.modalities, self.direction) == \
               (o.link_id, o.left_node, o.right_node, o.length, self.capacity, o.modalities, o.direction)

    def __ne__(self, o: object) -> bool:
        return not self == o

    def __hash__(self) -> int:
        return (hash(self.link_id) ^ hash(self.left_node) ^ hash(self.right_node) ^
                hash((self.link_id, self.left_node, self.right_node)))


class InfraPath(ListPath[InfraNode, InfraLink]):
    """
    Class implementing a path of infrastructure edges representing the underlying infrastructure of a ptn edge.
    Inherits from Class core.model.impl.list_path.ListPath

    The Path itself is always an undirected path, even if the PTN is directed! Each InfraLink in the path knows
    if a given modality is allowed to use the InfraLink only in forwards or only in backwards direction or in
    both directions. This class implementation ensures that the path of InfraLinks can be traversed by the given modality
    only in the allowed direction.
    """

    def __init__(self, ptn_edge: 'Link', modality: str):
        """
        Initialize an InfraPath object.

        :param ptn_edge: the PTN edge for which this path represents the underlying infrastructure
        :type ptn_edge: Link
        :param modality: the modality of the PTN where the edge belongs to
        :type modality: str
        """
        super().__init__(directed=False)

        self.ptn_edge = ptn_edge
        self.modality = modality


    def addLastEdge(self, edge: InfraLink) -> bool:
        """
        Add an infrastructure edge at the end of this infrastructure path. It is checked, whether the DirectionType
        of the edge to add is consistent with the path so far and the directedness of the PTN edge.

        :param edge: infrastructure link to add
        :type edge: InfraLink
        :raises DataPTNInfrastructureMapModalityException: If the modality is not supported on the infrastructure link
        :raises DataPTNInfrastructureMapDirectionException: If the modalities direction on the infrastructure link is incompatible with the directedness of the PTN or with the yet existing path
        :return: whether it was added succesfully
        :rtype: bool
        """

        if self.modality not in edge.getModalities():
            raise DataPTNInfrastructureMapModalityException(self.modality, edge.getId())

        direction = edge.getModalityDirection(self.modality)
        if self.ptn_edge.isDirected():

            # if path is empty, just continue
            if self.getEdges() == []:
                pass
            else: # path is not empty
                # get last edge of path and its direction
                current_last_edge = self.getEdges()[-1]
                last_direction = current_last_edge.getModalityDirection(self.modality)

                # depending on direction of current last path edge and direction of edge to add, two sepcific nodes of those edges must be equal
                # in order to form a valid path where the directions are correct when the new edge is added.
                if direction == DirectionType.FORWARDS:
                    if last_direction == DirectionType.FORWARDS:
                        if current_last_edge.getRightNode() != edge.getLeftNode():
                            raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())
                    if last_direction == DirectionType.BACKWARDS:
                        if current_last_edge.getLeftNode() != edge.getLeftNode():
                            raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())
                elif direction == DirectionType.BACKWARDS:
                    if last_direction == DirectionType.FORWARDS:
                        if current_last_edge.getRightNode() != edge.getRightNode():
                            raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())
                    if last_direction == DirectionType.BACKWARDS:
                        if current_last_edge.getLeftNode() != edge.getRightNode():
                            raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())
                else: # direction == DirectionType.BOTH
                    # edge can be added
                    pass
        else: # ptn is undirected
            # Only DirectionType BOTH is allowed. Don't care about direction of other edges.
            if direction != DirectionType.BOTH:
                raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())

        success = super().addFirstEdge(edge)
        if success:
            if not self.checkEndpointsConsistency():
                self.reverse()

        return success


    def addLast(self, edges: list[InfraLink]) -> bool:
        """
        Add a list of edges to the end of the path. Uses InfraPath.addLastEdge and checks, whether the DirectionType
        of the edges to add is consistent to form a path and the directedness of the PTN edge.

        :param edges: List of infrastructure links to add
        :type edges: list[InfraLink]
        :return: whether the edges were added successfully
        :rtype: bool
        """
        succeeded = True
        failed_element = None
        for edge in edges:
            succeeded = succeeded and self.addLastEdge(edge)
            if not succeeded:
                failed_element = edge
                break
        if not succeeded:
            self.reset_path(edges, failed_element)
        return succeeded


    def addFirstEdge(self, edge: InfraLink) -> bool:
        """
        Add an infrastructure link at the beginning of this infrastructure path. It is checked, whether the DirectionType
        of the edge to add is consistent with the path so far and the directedness of the ptn edge.

        :param edge: infrastructure link to add
        :type edge: InfraLink
        :raises DataPTNInfrastructureMapModalityException: If the modality is not supported on the infrastructure link
        :raises DataPTNInfrastructureMapDirectionException: If the modalities direction on the infrastructure link is incompatible with the directedness of the PTN or with the yet existing path
        :return: whether it was added succesfully
        :rtype: bool
        """

        if self.modality not in edge.getModalities():
            raise DataPTNInfrastructureMapModalityException(self.modality, edge.getId())

        direction = edge.getModalityDirection(self.modality)
        if self.ptn_edge.isDirected():

            # if path is empty, just add the edge
            if self.getEdges() != []:
                # path is not empty
                # get first edge of path and its direction
                current_first_edge = self.getEdges()[0]
                first_direction = current_first_edge.getModalityDirection(self.modality)

                # depending on direction of current first path edge and direction of edge to add, two sepcific nodes of those edges must be equal
                # in order to form a valid path where the directions are correct when the new edge is added.
                if direction == DirectionType.FORWARDS:
                    if first_direction == DirectionType.FORWARDS:
                        if current_first_edge.getLeftNode() != edge.getRightNode():
                            raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())
                    if first_direction == DirectionType.BACKWARDS:
                        if current_first_edge.getRightNode() != edge.getRightNode():
                            raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())
                elif direction == DirectionType.BACKWARDS:
                    if first_direction == DirectionType.FORWARDS:
                        if current_first_edge.getLeftNode() != edge.getLeftNode():
                            raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())
                    if first_direction == DirectionType.BACKWARDS:
                        if current_first_edge.getRightNode() != edge.getLeftNode():
                            raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())
                else: # direction == DirectionType.BOTH
                    # a direction of BOTH is fine
                    pass
        else: # ptn is undirected
            if direction != DirectionType.BOTH:
                raise DataPTNInfrastructureMapDirectionException(self.ptn_edge.getId(), self.modality, direction, edge.getId())

        success = super().addFirstEdge(edge)
        if success:
            if not self.checkEndpointsConsistency():
                self.reverse()

        return success


    def addFirst(self, edges: list[InfraLink]) -> bool:
        """
        Add a list of edges to the beginning of the path. Uses InfraPath.addFirstEdge and checkes, whether the DirectionType
        of the edges to add is consistent to form a path and the directedness of the ptn edge.

        :param edges: List of infrastructure links to add
        :type edges: list[InfraLink]
        :return: whether the edges were added successfully
        :rtype: bool
        """
        insert_list = list(edges)
        insert_list.reverse()
        succeeded = True
        failed_element = None
        for edge in insert_list:
            succeeded = succeeded and self.addFirstEdge(edge)
            if not succeeded:
                failed_element = edge
                break
        if not succeeded:
            self.reset_path(insert_list, failed_element)
        return succeeded


    def checkEndpointsConsistency(self) -> bool:
        """
        Check, whether the start and end nodes of the path match with the start and end stop of the corresponding PTN edge.

        :return: whether the endpoints are consistent with the PTN edge or not
        :rtype: bool
        """

        # check, if the endpoints are the same as for the ptn edge:
        correct_nodes = (self.ptn_edge.getLeftNode().getId() == self.getNodes()[0].getId() and self.ptn_edge.getRightNode().getId() == self.getNodes()[-1].getId()) or (self.ptn_edge.getLeftNode().getId() == self.getNodes()[-1].getId() and self.ptn_edge.getRightNode().getId() == self.getNodes()[0].getId())

        # if the ptn is directed, check if the path goes in the right direction:
        if self.ptn_edge.isDirected():
            # first, check if first node of path is left node of ptn edge and if last node of path is right node of ptn edge
            left_node_match = self.ptn_edge.getLeftNode().getId() == self.getNodes()[0].getId()
            right_node_match = self.ptn_edge.getRightNode().getId() == self.getNodes()[-1].getId()

            # Now, check if direction FORWARDS and BACKWARDS match. During the adding of an edge, we already checked that all
            # infra edges form a consitent path with the FORWARDS and BACKWARDS directions. We still have  to check, if this matches
            # with the direction of the PTN edge.
            # get first edge of path:
            first_edge = self.getEdges()[0]
            if first_edge.getModalityDirection(self.modality) == DirectionType.BOTH:
                correct_direction = True
            elif first_edge.getModalityDirection(self.modality) == DirectionType.FORWARDS:
                correct_direction = first_edge.getLeftNode().getId() == self.ptn_edge.getLeftNode().getId()
            else: # direction is BACKWARDS
                correct_direction = first_edge.getRightNode().getId() == self.ptn_edge.getLeftNode().getId()
            return correct_nodes and left_node_match and right_node_match and correct_direction
        else:
            return correct_nodes


    def checkPathLengthConsistency(self) -> bool:
        """
        Check, whether the length of the PTN edge equals the sum of the lenths of the infrastructure links in the path

        :return: whether the length of the PTN edge equals the length of the path
        :rtype: bool
        """
        return self.ptn_edge.getLength() == sum([e.getLength() for e in self.getEdges()])


    def checkLowerBoundConsistency(self, speed: float) -> bool:
        """
        Check, whether the length of the infrastructure path can be travelled with the given speed in the lower time bound of the PTN edge.

        :param speed: speed of a vehicle of this modality
        :type speed: float
        :return: whether the length of the infrastructure path can be travelled with the given speed in the lower time bound of the PTN edge
        :rtype: bool
        """
        return self.ptn_edge.getLowerBound() >= 60 * sum([e.getLength() for e in self.getEdges()])/speed


    def isDirected(self):
        """
        Whether the PTN edge for which this path represents the underlying infrastructure is directed or not.
        The actual path itself is always a path of undirected InfraLinks!

        :return: Whether the PTN edge for which this path represents the underlying infrastructure is directed or not
        :rtype: bool
        """
        return self.ptn_edge.isDirected()
