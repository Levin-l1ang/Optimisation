import copy
import math
import logging
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


class SPNode(Generic[N]):
    """Class to represent a node in the Dijkstra class.
    """
    def __init__(self, node: N, predecesor: N, distance: float):
        self.node = node
        self.distance = distance
        self.predecesor = predecesor

    @cache
    def __lt__(self, other: 'SPNode[N]'):
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
                return self.predecesor.getId() < other.predecesor.getId()
        except AttributeError:
            return NotImplemented


class Dijkstra(Generic[N, E]):
    """
    A straight forward implementation of the algorithm of Dijkstra, using
    PriorityQueue. The algorithm can be initialized with some graph (directed
    or undirected), a node in the graph and a distance function to compute the
    length of the edges. Shortest paths can be computed using
    computeShortestPath(Node) or computeShortestPaths(). The distance to a node
    and the shortest path can only be queried after they are computed by one of
    the two mentioned methods. If the shortest path for a queried node was
    already calculated in an earlier query (maybe due to a side effect of
    another query) there will be no new computation. This holds for
    computeShortestPaths() as well.
    Note that the used graph may not be altered after initializing an instance
    of this graph, since the results after that are undefined (already computed
    shortest paths may change). Therefore, the algorithm needs to be
    initialised anew after each change!
    """

    logger = logging.getLogger(__name__)

    def __init__(self, graph: Graph[N, E], start_node: N | list[N], distance_function: Callable[[E], float]):
        """
        Initialize a new shortest path algorithm. Computing is done with
        computeShortestPath(Node) or computeShortestPaths() and needs to be
        done before querying a shortest path or a distance with
        getDistance(Node) or getPath(Node).
        :param graph:    the graph to compute the shortest paths on. Note that
                        the used graph may not be altered after initializing
                        an instance of this graph, since the results after that are undefined
                        (already computed shortest paths may change). Therefore, the
                        algorithm needs to be inizialised anew after each change!
        :type graph: Graph[N, E]
        :param start_node:    the start node of the algorithm, i.e., the start of
                            the shortest paths.
        :type start_node: N | List[N]
        :param distance_function:     the distance function to compute the length
                                    of an edge.
        :type distance_function: Callable[[E], float]
        """

        # Flag to determine whether a warning for negative edges leading to/coming from
        # nodes with zero out/in-degree
        self.warning_printed = False

        self.graph = graph
        if isinstance(start_node, Node):
            self.start_nodes: list[N] = [start_node]
        else:
            self.start_nodes: list[N] = start_node
        self.distance_function = distance_function
        # used for calculating the path
        self.predecessors_id: dict[int, list[N]] = {}
        # distance from the start node
        self.distances_id: dict[int, float] = {
            node.getId(): 0 for node in self.start_nodes
        }
        # priority queue for the distance calculation frontier.
        # Shared between all invocations of distance calculation.
        self.queue: list[SPNode[N]] = []
        for node in self.start_nodes:
            self.add_outgoing_nodes(node, 0)
        self.already_computed_shortest_paths: dict[N, list[Path[N, E]]] = {}



    def add_outgoing_nodes(self, node: N, distance: float):
        """Add nodes on the other end of outgoing edges to the queue.

        :param node: The node object we have just visited.
        :param distance: The distance from self.start_node to the node.
        """
        for edge in self.graph.getOutgoingEdges(node):
            left = edge.getLeftNode()
            new_node = left if left.getId() != node.getId() else edge.getRightNode()
            if new_node.getId() == node.getId():
                continue  # skip edges with the same origin and destination
            edge_length = self.distance_function(edge)
            if edge_length < 0:
                if self.graph.isDirected() and (len(self.graph.getIncomingEdges(edge.getLeftNode()))==0 or len(self.graph.getOutgoingEdges(edge.getRightNode()))==0):
                    if not self.warning_printed:
                        self.logger.debug("Graph contains an edge with negative length which either leads to a node with out-degree zero or comes from a node with in-degree zero. For this special case of negative edge lengths, Dijsktra's algorithm will still work correctly.")
                        self.warning_printed = True
                else:
                    raise NegativeLengthExpection(edge, edge_length)
            if new_node.getId() not in self.distances_id:
                sp_node = SPNode(new_node, node, edge_length + distance)
                heappush(self.queue, sp_node)

    def is_computed(self, node: N | list[N]) -> bool:
        """Check if all computations for the given node are done.

        :param node: The node for which we want to check the computation status.
        :return: True if computation is done, i.e. we can get shortest paths and
        distance from self.start_node, False otherwise
        """
        if not self.queue:
            # no new nodes to explore -> all must be calculated
            return True
        if isinstance(node, list):
            return any(self.is_computed(n) for n in node)
        if node.getId() not in self.distances_id:
            # something to explore, but no distance value yet -> keep exploring
            return False
        # only when the next distance is greater, we can be guaranteed that all
        # shortest paths to endNode are found. The distance will not change.
        return self.queue[0].distance > self.distances_id[node.getId()]

    def getDistance(self, endNode: N | list[N]) -> float:
        """
        Get the distance form the initialized start node to the given end node.
        The shortest path to the given end node needs to be computed first,
        either by computeShortestPath(Node) or computeShortestPaths().

        :param endNode: the end node of the shortest path.
        :type endNode: N
        :raises exception.AlgorithmDijkstraUnknownNodeException: if the node was not in the graph during instantiation.
        :raises exception.AlgorithmDijkstraQueryDistanceBeforeComputationException: if the distance to the node was not yet computed.
        :return: the distance between the start and the end node or
                 MAX_VALUE if there is no path.
        :rtype: float
        """
        if not self.is_computed(endNode):
            raise exception.AlgorithmDijkstraQueryDistanceBeforeComputationException(endNode)
        if isinstance(endNode, list):
            return min(
                self.distances_id.get(node.getId(), math.inf) for node in endNode
            )
        return self.distances_id.get(endNode.getId(), math.inf)

    def getPath(self, destination: N | list[N]) -> Optional[Path[N, E]]:
        """
        Get the shortest path from the initialized start node to the given end
        node. The shortest path to the given end node needs to be computed
        first, either by computeShortestPath(Node) or computeShortestPaths().

        :param endNode: the end node of the shortest path
        :type endNode: N
        :raises exception.AlgorithmDijkstraQueryPathBeforeComputationException: if the path was queried before it was computed or if
            the node is not know, i.e., it was not in the graph when the Dijkstra class was instantiated.
        :return: the shortest path between the start and the end node or None, if
                the start and end node coincide or there is no path between the nodes.
        :rtype: Path
        """
        if not self.is_computed(destination):
            raise exception.AlgorithmDijkstraQueryPathBeforeComputationException(destination)
        if isinstance(destination, list):
            # set endNode to be equal to the node with the shortest distance
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
            # search for the edge between current and next node
            next_node = next(iter(self.predecessors_id.get(current_node.getId())))
            found_edge = False
            for edge in self.graph.getIncomingEdges(current_node):
                if((edge.getLeftNode() == current_node and edge.getRightNode() == next_node) or
                   (edge.getRightNode() == current_node and edge.getLeftNode() == next_node)):
                    path.addFirstEdge(edge)
                    current_node = next_node
                    found_edge = True
                    break
            if not found_edge:
                # This should never happen in a valid graph and with a well working algorithm.
                raise exception.AlgorithmDijkstraUnknownNodeException(endNode)
        self.already_computed_shortest_paths[endNode] = [path]
        return copy.deepcopy(path)

    def getPaths(self, destination: N | list[N]) -> Optional[List[Path[N, E]]]:
        """
        Get all shortest paths from the initialized start node to the given end
        node. All paths with the length of the shortest path will be returned.
        The shortest paths to the given end node need to be computed first,
        either by computeShortestPath(Node) or computeShortestPaths().
        :param destination  the end node of the shortest paths.
        :type destination: N | List[N]
        :raises exception.AlgorithmDijkstraUnknownNodeException: if the path was queried before it was computed or if
        the node is not knows, i.e., it was not in the fraph when the Dijkstra class was constructed.
        :return     the collection of shortest paths.
        :rtype: List[Path[N, E]] | None
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
                    if distance == min_distance:
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
        for nextNode in self.predecessors_id[endNode.getId()]:
            next_edge = None
            for edge in self.graph.getIncomingEdges(endNode):
                if (edge.getLeftNode() == endNode and edge.getRightNode() == nextNode) or \
                        (edge.getRightNode() == endNode and edge.getLeftNode() == nextNode):
                    next_edge = edge
                    break
            if next_edge is None:
                # This should never happen
                raise(exception.AlgorithmDijkstraUnknownNodeException(endNode))

            # Find all shortest paths to nextNode
            if nextNode in self.start_nodes:
                sp = ListPath(self.graph.isDirected())
                sp.addFirstEdge(next_edge)
                paths.append(sp)
                continue
            shortest_part_paths = self.getPaths(nextNode)
            if shortest_part_paths is None:
                # This is only the case of the distance to nextNode from
                # startNode was infinity. Therefore, the graph is not
                # connected.
                return None
            for shortest_part_path in shortest_part_paths:
                shortest_path = ListPath(self.graph.isDirected())
                shortest_path.addLast(shortest_part_path.getEdges())
                shortest_path.addLastEdge(next_edge)
                paths.append(shortest_path)
        # Copy all paths to store for buffering
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
        """
        Compute the shortest path from the initialized start nodes to the given
        end nodes.

        :param endNode: the node(s) to compute the shortest path to.
        :type endNode: N | List[N]
        :return: the distance between start and end node or inf, if there is no path.
        :rtype: float
        """
        while not self.is_computed(endNode):
            first = heappop(self.queue)
            # handle distance and predecesor updating
            if first.node.getId() in self.distances_id:
                if self.distances_id[first.node.getId()] == first.distance:
                    self.predecessors_id[first.node.getId()].append(first.predecesor)
                continue
            # seeing this node for the first time -> first.distance must be
            # the shortest path length to this node
            self.distances_id[first.node.getId()] = first.distance
            self.predecessors_id[first.node.getId()] = [first.predecesor]
            self.add_outgoing_nodes(first.node, first.distance)

        return self.getDistance(endNode)

    def computeShortestPaths(self) -> None:
        """
        Compute a shortest path between the initialized start node and every
        other node in the graph. Will reuse already computed shortest paths.
        """
        for node in self.graph.getNodes():
            if not self.is_computed(node):
                self.computeShortestPath(node)
