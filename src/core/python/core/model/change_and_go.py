from enum import Enum
from core.model.graph import Node, Edge
import math

class CGArcType(Enum):
    """
    Enumeration of all possible edge types in a Change&Go Network.
    """
    LINE = "\"line\""
    TRANSFER = "\"transfer\""
    BOARD = '"board"'
    ALIGHT = '"alight"'

class CGNodeType(Enum):
    LINE_STOP = "LINE_STOP"
    ORIGIN = "ORIGIN"
    DESTINATION = "DESTINATION"
    PLATFORM = "PLATFORM"


class CGNode(Node):
    """
    A class representing a node in a Change&Go Network.
    A  node contains line and stop information corrensponding to the underlying line pool.
    Line id is set to 0 for origin or destination nodes.
    """
    def __init__(self, node_id: int, stop_id: int, line_id: int,
                 type: CGNodeType) -> None:
        self.id = node_id
        self.stop_id = stop_id
        self.line_id = line_id  # set to 0 for origin or destination nodes
        self.type = type

    def getId(self):
        return self.id

    def getStopId(self):
        return self.stop_id

    def setId(self, stop_id: int):
        self.stop_id = stop_id

    def getLineId(self):
        return self.line_id

    def setLineId(self, line_id: int):
        self.line_id = line_id

    def getType(self):
        return self.type

    def isOrigin(self) -> bool:
        return self.type in [CGNodeType.PLATFORM, CGNodeType.ORIGIN]

    def isDestination(self) -> bool:
        return self.type in [CGNodeType.PLATFORM, CGNodeType.DESTINATION]

    def toCsvStrings(self):
        return [str(self.getId()), str(self.getStopId()), str(self.getLineId())]

    def __str__(self):
        return "C&G-Node " + ", ".join(
            [str(self.getId()), str(self.getStopId()), str(self.getLineId())])

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, CGNode):
            return False
        if not self.getId() == other.getId():
            return False
        if not self.getStopId() == other.getStopId():
            return False
        if not self.getLineId() == other.getLineId():
            return False
        return True

    def __ne__(self, other: object):
        return not self.__eq__(other)

    def __hash__(self):
        return hash((self.id, self.stop_id, self.line_id))


class CGEdge(Edge[CGNode]):
    """
    A class representing an edge in a Change&Go Network.
    An edge has a Change&Go-Type as well as information associated with a Change&Go Network,
    i.e. passenger load, cost as well as structural information.
    The passenger load is initialised to 0.
    """
    def __init__(self, edge_id: int, left_node: CGNode, right_node: CGNode,
                 cost: float, type: CGArcType, directed: bool=False) -> None:
        self.id = edge_id
        self.left_node = left_node
        self.right_node = right_node
        self.cost = cost
        self.type = type
        self.directed = directed
        self.load = 0

    def getId(self):
        return self.id

    def setId(self, new_id: int):
        self.id = new_id

    def getLeftNode(self):
        return self.left_node

    def getRightNode(self):
        return self.right_node

    def getCost(self):
        return self.cost

    def getType(self):
        return self.type

    def isDirected(self):
        return self.directed

    def setDirected(self, directed: bool):
        self.directed = directed

    def setLoad(self, load: float):
        self.load = load

    def getLoad(self):
        return self.load

    def toCsvStrings(self):
        return [str(self.getId()), str(self.getLeftNode().getId()), str(self.getRightNode().getId()), str(self.getCost()),
             str(self.getType().value)]

    def __str__(self):
        return "C&G-Edge " + ", ".join(
            [str(self.getId()), str(self.getLeftNode().getId()), str(self.getRightNode().getId()), str(self.getCost()),
             str(self.getType().value)])

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, CGEdge):
            return False
        if not self.type == other.getType():
            return False
        if not self.getId() == other.getId():
            return False
        if not math.isclose(self.getCost(), other.getCost()):
            return False
        if not math.isclose(self.getLoad(), other.getLoad()):
            return False
        if not self.isDirected() == other.isDirected():
            return False
        # Check for the directed attribute and compare the edges
        if self.isDirected():
            return (self.getLeftNode() == other.getLeftNode()
                    and self.getRightNode() == other.getRightNode())
        else:
            return ((self.getLeftNode() == other.getLeftNode()
                     and self.getRightNode() == other.getRightNode())
                    or (self.getLeftNode() == other.getRightNode()
                        and self.getRightNode() == other.getLeftNode()))

    def __ne__(self, other: object):
        return not self.__eq__(other)

    def __hash__(self):
        return hash((self.id, self.left_node, self.right_node, self.cost, self.load, self.type, self.directed))
