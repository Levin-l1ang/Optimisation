from typing import List

from core.exceptions.graph_exceptions import GraphNodeIdMultiplyAssignedException, GraphIncidentNodeNotFoundException, \
    GraphEdgeIdMultiplyAssignedException
from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.io.csv import CsvReader, CsvWriter
from core.model.graph import Graph
from core.model.impl.dict_graph import DictGraph
from core.model.infrastructure import InfrastructureEdge, InfrastructureNode, WalkingEdge
from core.util.config import Config


class InfrastructureReader:
    """
    Class containing all methods to read an infrastructure network with a corresponding walking graph.
    Use a CsvReader with the appropriate processing methods as an argument to read the files.
    """

    def __init__(self, node_file_name: str, infrastructure_edge_file_name: str, walking_edge_file_name: str,
                 infrastructure_network: Graph[InfrastructureNode, InfrastructureEdge], walking_network: Graph[InfrastructureNode, WalkingEdge],
                 directed_infrastructure: bool, directed_walking: bool, conversion_factor_length: float,
                 conversion_factor_coordinates: float):
        self.node_file_name = node_file_name
        self.infrastructure_edge_file_name = infrastructure_edge_file_name
        self.walking_edge_file_name = walking_edge_file_name
        self.infrastructure_network = infrastructure_network
        self.walking_network = walking_network
        self.directed_infrastructure = directed_infrastructure
        self.directed_walking = directed_walking
        self.conversion_factor_length = conversion_factor_length
        self.conversion_factor_coordinates = conversion_factor_coordinates

    def process_node(self, args: List[str], line_number: int):
        if len(args) != 5:
            raise InputFormatException(self.node_file_name, len(args), 5)
        try:
            node_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.node_file_name, 1, line_number, "int", args[0])
        name = args[1]
        try:
            x_coord = float(args[2]) * self.conversion_factor_coordinates
        except ValueError:
            raise InputTypeInconsistencyException(self.node_file_name, 3, line_number, "float", args[1])
        try:
            y_coord = float(args[3]) * self.conversion_factor_coordinates
        except ValueError:
            raise InputTypeInconsistencyException(self.node_file_name, 4, line_number, "float", args[2])
        if args[4].lower() not in ("true", "false"):
            raise InputTypeInconsistencyException(self.node_file_name, 5, line_number, "bool", args[4])
        stop_possible = (args[4].lower() == "true")
        new_node = InfrastructureNode(node_id, name, x_coord, y_coord, stop_possible)
        if not self.infrastructure_network.addNode(new_node) or not self.walking_network.addNode(new_node):
            raise GraphNodeIdMultiplyAssignedException(node_id)

    def process_infrastructure_edge(self, args: List[str], line_number: int):
        if len(args) != 6:
            raise InputFormatException(self.infrastructure_edge_file_name, len(args), 6)

        try:
            edge_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 1, line_number, "int", args[0])
        try:
            left_node_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 2, line_number, "int", args[1])
        try:
            right_node_id = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 3, line_number, "int", args[2])
        try:
            length = float(args[3]) * self.conversion_factor_length
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 4, line_number, "float", args[3])
        try:
            lower_bound = int(args[4])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 5, line_number, "int", args[4])
        try:
            upper_bound = int(args[5])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 6, line_number, "int", args[5])

        left_node = self.infrastructure_network.getNode(left_node_id)
        if not left_node:
            raise GraphIncidentNodeNotFoundException(edge_id, left_node_id)
        right_node = self.infrastructure_network.getNode(right_node_id)
        if not right_node:
            raise GraphIncidentNodeNotFoundException(edge_id, right_node_id)
        edge = InfrastructureEdge(edge_id, left_node, right_node, length, lower_bound, upper_bound,
                                  self.directed_infrastructure)
        if not self.infrastructure_network.addEdge(edge):
            raise GraphEdgeIdMultiplyAssignedException(edge_id)

    def process_walking_edge(self, args: List[str], line_number: int):
        if len(args) != 4:
            raise InputFormatException(self.infrastructure_edge_file_name, len(args), 5)
        try:
            edge_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 1, line_number, "int", args[0])
        try:
            left_node_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 2, line_number, "int", args[1])
        try:
            right_node_id = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 3, line_number, "int", args[2])
        try:
            time = int(args[3])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_edge_file_name, 4, line_number, "int", args[3])
        left_node = self.walking_network.getNode(left_node_id)
        if not left_node:
            raise GraphIncidentNodeNotFoundException(edge_id, left_node_id)
        right_node = self.walking_network.getNode(right_node_id)
        if not right_node:
            raise GraphIncidentNodeNotFoundException(edge_id, right_node_id)
        edge = WalkingEdge(edge_id, left_node, right_node, time, self.directed_walking)
        if not self.walking_network.addEdge(edge):
            raise GraphEdgeIdMultiplyAssignedException(edge_id)

    @staticmethod
    def read(read_nodes: bool = True, read_infrastructure_edges: bool = True, read_walking_edges: bool = True,
             node_file_name: str = "", infrastructure_edge_file_name: str = "", walking_edge_file_name: str = "",
             directed_infrastructure: bool = None, directed_walking: bool = None,
             infrastructure_network: Graph[InfrastructureNode, InfrastructureEdge] = None,
             walking_network: Graph[InfrastructureNode, WalkingEdge] = None, conversion_factor_length: float = None,
             conversion_factor_coordinates: float = None, config: Config = Config.getDefaultConfig()) \
            -> (Graph[InfrastructureNode, InfrastructureEdge], Graph[InfrastructureNode, WalkingEdge]):
        """
        Read the given files and add them to the infrastructure network/the walking graph.
        If no graphs are given, new ones are created. If parameters are not given but needed,
        the respective values will be read from the given config.
        :param read_nodes: whether to read the infrastructure nodes. These are added to both networks
        :param read_infrastructure_edges: whether to read the infrastructure edges
        :param read_walking_edges: whether to read the walking edges
        :param node_file_name: the node file name to read. If none is given, the file name will be read from the config
        :param infrastructure_edge_file_name: the file to read the infrastructure edges from. If none is given, the
        file name will be read from the config
        :param walking_edge_file_name: the file to read the walking edges from. If none is given, the
        file name will be read from the config
        :param directed_infrastructure: whether the infrastructure network read should be directed. If it is not given,
        this will be queried from the config
        :param directed_walking: whether the walking network read should be directed. If it is not given,
        this will be queried from the config
        :param infrastructure_network: the infrastructure network to store the read contents in. If none is given, a
        new one will be created
        :param walking_network: the walking network to store the read contents in. If none is given, a
        new one will be created
        :param conversion_factor_length: the factor to convert the length of an edge into kilometers
        :param conversion_factor_coordinates: the factor to convert the distances between coordinates into kilometers
        :param config: the config to query parameters from. Will only be used when parameters are needed but not given
        :return: the networks with the added data
        """
        if not infrastructure_network:
            infrastructure_network = DictGraph()
        if not walking_network:
            walking_network = DictGraph()
        if read_nodes:
            if not node_file_name:
                node_file_name = config.getStringValue("filename_node_file")
            if not conversion_factor_coordinates:
                conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates")
        if read_infrastructure_edges:
            if not infrastructure_edge_file_name:
                infrastructure_edge_file_name = config.getStringValue("filename_infrastructure_edge_file")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue("gen_conversion_length")
            if directed_infrastructure is None:
                directed_infrastructure = not config.getBooleanValue("ptn_is_undirected")
        if read_walking_edges:
            if not walking_edge_file_name:
                walking_edge_file_name = config.getStringValue("filename_walking_edge_file")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue("gen_conversion_length")
            if directed_walking is None:
                directed_walking = config.getBooleanValue("sl_walking_is_directed")
        reader = InfrastructureReader(node_file_name, infrastructure_edge_file_name, walking_edge_file_name,
                                      infrastructure_network, walking_network, directed_infrastructure,
                                      directed_walking, conversion_factor_length, conversion_factor_coordinates)
        if read_nodes:
            CsvReader.readCsv(node_file_name, reader.process_node)
        if read_infrastructure_edges:
            CsvReader.readCsv(infrastructure_edge_file_name, reader.process_infrastructure_edge)
        if read_walking_edges:
            CsvReader.readCsv(walking_edge_file_name, reader.process_walking_edge)
        return infrastructure_network, walking_network


