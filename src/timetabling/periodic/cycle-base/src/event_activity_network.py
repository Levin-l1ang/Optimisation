import logging

from typing import Callable
import networkx as nx

from core.model.graph import Graph
from core.model.periodic_ean import PeriodicActivity, PeriodicEvent

from core.util.networkx import convert_graph_to_networkx

logger_ = logging.getLogger(__name__)


class PeriodicEventActivityNetwork:
    """
    A first try implementation of an event-activity network
    """

    def __init__(self, graph: Graph[PeriodicEvent, PeriodicActivity], period_length: int,
                 weight_function: Callable[[PeriodicActivity], float]):
        self.graph = graph
        self.period_length = period_length
        self.weight_function = weight_function
        self.edge_table = {(e.getLeftNode().getId(), e.getRightNode().getId()): e for e in graph.getEdges()}
        self.span = {}

        self.nx_graph = nx.DiGraph()
        self.nx_spanning_tree = nx.Graph()

        self.tensions = {}

        self.cycle_base = []
        self.network_matrix_dict = {}

    def calculate_span(self):
        self.span = {e: (e.getUpperBound() - e.getLowerBound()) for e in self.graph.getEdges()}

    def calculate_nx_graph(self):
        self.nx_graph = convert_graph_to_networkx(graph=self.graph, multi_graph=False,
                                                  weight_function=self.weight_function)

    def calculate_nx_spanning_tree(self):
        self.nx_spanning_tree = nx.minimum_spanning_tree(self.nx_graph.to_undirected())

    def calculate_cycle_base(self):
        logger_.info("Calculating cycle base")

        for i, edge in enumerate(self.graph.getEdges()):
            # We iterate only over the edges in the graph that are not in the tree
            if (edge.getLeftNode().getId(), edge.getRightNode().getId()) in self.nx_spanning_tree.edges() or \
                (edge.getRightNode().getId(), edge.getLeftNode().getId()) in self.nx_spanning_tree.edges():
                continue

            path_edges = [(edge.getLeftNode().getId(), edge.getRightNode().getId())]
            incidence_vector = {edge: 1}

            path = nx.shortest_path(self.nx_spanning_tree, edge.getRightNode().getId(), edge.getLeftNode().getId())

            prev_node = path[0]
            for node in path[1:]:
                path_edges.append((prev_node, node))

                if (prev_node, node) in self.edge_table.keys():
                    tmp = self.edge_table[(prev_node, node)]
                    incidence_vector[tmp] = 1
                else:
                    tmp = self.edge_table[(node, prev_node)]
                    incidence_vector[tmp] = -1

                prev_node = node

            self.cycle_base.append(path_edges)
            self.network_matrix_dict[edge] = incidence_vector

    # method for calculating times of the events from tensions
    def get_time_from_tensions(self):
        used = {node: False for node in self.graph.getNodes()}

        for node in used.keys():
            if used[node]:
                continue
            node.time = 0
            used[node] = True
            self.get_time_from_tensions_bfs(node, used)

    # breadth first search for calculating times of events from tension
    def get_time_from_tensions_bfs(self, node, used):
        queue = [node]
        while len(queue) > 0:
            cur = queue.pop(0)

            for edge in self.graph.getIncidentEdges(cur):
                if edge.getLeftNode() == cur:
                    next_node = edge.getRightNode()
                    if not used[next_node]:
                        queue.append(next_node)
                        used[next_node] = True
                        next_node.time = int((cur.time + self.tensions[edge]) % self.period_length)
                else:
                    next_node = edge.getLeftNode()
                    if not used[next_node]:
                        queue.append(next_node)
                        used[next_node] = True
                        next_node.time = int((cur.time - self.tensions[edge]) % self.period_length)
