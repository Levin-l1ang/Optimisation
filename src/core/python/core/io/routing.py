import logging

from typing import Dict
from enum import Enum
from core.exceptions.data_exceptions import DataIndexNotFoundException, DataRoutingMultiplePathsException, DataRoutingPathInconsistencyException
from core.exceptions.graph_exceptions import GraphEdgeByNodesNotFoundException
from core.exceptions.input_exceptions import InputTypeInconsistencyException
from core.io.csv import CsvReader, CsvWriter
from core.model.graph import Graph, Node, Edge
from core.util.config import Config
from core.model.path import Path
from core.model.impl.list_path import ListPath
from core.model.routing import Routing


class RoutingGraphType(Enum):
    """
    Enumeration of all possible counting modes in the zone tariff model.
    """
    PTN = "PTN"
    EAN = "EAN"

class RoutingReader:
    """
    Class to process csv-lines, formatted in the LinTim Routing-Ptn.giv or Routing-Ean.giv format.
    Use a CsvReader with the appropriate processing methods as an argument to read the files.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, routing_file_name: str, paths: Dict[Node, Dict[Node, Path]],
                 graph: Graph[Node, Edge]):
        """
        Constructor of a RoutingReader for a routing, a graph and a given
        filename. The given name will not influence the read file but the used name in any error message, so be sure to
        use the same name in here and in the CsvReader!
        :param routing_file_name: source file name for exceptions
        :param paths: paths
        :param graph: the underlying graph
        """
        self.routing_file_name = routing_file_name
        self.paths = paths
        self.graph = graph
        self.directed = graph.isDirected()
        self.read_pairs = set()

    def process_routing_line(self, args: [str], line_number: int) -> None:
        """
        Process the contents of a routing line.
        :param args: the content of the line
        :param line_number: the line number, used for error handling
        """
        # convert inputs to ints
        try:
            origin_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.routing_file_name, 1, line_number, "int", args[0])
        try:
            destination_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.routing_file_name, 2, line_number, "int", args[1])
        try:
            node_ids = args[2].split(",")
            if len(node_ids) > 1:
                node_ids = [int(token.strip()) for token in node_ids]
            else: # given path consists of one or less nodes
                node_ids = []
        except ValueError:
            raise InputTypeInconsistencyException(self.routing_file_name, 3, line_number, "[int]", args[2])

        # determine origin and destination node
        origin = self.graph.getNode(origin_id)
        if not origin:
            raise DataIndexNotFoundException("Node", origin_id)
        destination = self.graph.getNode(destination_id)
        if not destination:
            raise DataIndexNotFoundException("Node", destination_id)
        if (origin, destination) in self.read_pairs:
            raise DataRoutingMultiplePathsException(origin_id, destination_id)

        # build path and check consistency
        if origin not in self.paths.keys():
            self.paths[origin] = dict()
        path = ListPath(self.directed)
        nodes = []
        if len(node_ids) < 2:
            if origin == destination:
                self.paths[origin][destination] = path
                self.read_pairs.add((origin, destination))
            else:
                raise DataRoutingPathInconsistencyException(origin_id, destination_id)
        else: # given path consists of at least two nodes
            for node_id in node_ids:
                node = self.graph.getNode(node_id)
                if not node:
                    self.logger.error(f"Error occured in line {line_number} of file {self.routing_file_name}")
                    raise DataIndexNotFoundException("Node", node_id)
                nodes.append(node)
            if nodes[0] != origin or nodes[-1] != destination:
                raise DataRoutingPathInconsistencyException(origin_id, destination_id)
            edges = []
            for i in range(len(nodes)-1):
                node1 = nodes[i]
                node2 = nodes[i+1]
                edge = self.graph.get_edge_by_nodes(node1, node2)
                if not edge:
                    self.logger.error(f"Error occured in line {line_number} of file {self.routing_file_name}")
                    raise GraphEdgeByNodesNotFoundException(node1.getId(), node2.getId())
                edges.append(edge)
            succeded = path.addLast(edges)
            if not succeded:
                raise DataRoutingPathInconsistencyException(origin_id, destination_id)
            self.paths[origin][destination] = path
            self.read_pairs.add((origin, destination))


    @staticmethod
    def read(graph: Graph[Node, Edge], graph_type: RoutingGraphType = RoutingGraphType.PTN, read_paths: bool = True,
             routing_file_name: str = "", paths: Dict[Node, Dict[Node, Path]] = None,
             config: Config = Config.getDefaultConfig()) -> Routing:
        """
        Read the routing, depending on read_paths. Read paths will be added to the given path-dict, if there is some.
        :param graph: the underlying graph
        :param graph_type: whether we deal with a PTN or an EAN
        :read_paths: if the paths should be read
        :param routing_file_name: the routing file name to read.
        :param paths: given paths dictionary
        :param config: the config to use to read file names or headers, if necessary. Will use the default config if
        none is given
        :return: a dictionary of ListPaths indexed by node pairs.
        """

        if graph_type == RoutingGraphType.PTN:
            routing_file_name = config.getStringValue("filename_routing_ptn_input")
        elif graph_type == RoutingGraphType.EAN:
            routing_file_name = config.getStringValue("filename_routing_ean_input")
        if not paths:
            paths = dict()
        reader = RoutingReader(routing_file_name, paths, graph)
        if read_paths:
            CsvReader.readCsv(routing_file_name, reader.process_routing_line)
        routing = Routing(graph, paths)

        return routing


class RoutingWriter:
    """
    Class implementing writing routings as static methods. Use the static methods to write.
    """
    logger = logging.getLogger(__name__)

    @staticmethod
    def write(routing: Routing, graph_type: RoutingGraphType = RoutingGraphType.PTN, write_paths: bool = True,
              routing_file_name: str = "", routing_header: str = "", config: Config = Config.getDefaultConfig()):
        """
        Write the given routing as routing file. If no file name or header is given, the
        values are read from the given config (or the default config, if there is none).
        :param routing: the routing to write
        :param graph_type: whether we deal with a PTN or an EAN
        :param write_paths: whether to write the paths
        :param routing_file_name: filename to write the data to
        :param routing_header: header to write in the file
        :param config: the config to use to read file names or headers, if necessary. Will use the default config if
        none is given
        """
        if write_paths:
            if not routing_file_name:
                if graph_type == RoutingGraphType.PTN:
                    routing_file_name = config.getStringValue("filename_routing_ptn_output")
                elif graph_type == RoutingGraphType.EAN:
                    routing_file_name = config.getStringValue("filename_routing_ean_output")
            if not routing_header:
                routing_header = config.getStringValue("routing_header")
            routing_writer = CsvWriter(routing_file_name, routing_header)
            paths = routing.getPaths()
            paths = dict(sorted(paths.items(), key=lambda s: s[0].getId()))
            for origin in paths.keys():
                paths[origin] = dict(sorted(paths[origin].items(), key=lambda s: s[0].getId()))
                for destination in paths[origin].keys():
                    routing_writer.writeLine([str(origin.getId()), str(destination.getId()), ",".join([str(path_node.getId()) for path_node in paths[origin][destination].getNodes()])])
            routing_writer.close()


