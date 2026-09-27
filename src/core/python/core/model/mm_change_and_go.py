
from enum import Enum
from core.model.graph import Node, Edge
import math

class MMCGArcType(Enum):
    """
    Enumeration of all possible edge types in a Multimodal Change&Go Network.
    """
    LINE = "\"line\""
    RIDEPOOLING = "\"ridepooling\""
    NONSCHEDULED = "\"nonscheduled\""
    TRANSFER = "\"transfer\""
    MODE_TRANSFER = "\"mode_transfer\""
    BOARD = "\"board\""
    ALIGHT = "\"alight\""

class MMCGNodeType(Enum):
    LINE = "LINE"
    RIDEPOOLING = "RIDEPOOLING"
    NONSCHEDULED = "NONSCHEDULED"
    ORIGIN = "ORIGIN"
    DESTINATION = "DESTINATION"
    PLATFORM = "PLATFORM"
    STATION = "STATION"

class MMCGNode(Node):
    """
    A class representing a node in a Multimodal Change&Go Network.
    A  node contains line/area, stop and modality information corrensponding to the underlying line/ridepooling area pool.
    Line/area id is set to 0 for nodes which are not in this respective modality.
    """
    def __init__(self, *, node_id: int, stop_id: int, line_id: int = 0, area_id: int = 0 , type: MMCGNodeType, modality: str = None) -> None:
        self.id = node_id
        self.stop_id = stop_id
        self.line_id = line_id
        self.area_id = area_id
        self.type = type
        self.modality = modality

    def getId(self):
        return self.id

    def getStopId(self):
        return self.stop_id

    def setId(self, node_id: int):
        self.id = node_id

    def setStopId(self, stop_id: int):
        self.stop_id = stop_id

    def getLineId(self):
        return self.line_id

    def setLineId(self, line_id: int):
        self.line_id = line_id

    def getAreaId(self):
        return self.area_id

    def setAreaId(self, area_id: int):
        self.area_id = area_id

    def getType(self):
        return self.type

    def isOrigin(self) -> bool:
        return self.type in [MMCGNodeType.PLATFORM, MMCGNodeType.ORIGIN]

    def isDestination(self) -> bool:
        return self.type in [MMCGNodeType.PLATFORM, MMCGNodeType.DESTINATION]

    def getModality(self):
        return self.modality

    def setModality(self, modality: str):
        self.modality = modality

    def toCsvStrings(self):
        return [str(self.getId()), str(self.getStopId()), str(self.getLineId()), str(self.getAreaId()), str(self.getModality())]

    def __str__(self):
        return "Multimodal C&G-Node " + ", ".join([str(self.getId()), str(self.getType().value), str(self.getStopId()), str(self.getLineId()), str(self.getAreaId()), str(self.getModality())])

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, MMCGNode):
            return False
        if not self.getId() == other.getId():
            return False
        if not self.getStopId() == other.getStopId():
            return False
        if not self.getLineId() == other.getLineId():
            return False
        if not self.getAreaId() == other.getAreaId():
            return False
        if not self.getModality() == other.getModality():
            return False
        return True

    def __ne__(self, other: object):
        return not self.__eq__(other)

    def __hash__(self):
        return hash((self.id, self.stop_id, self.line_id, self.area_id, self.modality))


class MMCGEdge(Edge[MMCGNode]):
    """
    A class representing an edge in a Multimodal Change&Go Network.
    An edge has a Multimodal Change&Go-Type as well as information associated with a Multimodal Change&Go Network,
    i.e. passenger load, cost as well as structural information.
    The passenger load is initialised to 0.
    """
    def __init__(self, link_id: int, left_node: MMCGNode, right_node: MMCGNode,
                 cost: float, type: MMCGArcType, directed: bool=True) -> None:
        self.id = link_id
        self.left_node = left_node
        self.right_node = right_node
        self.cost = cost
        self.type: MMCGArcType = type
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

    def setCost(self, cost: float):
        self.cost = cost

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
        return "Multimodal C&G-Edge " + ", ".join(
            [str(self.getId()), str(self.getLeftNode().getId()), str(self.getRightNode().getId()), str(self.getCost()),
             str(self.getType().value)])

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, MMCGEdge):
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
        return hash((self.id, self.right_node, self.right_node, self.cost, self.load, self.type, self.directed))
