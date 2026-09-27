import copy
import math
from heapq import heappush, heappop
from functools import cache

from core.model.graph import (Edge, Node, Graph)
from core.model.path import Path
from core.model.impl.list_path import ListPath

import core.exceptions.algorithm_dijkstra as exception
from core.exceptions.algorithm_dijkstra import(
    AlgorithmDijkstraNegativeEdgeLengthException as NegativeLengthExpection,
)

from typing import TypeVar, Generic, Callable, List, Optional

N = TypeVar('N', bound=Node)
E = TypeVar('E', bound=Edge[N])
G = TypeVar('G', bound=Graph[N, E])


class SPNode(Generic[N, E]):
    """Class to represent a node in the Dijkstra class.

    Enhanced for multigraph support by storing the edge used to reach this node.
    This class is used internally by the Dijkstra algorithm to maintain
    the priority queue of nodes to be processed.

    :param N: Type variable for Node type
    :param E: Type variable for Edge type
    """

    def __init__(self, node: N, predecessor: N, distance: float, edge: Optional[E] = None):
        """Initialize a new SPNode instance.

        :param node: The graph node this SPNode represents
        :type node: N
        :param predecessor: The predecessor node in the shortest path
        :type predecessor: N
        :param distance: The total distance from start node to this node
        :type distance: float
        :param edge: The edge used to reach this node from its predecessor
        :type edge: Optional[E]
        """
        self.node = node
        self.distance = distance
        self.predecessor = predecessor
        self.edge = edge

    @cache
    def __lt__(self, other: 'SPNode[N, E]'):
        """Compare two SPNode instances for priority queue ordering.

        Comparison is based on:
        1. Distance (lower is better)
        2. Node ID (for deterministic ordering)
        3. Predecessor ID (for consistent tie-breaking)

        :param other: The other SPNode to compare with
        :type other: SPNode[N, E]
        :return: True if this node has higher priority (lower distance), False otherwise
        :rtype: bool
        :raises: NotImplemented if comparison is not possible
        """
        try:
            if self.distance < other.distance:
                return True
            elif self.distance > other.distance:
                return False
            elif self.node.getId() < other.node.getId():
                return True
            elif self.node.getId() > other.node.getId():
                return False
            else:
                return self.predecessor.getId() < other.predecessor.getId()
        except AttributeError:
            return NotImplemented


