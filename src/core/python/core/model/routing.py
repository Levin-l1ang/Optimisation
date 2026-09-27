import logging

from typing import Dict, Set, Tuple, Callable
from core.exceptions.routing_exceptions import RoutingNoPathAvailableException, RoutingPathInconsistencyException
from core.model.graph import Graph, Node, Edge
from core.model.impl.list_path import ListPath
from core.util.networkx import convert_nxpath_to_listpath
from core.model.impl.mapOD import MapOD
import networkx as nx


class Routing:
    """
    A class for representing a routing in a graph.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, graph: Graph[Node, Edge], paths: Dict[Node, Dict[Node, ListPath]] = dict()) -> None:
        """
        Constructor for a routing in a graph
        """
        self.graph = graph
        self.directed = graph.isDirected()
        self.paths = dict()
        self.node_pairs = set()
        for origin in paths.keys():
            for destination in paths[origin].keys():
                self.setPath(origin, destination, paths[origin][destination])

    def fillShortestPaths(self, function: Callable[[Edge], float]):
        """
        computes a shortest path routing w.r.t. the specified function and sets the path for all od-pairs not yet included.
        :param function: weight function on the edges
        """
        # Create networkX graph and determine shortest paths w.r.t. specified function between all pairs of nodes
        G = nx.DiGraph()
        G.add_nodes_from(self.graph.getNodes())
        for edge in self.graph.getEdges():
            G.add_edge(edge.getLeftNode(), edge.getRightNode(), weight=function(edge))
            if not self.directed:
                G.add_edge(edge.getRightNode(), edge.getLeftNode(), weight=function(edge))
        nxpaths = dict(nx.all_pairs_dijkstra_path(G))
        for origin in self.graph.getNodes():
            for destination in self.graph.getNodes():
                if (origin, destination) not in self.node_pairs:
                    try:
                        self.setPath(origin, destination, convert_nxpath_to_listpath(nxpaths[origin][destination], self.graph))
                    except KeyError:
                        raise RoutingNoPathAvailableException(origin.getId(), destination.getId())
        return self

    def getGraph(self) -> Graph[Node, Edge]:
        """ returns the underlying graph """
        return self.graph

    def getPaths(self) -> Dict[Node, Dict[Node, ListPath]]:
        """ returns a dictionary of ListPaths """
        return self.paths

    def getNodePairs(self) -> Set[Tuple[Node, Node]]:
        """ returns the set of all node pairs for which there is a path in the routing """
        return self.node_pairs

    def getPath(self, origin: Node, destination: Node) -> ListPath:
        """
        returns the path from origin to destination as ListPath
        :param origin: origin node
        :param destination: destination node
        :return: path from origin to destination
        """
        if (origin, destination) not in self.node_pairs:
            self.logger.debug(f"No path from {origin.getId()} to {destination.getId()} available!")
            raise RoutingNoPathAvailableException(origin.getId(), destination.getId())
        return self.paths[origin][destination]

    def getPathLength(self, origin: Node, destination: Node) -> float:
        """
        returns the sum of the lengths of the edges of the path from origin to destination
        :param origin: origin node
        :param destination: destination node
        :return: path length from origin to destination
        """
        if (origin, destination) not in self.node_pairs:
            self.logger.warning(f"No path from {origin.getId()} to {destination.getId()} available!")
            raise RoutingNoPathAvailableException(origin.getId(), destination.getId())
        return sum([edge.getLength() for edge in self.paths[origin][destination].getEdges()])

    def getPathLengths(self) -> Dict[Node, Dict[Node, float]]:
        """ returns dictionary of paths lengths w.r.t. edge length """
        paths_lengths = dict()
        for (origin, destination) in self.node_pairs:
            if origin not in paths_lengths.keys():
                paths_lengths[origin] = dict()
            paths_lengths[origin][destination] = self.getPathLength(origin, destination)
        return paths_lengths

    def getPathLowerBound(self, origin: Node, destination: Node) -> float:
        """
        returns the sum of the lower bounds on the edges of the path from origin to destination
        :param origin: origin node
        :param destination: destination node
        :return: sum of lower bounds from origin to destination
        """
        if (origin, destination) not in self.node_pairs:
            self.logger.warning(f"No path from {origin.getId()} to {destination.getId()} available!")
            raise RoutingNoPathAvailableException(origin.getId(), destination.getId())
        return sum([edge.getLowerBound() for edge in self.paths[origin][destination].getEdges()])

    def setPath(self, origin: Node, destination: Node, path: ListPath) -> None:
        """
        set the path from origin to destination to the given ListPath
        :param origin: origin node
        :param destination: destination node
        :param path: the path to set
        """
        # If path is empty or consists of just one node, test if OD-pair is trivial. If so, just set it
        if len(path.getNodes()) < 2:
            if origin != destination:
                self.logger.debug(f"Empty path given for OD-pair with different origin and destination!")
                raise RoutingPathInconsistencyException(origin.getId(), destination.getId())
            if len(path.getNodes()) == 1 and origin != path.getNodes()[0]:
                self.logger.debug(f"Given path has different end nodes than specified origin and destination!")
                raise RoutingPathInconsistencyException(origin.getId(), destination.getId())
            if origin not in self.paths.keys():
                self.paths[origin] = dict()
            self.paths[origin][destination] = path
            self.node_pairs.add((origin, destination))
        else:
        # Test, if path is from origin to destination
            pathnode1 = path.getNodes()[0]
            pathnode2 = path.getNodes()[-1]
            if {pathnode1, pathnode2} != {origin, destination}:
                self.logger.debug(f"Given path has different end nodes than specified origin and destination!")
                raise RoutingPathInconsistencyException(origin.getId(), destination.getId())
            if path.getNodes()[0] != origin:
                if self.directed:
                    self.logger.debug(f"Given path has wrong direction and graph is directed!")
                    raise RoutingPathInconsistencyException(origin.getId(), destination.getId())
                succeded = path.reverse()
                if not succeded:
                    self.logger.warning(f"Given path has wrong direction and can not be reversed!")
                    raise RoutingPathInconsistencyException(origin.getId(), destination.getId())
            # Now we can add the path
            if origin not in self.paths.keys():
                self.paths[origin] = dict()
            self.paths[origin][destination] = path
            self.node_pairs.add((origin, destination))

    def setPaths(self, paths: Dict[Node, Dict[Node, ListPath]]) -> None:
        for origin in paths.keys():
            for destination in paths[origin].keys():
                self.setPath(origin, destination, paths[origin][destination])

    def isGraphDirected(self) -> bool:
        """ returns, if the underlying graph is directed or not """
        return self.graph.isDirected()

    def clearPaths(self) -> None:
        """ clears the paths dictionary """
        self.paths = dict()
        self.node_pairs = set()

    def clearPath(self, origin: Node, destination: Node) -> None:
        if (origin, destination) in self.node_pairs:
            self.node_pairs.remove((origin, destination))
            self.paths[origin][destination] = ListPath(self.isGraphDirected())

    def isComplete(self) -> bool:
        """ Tests, if the routing is complete, i.e. if there is a path for each OD-pair except trivial pairs"""
        for origin in self.graph.getNodes():
            for destination in self.graph.getNodes():
                if origin!=destination and ((origin, destination) not in self.node_pairs):
                    return False
        return True

    def __eq__(self, other) -> bool:
        if not isinstance(other, Routing):
            return False
        return (self.graph == other.graph and self.paths == other.paths)

    def __ne__(self, other) -> bool:
        return not self.__eq__(other)

    def __str__(self) -> str:
        return ("Routing:\n"
                + "\n".join(["Node {} to Node {}:\n{}\n".format(origin.getId(), destination.getId(), self.paths[origin][destination])
                            for origin in self.paths.keys() for destination in self.paths[origin]]))