class InfrastructureWriter:
    """
    Class implementing the writing of the infrastructure and walking network as a static method.
    """

    @staticmethod
    def write(infrastructure_network: Graph[InfrastructureNode, InfrastructureEdge] = None,
              walking_network: Graph[InfrastructureNode, WalkingEdge] = None, config: Config = Config.getDefaultConfig(),
              write_nodes: bool = True, write_infrastructure_edges: bool = True, write_walking_edges: bool = True,
              nodes_file_name: str = "", infrastructure_edge_file_name: str = "", walking_edge_file_name: str = "",
              nodes_header: str = "", infrastructure_edge_header: str = "", walking_edge_header: str = "",
              conversion_factor_coordinates: float = None, conversion_factor_length: float = None):
        """
        Write the given networks to the specified files. The parts to write can be controlled by write_nodes,
        write_infrastructure_edges and write_walking_edges. If filenames and/or headers are not given for data to write,
        the respective values will be read from the given config.
        :param infrastructure_network: the infrastructure network to write
        :param walking_network: the walking network to write
        :param config: the config to read parameters from that are needed but not given
        :param write_nodes: whether to write the nodes. If set to true, the nodes from the infrastructure network will
        be written
        :param write_infrastructure_edges: whether to write the infrastructure edges
        :param write_walking_edges: whether to write the walking edges
        :param nodes_file_name: where to write the nodes to
        :param infrastructure_edge_file_name: where to write the infrastructure edges to
        :param walking_edge_file_name: where to write the walking edges to
        :param nodes_header: the header for the nodes file
        :param infrastructure_edge_header: the header for the infrastructure edges file
        :param walking_edge_header: the header for the walking edges file
        :param conversion_factor_coordinates: the conversion factor for the node coordinates
        :param conversion_factor_length: the factor to converse edge lengths to km
        """
        if write_nodes:
            if not nodes_file_name:
                nodes_file_name = config.getStringValue("filename_node_file")
            if not nodes_header:
                nodes_header = config.getStringValue("nodes_header")
            if not conversion_factor_coordinates:
                conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates")
            CsvWriter.writeListStatic(nodes_file_name, infrastructure_network.getNodes(),
                 lambda n: n.toCsvStrings(conversion_factor_coordinates),
                 InfrastructureNode.getId, nodes_header)
        if write_infrastructure_edges:
            if not infrastructure_edge_file_name:
                infrastructure_edge_file_name = config.getStringValue("filename_infrastructure_edge_file")
            if not infrastructure_edge_header:
                infrastructure_edge_header = config.getStringValue("infrastructure_edge_header")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue("gen_conversion_length")
            CsvWriter.writeListStatic(infrastructure_edge_file_name, infrastructure_network.getEdges(),
                lambda e: e.toCsvStrings(conversion_factor_length),
                InfrastructureEdge.getId, infrastructure_edge_header)
        if write_walking_edges:
            if not walking_edge_file_name:
                walking_edge_file_name = config.getStringValue("filename_walking_edge_file")
            if not walking_edge_header:
                walking_edge_header = config.getStringValue("walking_edge_header")
            CsvWriter.writeListStatic(walking_edge_file_name, walking_network.getEdges(), WalkingEdge.toCsvStrings,
                                      WalkingEdge.getId, walking_edge_header)
