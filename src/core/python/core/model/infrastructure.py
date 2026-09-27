from typing import List

from core.io.csv import CsvWriter
from core.model import graph
from core.model.graph import N


class InfrastructureNode(graph.Node):
    """
    ==!! This Class is deprecated !! Should no longer be used !!==

    Class reprenting a node in the infrastructure network. A node is the smallest possible unit, may represent a
    stop, a demand point, an intersection, ...
    """

    def __init__(self, node_id: int, name: str, x_coord: float, y_coord: float, stop_possible: bool) -> None:
        """
        ==!! This Class is deprecated !! Should no longer be used !!==

        Create a new node.
        :param node_id: the id of node. Needs to be unique for a given graph
        :param x_coord: the x coordinate of the node
        :param y_coord: the y coordinate of the node
        :param stop_possible: whether it is possible to build a stop at the given node
        """
        self.node_id = node_id
        self.name = name
        self.x_coord = x_coord
        self.y_coord = y_coord
        self.stop_possible = stop_possible

    def getId(self) -> int:
        return self.node_id

    def setId(self, new_id: int) -> None:
        self.node_id = new_id

    def getName(self) -> str:
        return self.name

    def getXCoordinate(self) -> float:
        """
        Get the x coordinate of the node
        :return: the x coordinate
        """
        return self.x_coord

    def getYCoordinate(self) -> float:
        """
        Get the y coordinate of the node
        :return: the y coordinate
        """
        return self.y_coord

    def toCsvStrings(self, conversion_factor_coordinates: float = 1) -> List[str]:
        if self.stop_possible:
            stop = "true"
        else:
            stop = "false"
        return [str(self.getId()), self.name, CsvWriter.shortenDecimalValueForOutput(self.x_coord / conversion_factor_coordinates),
                CsvWriter.shortenDecimalValueForOutput(self.y_coord / conversion_factor_coordinates), stop]

    def __eq__(self, o: object) -> bool:
        return isinstance(o, InfrastructureNode) and \
               (self.node_id, self.x_coord, self.y_coord, self.stop_possible) == \
               (o.node_id, o.x_coord, o.y_coord, o.stop_possible)

    def __ne__(self, o: object) -> bool:
        return not self == o

    def __hash__(self) -> int:
        return (hash(self.node_id) ^ hash(self.x_coord) ^ hash(self.y_coord) ^
                hash((self.node_id, self.x_coord, self.y_coord)))