class Dijkstra(Generic[N, E]):
    """Enhanced Dijkstra implementation that supports multigraphs.

    This implementation can handle multiple edges between the same pair of nodes
    and correctly identifies the shortest path using the optimal edges. The algorithm
    maintains all equally good shortest paths and can return either a single optimal
    path or all optimal paths.

    Key features:
    - Supports both directed and undirected graphs
    - Handles multigraphs with multiple edges between nodes
    - Can compute paths from single or multiple start nodes
    - Stores optimal edges for efficient path reconstruction
    - Caches computed paths for better performance

    Note:
        The graph should not be modified after initializing this class, as the
        results become undefined. A new instance must be created after graph changes.

    :param N: Type variable for Node type
    :param E: Type variable for Edge type

    Example:
        .. code-block:: python

            # Initialize Dijkstra with a graph, start node, and distance function
            dijkstra = Dijkstra(graph, start_node, lambda edge: edge.weight)

            # Compute shortest path to a target node
            distance = dijkstra.computeShortestPath(target_node)
            path = dijkstra.getPath(target_node)

            # Get all shortest paths
            all_paths = dijkstra.getPaths(target_node)
    """

    def __init__(self, graph: Graph[N, E], start_node: N | list[N], distance_function: Callable[[E], float]):
        """Initialize a new Dijkstra shortest path algorithm instance.

        Computing is done with :meth:`computeShortestPath` or :meth:`computeShortestPaths`
        and needs to be done before querying shortest paths or distances.

        :param graph: The graph to compute shortest paths on. Must not be altered
                     after initialization as results become undefined.
        :type graph: Graph[N, E]
        :param start_node: The start node(s) of the algorithm. Can be a single node
                          or a list of nodes for multi-source shortest paths.
        :type start_node: N | list[N]
        :param distance_function: Function to compute the length/weight of an edge.
                                 Must return non-negative values.
        :type distance_function: Callable[[E], float]

        Example:
            .. code-block:: python

                # Single start node
                dijkstra = Dijkstra(graph, start_node, lambda e: e.weight)

                # Multiple start nodes
                dijkstra = Dijkstra(graph, [node1, node2], lambda e: e.length)
        """
        self.graph = graph
        if isinstance(start_node, Node):
            self.start_nodes: list[N] = [start_node]
        else:
            self.start_nodes: list[N] = start_node
        self.distance_function = distance_function

        # Enhanced to store the optimal edge for each predecessor
        self.predecessors_id: dict[int, list[tuple[N, E]]] = {}
        # distance from the start node
        self.distances_id: dict[int, float] = {
            node.getId(): 0 for node in self.start_nodes
        }
        # priority queue for the distance calculation frontier.
        self.queue: list[SPNode[N, E]] = []

        for node in self.start_nodes:
            self.add_outgoing_nodes(node, 0)
        self.already_computed_shortest_paths: dict[N, list[Path[N, E]]] = {}

    def add_outgoing_nodes(self, node: N, distance: float):
        """Add nodes reachable via outgoing edges to the processing queue.

        Enhanced for multigraph support: considers all edges, including multiple
        edges between the same nodes. Each edge is evaluated separately to find
        the optimal paths in multigraphs.

        :param node: The node object we have just visited
        :type node: N
        :param distance: The distance from start node(s) to the current node
        :type distance: float
        :raises NegativeLengthExpection: If any edge has negative length
        """
        for edge in self.graph.getOutgoingEdges(node):
            left = edge.getLeftNode()
            new_node = left if left.getId() != node.getId() else edge.getRightNode()
            if new_node.getId() == node.getId():
                continue  # skip self-loops

            edge_length = self.distance_function(edge)
            if edge_length < 0:
                raise NegativeLengthExpection(edge, edge_length)

            new_distance = edge_length + distance

            # In multigraphs, we need to consider all edges, even to already visited nodes
            # if they might provide a shorter path
            if (new_node.getId() not in self.distances_id or
                new_distance <= self.distances_id[new_node.getId()]):
                sp_node = SPNode(new_node, node, new_distance, edge)
                heappush(self.queue, sp_node)

    def is_computed(self, node: N | list[N]) -> bool:
        """Check if shortest path computation is complete for the given node(s).

        A node's shortest path is considered computed when either:
        1. The processing queue is empty (all reachable nodes processed)
        2. The next node in queue has a greater distance than the target node

        :param node: The node or list of nodes to check computation status for
        :type node: N | list[N]
        :return: True if computation is complete, False if more processing needed
        :rtype: bool
        """
        if not self.queue:
            return True
        if isinstance(node, list):
            return any(self.is_computed(n) for n in node)
        if node.getId() not in self.distances_id:
            return False
        return self.queue[0].distance > self.distances_id[node.getId()]

    def getDistance(self, endNode: N | list[N]) -> float:
        """Get the shortest distance from start node(s) to the given end node(s).

        The shortest path to the end node must be computed first using
        :meth:`computeShortestPath` or :meth:`computeShortestPaths`.

        :param endNode: The destination node or list of nodes
        :type endNode: N | list[N]
        :return: The shortest distance, or infinity if no path exists
        :rtype: float
        :raises exception.AlgorithmDijkstraQueryDistanceBeforeComputationException:
                If distance is queried before computation

        Example:
            .. code-block:: python

                dijkstra.computeShortestPath(target)
                distance = dijkstra.getDistance(target)  # Returns shortest distance

                # For multiple targets, returns minimum distance
                min_distance = dijkstra.getDistance([target1, target2, target3])
        """
        if not self.is_computed(endNode):
            raise exception.AlgorithmDijkstraQueryDistanceBeforeComputationException(endNode)
        if isinstance(endNode, list):
            return min(
                self.distances_id.get(node.getId(), math.inf) for node in endNode
            )
        return self.distances_id.get(endNode.getId(), math.inf)

    def getPath(self, destination: N | list[N]) -> Optional[Path[N, E]]:
        """Get a shortest path from start node(s) to the destination node(s).

        Enhanced for multigraph support: uses the stored optimal edges for
        efficient and correct path reconstruction. If multiple destinations
        are provided, returns the path to the closest one.

        :param destination: The destination node or list of nodes
        :type destination: N | list[N]
        :return: A shortest path, or None if no path exists or destination is a start node
        :rtype: Optional[Path[N, E]]
        :raises exception.AlgorithmDijkstraQueryPathBeforeComputationException:
                If path is queried before computation
        :raises exception.AlgorithmDijkstraUnknownNodeException:
                If path reconstruction fails due to graph inconsistency

        Example:
            .. code-block:: python

                dijkstra.computeShortestPath(target)
                path = dijkstra.getPath(target)
                if path:
                    edges = path.getEdges()  # Get the sequence of edges
        """
        if not self.is_computed(destination):
            raise exception.AlgorithmDijkstraQueryPathBeforeComputationException(destination)

        if isinstance(destination, list):
            min_distance: float = math.inf
            min_index: int = -1
            for idx, node in enumerate(destination):
                if self.is_computed(node):
                    distance = self.getDistance(node)
                    if distance < min_distance:
                        min_distance = distance
                        min_index = idx
            endNode: N = destination[min_index]
        else:
            endNode: N = destination

        if endNode in self.start_nodes or self.getDistance(endNode) == math.inf:
            return None

        if endNode in self.already_computed_shortest_paths:
            return copy.deepcopy(self.already_computed_shortest_paths[endNode][0])

        path = ListPath(self.graph.isDirected())
        current_node = endNode

        while current_node not in self.start_nodes:
            # Get the optimal predecessor and edge
            if current_node.getId() not in self.predecessors_id:
                raise exception.AlgorithmDijkstraUnknownNodeException(endNode)

            # Take the first predecessor-edge pair (all should be optimal)
            next_node, optimal_edge = self.predecessors_id[current_node.getId()][0]

            path.addFirstEdge(optimal_edge)
            current_node = next_node

        self.already_computed_shortest_paths[endNode] = [path]
        return copy.deepcopy(path)

    def getPaths(self, destination: N | list[N]) -> Optional[List[Path[N, E]]]:
        """Get all shortest paths from start node(s) to the destination node(s).

        Enhanced for multigraph support: considers all optimal predecessor-edge
        combinations to find all equally good shortest paths. All paths with
        the same optimal length are returned.

        :param destination: The destination node or list of nodes
        :type destination: N | list[N]
        :return: List of all shortest paths, or None if no path exists
        :rtype: Optional[List[Path[N, E]]]
        :raises exception.AlgorithmDijkstraQueryPathBeforeComputationException:
                If paths are queried before computation
        :raises exception.AlgorithmDijkstraUnknownNodeException:
                If path reconstruction fails due to graph inconsistency

        Example:
            .. code-block:: python

                dijkstra.computeShortestPath(target)
                all_paths = dijkstra.getPaths(target)
                if all_paths:
                    print(f"Found {len(all_paths)} optimal paths")
                    for path in all_paths:
                        print(f"Path length: {len(path.getEdges())}")
        """
        if not self.is_computed(destination):
            raise exception.AlgorithmDijkstraQueryPathBeforeComputationException(destination)

        if isinstance(destination, list):
            min_distance: float = math.inf
            paths: list[Path[N, E]] | None = None
            for node in destination:
                if self.is_computed(node):
                    distance = self.getDistance(node)
                    if distance < min_distance:
                        min_distance = distance
                        paths = self.getPaths(node)
                    elif distance == min_distance:
                        node_paths = self.getPaths(node)
                        if node_paths is not None:
                            if paths is None:
                                paths = node_paths
                            else:
                                paths += node_paths
            return paths
        else:
            endNode = destination

        if endNode in self.start_nodes or self.distances_id.get(endNode.getId()) == math.inf:
            return None

        if endNode in self.already_computed_shortest_paths:
            return copy.deepcopy(self.already_computed_shortest_paths[endNode])

        paths = []

        # Process all optimal predecessors
        for nextNode, optimal_edge in self.predecessors_id[endNode.getId()]:
            if nextNode in self.start_nodes:
                sp = ListPath(self.graph.isDirected())
                sp.addFirstEdge(optimal_edge)
                paths.append(sp)
                continue

            shortest_part_paths = self.getPaths(nextNode)
            if shortest_part_paths is None:
                return None

            for shortest_part_path in shortest_part_paths:
                shortest_path = ListPath(self.graph.isDirected())
                shortest_path.addLast(shortest_part_path.getEdges())
                shortest_path.addLastEdge(optimal_edge)
                paths.append(shortest_path)

        # Buffer the paths
        buffer_paths = []
        for sp in paths:
            edges = sp.getEdges()
            buffer_path = ListPath(self.graph.isDirected())
            for edge in edges:
                buffer_path.addLastEdge(edge)
            buffer_paths.append(buffer_path)
        self.already_computed_shortest_paths[endNode] = buffer_paths

        return copy.deepcopy(paths)

    def computeShortestPath(self, endNode: N | list[N]) -> float:
        """Compute the shortest path from start node(s) to the given end node(s).

        Enhanced for multigraph support: properly handles multiple edges between
        the same nodes and stores optimal edges for each shortest path. The algorithm
        explores all edges and maintains all equally good paths.

        :param endNode: The destination node or list of nodes to compute paths to
        :type endNode: N | list[N]
        :return: The shortest distance to the destination, or infinity if unreachable
        :rtype: float

        Example:
            .. code-block:: python

                # Compute path to single destination
                distance = dijkstra.computeShortestPath(target_node)

                # Compute paths to multiple destinations (returns minimum distance)
                min_distance = dijkstra.computeShortestPath([node1, node2, node3])
        """
        while not self.is_computed(endNode):
            first = heappop(self.queue)

            # If we've already found a shorter path to this node, skip
            if (first.node.getId() in self.distances_id and
                self.distances_id[first.node.getId()] < first.distance):
                continue

            # If this is an equally good path, add the predecessor-edge pair
            if (first.node.getId() in self.distances_id and
                self.distances_id[first.node.getId()] == first.distance):
                if first.node.getId() in self.predecessors_id:
                    # Add this predecessor-edge pair if it's not already there
                    pred_edge_pair = (first.predecessor, first.edge)
                    if pred_edge_pair not in self.predecessors_id[first.node.getId()]:
                        self.predecessors_id[first.node.getId()].append(pred_edge_pair)
                continue

            # This is the first time we see this node or we found a better path
            self.distances_id[first.node.getId()] = first.distance
            self.predecessors_id[first.node.getId()] = [(first.predecessor, first.edge)]
            self.add_outgoing_nodes(first.node, first.distance)

        return self.getDistance(endNode)

    def computeShortestPaths(self) -> None:
        """Compute shortest paths from start node(s) to all reachable nodes.

        This method will compute shortest paths to every node in the graph,
        reusing already computed paths for efficiency. After calling this method,
        distances and paths to any node can be queried without additional computation.

        Example:
            .. code-block:: python

                # Compute all shortest paths at once
                dijkstra.computeShortestPaths()

                # Now any node can be queried efficiently
                for node in graph.getNodes():
                    distance = dijkstra.getDistance(node)
                    path = dijkstra.getPath(node)
        """
        for node in self.graph.getNodes():
            if not self.is_computed(node):
                self.computeShortestPath(node)