import math
from enum import Enum

from core.model.ptn import Link, Stop


class MTType(Enum):
    """
    Enumeration of all possible edge types in a Multimodal Transfer Network.
    """
    LINE = 'line'
    TRANSFER = 'transfer'
    # in python 3.11 these could be replaced with StrEnum and Enum.auto().


class MTNode(Stop):
    """
    A class representing a node in a Multimodal Transfer Network.
    A node contains modality and stop information corrensponding to the
    underlying multimodal PTN.
    """
    def __init__(self, *, node_id: int, **kwargs) -> None:
        super().__init__(**kwargs)
        self.node_id = node_id

    def getId(self):
        return self.node_id

    def getStopId(self):
        return self.stop_id

    def setId(self, node_id: int):
        self.node_id = node_id

    def setStopId(self, stop_id: int):
        self.stop_id = stop_id

    def __str__(self):
        return 'MT-Node ' + ', '.join(
            [str(self.getId()), str(self.getStopId())],
        )

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, MTNode):
            return False
        # Assume, that node ids are globally unique for all compared nodes.
        # Works perfectly well if the nodes are part of the same graph, as this
        # guarantees unique node ids.
        return self.node_id == other.node_id

    def __ne__(self, other: object):
        return not self.__eq__(other)

    def __hash__(self) -> int:
        return hash((self.node_id, super().__hash__()))


class MTEdge(Link):
    """
    A class representing an edge in a Multimodal Transfer Network.
    An edge has a Multimodal Transfer-Type as well as information associated
    with a Multimodal Transfer Network, i.e. passenger load, weight as well as
    structural information. The passenger load is initialised to 0.
    """
    def __init__(
            self,
            left_stop: MTNode,
            right_stop: MTNode,
            edge_type: MTType,
            **kwargs,
    ) -> None:
        super().__init__(left_stop=left_stop, right_stop=right_stop, **kwargs)
        self.left_stop = left_stop
        self.right_stop = right_stop
        self.type = edge_type
        self.load = 0

    def getLeftNode(self) -> MTNode:
        return self.left_stop

    def getRightNode(self) -> MTNode:
        return self.right_stop

    def getType(self):
        return self.type

    def setDirected(self, directed: bool):
        self.directed = directed

    def setLoad(self, load: float):
        self.load = load

    def getLoad(self):
        return self.load

    def toCsvStrings(self):
        return [
            str(self.getId()),
            str(self.getLeftNode().getId()),
            str(self.getRightNode().getId()),
            str(self.getType().value),
        ]

    def __str__(self):
        return 'MT-Edge ' + ', '.join(
            [
                str(self.getId()),
                str(self.getLeftNode().getId()),
                str(self.getRightNode().getId()),
                str(self.getType().value),
            ],
        )

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, MTEdge):
            return False
        if not self.type == other.getType():
            return False
        if not self.getId() == other.getId():
            return False
        if not math.isclose(self.getLoad(), other.getLoad()):
            return False
        if not self.isDirected() == other.isDirected():
            return False
        # Check for the directed attribute and compare the edges
        if self.isDirected():
            return (
                self.getLeftNode() == other.getLeftNode()
                and self.getRightNode() == other.getRightNode()
            )
        else:
            return (
                (
                    self.getLeftNode() == other.getLeftNode()
                    and self.getRightNode() == other.getRightNode()
                ) or (
                    self.getLeftNode() == other.getRightNode()
                    and self.getRightNode() == other.getLeftNode()
                )
            )

    def __ne__(self, other: object):
        return not self.__eq__(other)

    def __hash__(self):
        return hash(
            (
                self.link_id,
                self.left_stop,
                self.right_stop,
                self.load,
                self.type,
                self.directed,
            ),
        )
