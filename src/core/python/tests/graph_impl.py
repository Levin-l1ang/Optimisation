from core.model.graph import Node, Edge, N


class AbstractNode(Node):

    def __init__(self, node_id: int):
        self.node_id = node_id

    def getId(self):
        return self.node_id

    def setId(self, new_id: int):
        self.node_id = new_id

    def __hash__(self) -> int:
        return self.node_id

    def __eq__(self, o: object) -> bool:
        if not isinstance(o, AbstractNode):
            return False
        return o.getId() == self.getId()

    def __ne__(self, other):
        return not self.__eq__(other)

    def __str__(self):
        return "AbstractNode {}".format(self.node_id)


class AbstractEdge(Edge[AbstractNode]):

    def __init__(self, edge_id: int, left_node: AbstractNode, right_node: AbstractNode):
        self.edge_id = edge_id
        self.left_node = left_node
        self.right_node = right_node

    def getId(self) -> int:
        return self.edge_id

    def setId(self, new_id: int) -> None:
        self.edge_id = new_id

    def getLeftNode(self) -> N:
        return self.left_node

    def getRightNode(self) -> N:
        return self.right_node

    def isDirected(self) -> bool:
        return True

    def __hash__(self) -> int:
        return self.edge_id

    def __eq__(self, o: object) -> bool:
        if not isinstance(o, AbstractEdge):
            return False
        return o.getId() == self.getId()

    def __ne__(self, other):
        return not self.__eq__(other)

    def __str__(self):
        return "AbstractEdge {}".format(self.edge_id)