class InfrastructureEdge(graph.Edge[InfrastructureNode]):
    """
    ==!! This Class is deprecated !! Should no longer be used !!==

    Class representing an infrastructure edge. These are the possible edges in the network, i.e., possible direct
    connections between two nodes. They may be directed or undirected. Not all infrastructure edges need to be present
    in the final public transport network, dependend on the chosen stops.
    """

    def __init__(self, edge_id: int, left_node: InfrastructureNode, right_node: InfrastructureNode, length: float, lower_bound: int,
                 upper_bound: int, directed: bool) -> None:
        """
        ==!! This Class is deprecated !! Should no longer be used !!==

        Create a new infrastructure edge
        :param edge_id: the edge id. Needs to be unique for a given graph
        :param left_node: the left node of the edge. This is the source of the edge if it is directed
        :param right_node: the right node of the edge. This is the target of the edge if it is directed
        :param length: the length of the edge
        :param lower_bound: the lower bound of the edge, i.e., the minimal time needed to traverse this edge
        :param upper_bound: the upper hound of the edge, i.e., the maximal time allowed to traverse this edge
        :param directed: whether this edge is directed
        """
        self.edge_id = edge_id
        self.left_node = left_node
        self.right_node = right_node
        self.length = length
        self.lower_bound = lower_bound
        self.upper_bound = upper_bound
        self.directed = directed

    def getId(self) -> int:
        return self.edge_id

    def setId(self, new_id: int) -> None:
        self.edge_id = new_id

    def getLeftNode(self) -> N:
        return self.left_node

    def getRightNode(self) -> N:
        return self.right_node

    def isDirected(self) -> bool:
        return self.directed

    def getLength(self) -> float:
        """
        Get the length of the infrastructure edge
        :return: the length
        """
        return self.length

    def getLowerBound(self) -> int:
        """
        Get the lower bound of the infrastructure edge, i.e., the minimal time to traverse the edge
        :return:  the lower bound
        """
        return self.lower_bound

    def getUpperBound(self) -> int:
        """
        Get the upper bound of the infrastructure edge, i.e., the maximal time allowed to traverse the edge
        :return:  the upper bound
        """
        return self.upper_bound

    def setLowerBound(self, lower_bound: int) -> None:
        """
        Set a new lower bound for the edge, i.e., a new minimal time to traverse the edge
        :param lower_bound: the new lower bound
        """
        self.lower_bound = lower_bound

    def setUpperBound(self, upper_bound: int) -> None:
        """
        Set a new upper bound for the edge, i.e., a new maximal time allowed to traverse the edge
        :param upper_bound: the new upper bound
        """
        self.upper_bound = upper_bound

    def toCsvStrings(self, conversion_factor_length: float = 1) -> List[str]:
        """
        Return a string list, representing the link for a LinTim csv file.
        :return: the csv representation of this link
        """
        return [str(self.getId()),
                str(self.getLeftNode().getId()),
                str(self.getRightNode().getId()),
                CsvWriter.shortenDecimalValueForOutput(self.getLength() / conversion_factor_length),
                str(self.getLowerBound()),
                str(self.getUpperBound())]

    def __eq__(self, o: object) -> bool:
        return isinstance(o, InfrastructureEdge) and \
               (self.edge_id, self.left_node, self.right_node, self.directed,
                self.length, self.lower_bound, self.upper_bound) == \
               (o.edge_id, o.left_node, o.right_node, o.directed, o.length, o.lower_bound, o.upper_bound)

    def __ne__(self, o: object) -> bool:
        return not self == o

    def __hash__(self) -> int:
        return (hash(self.edge_id) ^ hash(self.left_node) ^ hash(self.right_node) ^
                hash((self.edge_id, self.left_node, self.right_node)))


class WalkingEdge(graph.Edge[InfrastructureNode]):
    """
    ==!! This Class is deprecated !! Should no longer be used !!==

    Class representing a walking edge. These are the possible walking paths in the network, i.e., possible direct
    connections between two nodes that can be used by passengers. They may be directed or undirected.
    """

    def __init__(self, edge_id: int, left_node: InfrastructureNode, right_node: InfrastructureNode, time: int, directed: bool) \
            -> None:
        """==!! This Class is deprecated !! Should no longer be used !!=="""
        self.edge_id = edge_id
        self.left_node = left_node
        self.right_node = right_node
        self.time = time
        self.directed = directed

    def getId(self) -> int:
        return self.edge_id

    def setId(self, new_id: int) -> None:
        self.edge_id = new_id

    def getLeftNode(self) -> N:
        return self.left_node

    def getRightNode(self) -> N:
        return self.right_node

    def isDirected(self) -> bool:
        return self.directed

    def getTime(self) -> float:
        return self.time

    def setTime(self, time: float) -> None:
        self.time = time

    def toCsvStrings(self) -> List[str]:
        """
        Return a string list, representing the link for a LinTim csv file.
        :return: the csv representation of this link
        """
        return [str(self.getId()),
                str(self.getLeftNode().getId()),
                str(self.getRightNode().getId()),
                str(self.getTime())]

    def __eq__(self, o: object) -> bool:
        return isinstance(o, WalkingEdge) and \
               (self.edge_id, self.left_node, self.right_node, self.directed, self.time) == \
               (o.edge_id, o.left_node, o.right_node, o.directed, o.time)

    def __hash__(self) -> int:
        return (hash(self.edge_id) ^ hash(self.left_node) ^ hash(self.right_node) ^
                hash((self.edge_id, self.left_node, self.right_node)))

    def __ne__(self, o: object) -> bool:
        return not self == o
