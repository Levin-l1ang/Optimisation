import networkx as nx

from core.model.ptn import Stop
from ptn_data import Ptn


class TurnaroundData:

    def __init__(self, ptn: Ptn, vs_depot_index: int, vs_turn_over_time: int):
        self.min_turnaround_time = {}
        self.min_turnaround_distance = {}
        self.min_time_from_depot = {}
        self.min_time_to_depot = {}
        self.min_distance_from_depot = {}
        self.min_distance_to_depot = {}
        self.compute(ptn, vs_depot_index, vs_turn_over_time)

    def get_min_turnaround_distance(self, start: Stop, end: Stop) -> float:
        return self.min_turnaround_distance[(start, end)]

    def get_min_turnaround_time(self, start: Stop, end: Stop) -> int:
        return self.min_turnaround_time[(start, end)]

    def get_min_from_depot_time(self, node: Stop) -> int:
        try:
            return self.min_time_from_depot[node]
        except KeyError:
            # This may happen when we dont have a depot, then the corresponding dict is not initialized. Just return 0.
            return 0

    def get_min_to_depot_time(self, node: Stop) -> int:
        try:
            return self.min_time_to_depot[node]
        except KeyError:
            # This may happen when we dont have a depot, then the corresponding dict is not initialized. Just return 0.
            return 0

    def get_min_from_depot_distance(self, node: Stop) -> float:
        try:
            return self.min_distance_from_depot[node]
        except KeyError:
            # This may happen when we dont have a depot, then the corresponding dict is not initialized. Just return 0.
            return 0

    def get_min_to_depot_distance(self, node: Stop) -> float:
        try:
            return self.min_distance_to_depot[node]
        except KeyError:
            # This may happen when we dont have a depot, then the corresponding dict is not initialized. Just return 0.
            return 0

    def compute(self, ptn: Ptn, vs_depot_index: int, vs_turn_over_time: int) -> None:
        ptn_graph = nx.DiGraph()
        for ptn_node in ptn.get_nodes():
            ptn_graph.add_node(ptn_node.getId())
        for ptn_edge in ptn.get_edges():
            ptn_graph.add_edge(ptn_edge.getLeftNode().getId(), ptn_edge.getRightNode().getId(),
                               lower_bound_time=ptn_edge.getLowerBound(), length=ptn_edge.getLength())
        shortest_paths = dict(nx.all_pairs_dijkstra_path(ptn_graph, weight="lower_bound_time"))
        for origin in ptn.get_nodes():
            for destination in ptn.get_nodes():
                min_turn_around_time = 0
                min_turn_around_distance = 0
                for i in range(0,shortest_paths[origin.getId()][destination.getId()].__len__()-1):
                    left_node = shortest_paths[origin.getId()][destination.getId()][i]
                    right_node = shortest_paths[origin.getId()][destination.getId()][i+1]
                    min_turn_around_time += ptn_graph[left_node][right_node]["lower_bound_time"]
                    min_turn_around_distance += ptn_graph[left_node][right_node]["length"]
                self.min_turnaround_time[(origin, destination)] = min_turn_around_time + vs_turn_over_time
                self.min_turnaround_distance[(origin, destination)] = min_turn_around_distance
                if origin.getId() == vs_depot_index:
                    self.min_time_from_depot[destination] = min_turn_around_time
                    self.min_distance_from_depot[destination] = min_turn_around_distance
                if destination.getId() == vs_depot_index:
                    self.min_time_to_depot[origin] = min_turn_around_time
                    self.min_distance_to_depot[origin] = min_turn_around_distance