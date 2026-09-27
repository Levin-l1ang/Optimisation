from typing import ValuesView, Dict
from typing import List

from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.ptn import Stop, Link


class Ptn:
    def __init__(self) -> None:
        self.graph: Graph[Stop, Link] = SimpleDictGraph()
        self.backward_edges: Dict[Link, Link] = {}

    def add_node(self, node: Stop) -> None:
        self.graph.addNode(node)

    def add_edge(self, edge: Link, backward_edge: Link = None) -> None:
        self.graph.addEdge(edge)
        self.backward_edges[edge] = backward_edge
        if not edge.isDirected() and not backward_edge:
            backward_edge = Link(-edge.getId(), edge.getRightNode(), edge.getLeftNode(), edge.getLength(),
                                 edge.getLowerBound(), edge.getUpperBound(), True)
            self.backward_edges[edge] = backward_edge
            self.add_edge(backward_edge, edge)

    def get_nodes(self) -> List[Stop]:
        return self.graph.getNodes()

    def get_node(self, node_id: int) -> Stop:
        return self.graph.getNode(node_id)

    def get_edges(self) -> List[Link]:
        return self.graph.getEdges()

    def get_edge(self, edge_id: int) -> Link:
        return self.graph.getEdge(edge_id)

    def get_edge_between(self, source: Stop, target: Stop):
        edge = next(iter([e for e in self.graph.getOutgoingEdges(source) if e.getRightNode() == target]), None)
        if not edge and not self.graph.isDirected():
            edge = next(iter([e for e in self.graph.getIncomingEdges(source) if e.getLeftNode() == target]), None)
        return edge

    def get_backward_edge(self, link: Link) -> Link:
        return self.backward_edges[link]