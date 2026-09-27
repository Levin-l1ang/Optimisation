from core.exceptions.graph_exceptions import GraphNodeIdMultiplyAssignedException, GraphIncidentNodeNotFoundException, \
    GraphEdgeIdMultiplyAssignedException
from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.exceptions.data_exceptions import DataIndexNotFoundException
from core.io.csv import CsvReader, CsvWriter
from core.model.graph import Graph
from core.model.impl.dict_graph import DictGraph
from core.model.infrastructure_network import InfraLink, InfraNode, DirectionType
from core.util.config import Config


class InfrastructureNetworkReader:
    """
    Class containing all methods to read an infrastructure network.
    Use a CsvReader with the appropriate processing methods as an argument to read the files.
    """

    def __init__(self, infrastructure_node_file_name: str, infrastructure_link_file_name: str, infrastructure_load_file_name: str, infrastructure_node_coordinates_file_name: str,
                 infrastructure_network: Graph[InfraNode, InfraLink],
                 conversion_factor_length: float,
                 conversion_factor_coordinates: float):
        self.infrastructure_node_file_name = infrastructure_node_file_name
        self.infrastructure_link_file_name = infrastructure_link_file_name
        self.infrastructure_node_coordinates_file_name = infrastructure_node_coordinates_file_name
        self.infrastructure_load_file_name = infrastructure_load_file_name
        self.infrastructure_network = infrastructure_network
        self.conversion_factor_length = conversion_factor_length
        self.conversion_factor_coordinates = conversion_factor_coordinates

    def process_infrastructure_node(self, args: list[str], line_number: int):
        if len(args) != 5:
            raise InputFormatException(self.infrastructure_node_file_name, len(args), 5)
        try:
            node_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_node_file_name, 1, line_number, "int", args[0])
        name = args[1]
        try:
            x_coord = float(args[2]) * self.conversion_factor_coordinates
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_node_file_name, 3, line_number, "float", args[2])
        try:
            y_coord = float(args[3]) * self.conversion_factor_coordinates
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_node_file_name, 4, line_number, "float", args[3])
        if args[4].strip()[0] == '[' and args[4].strip()[-1] == ']':
            if args[4].strip()[1:-1] == "":
                node_modalities = []
            else:
                node_modalities = [modality.strip() for modality in args[4].strip()[1:-1].split(',')]
        else:
            raise InputTypeInconsistencyException(self.infrastructure_node_file_name, 5, line_number, "[<comma-separated strings>]", args[4])

        new_node = InfraNode(node_id, name, x_coord, y_coord, node_modalities)
        if not self.infrastructure_network.addNode(new_node):
            raise GraphNodeIdMultiplyAssignedException(node_id)

    def process_infrastructure_link(self, args: list[str], line_number: int):
        if len(args) != 7:
            raise InputFormatException(self.infrastructure_link_file_name, len(args), 7)

        try:
            link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_link_file_name, 1, line_number, "int", args[0])
        try:
            left_node_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_link_file_name, 2, line_number, "int", args[1])
        try:
            right_node_id = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_link_file_name, 3, line_number, "int", args[2])
        try:
            length = float(args[3]) * self.conversion_factor_length
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_link_file_name, 4, line_number, "float", args[3])
        try:
            capacity = float(args[4])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_link_file_name, 5, line_number, "float", args[4])

        if args[5].strip()[0] == '[' and args[5].strip()[-1] == ']':
            if args[5].strip()[1:-1] == "":
                link_modalities = []
            else:
                link_modalities = [modality.strip() for modality in args[5].strip()[1:-1].split(',')]
        else:
            raise InputTypeInconsistencyException(self.infrastructure_link_file_name, 6, line_number, "[<comma-separated strings>]", args[5])

        if args[6].strip()[0] == '[' and args[6].strip()[-1] == ']':
            if args[6].strip()[1:-1] == "":
                link_directions = {}
            else:
                link_directions = {}
                direction_list = args[6].strip()[1:-1].split(',')
                for i in range(len(link_modalities)):
                    try:
                        link_directions[link_modalities[i]] = DirectionType(direction_list[i].strip().upper())
                    except ValueError:
                        InputTypeInconsistencyException(self.infrastructure_link_file_name, 7, line_number, "DirectionType (FORWARDS|BACKWARDS|BOTH)", direction_list[i])
        else:
            raise InputTypeInconsistencyException(self.infrastructure_link_file_name, 7, line_number, "[<comma-separated strings (FORWARDS|BACKWARDS|BOTH)>]", args[6])

        left_node = self.infrastructure_network.getNode(left_node_id)
        if not left_node:
            raise GraphIncidentNodeNotFoundException(link_id, left_node_id)
        right_node = self.infrastructure_network.getNode(right_node_id)
        if not right_node:
            raise GraphIncidentNodeNotFoundException(link_id, right_node_id)
        edge = InfraLink(link_id, left_node, right_node, length, capacity, link_modalities, link_directions)
        if not self.infrastructure_network.addEdge(edge):
            raise GraphEdgeIdMultiplyAssignedException(link_id)

    def process_infrastructure_load(self, args: list[str], line_number: int):
        if len(args) != 2:
            raise InputFormatException(self.infrastructure_load_file_name, len(args), 2)

        try:
            link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_load_file_name, 1, line_number, "int", args[0])
        try:
            load = float(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_load_file_name, 2, line_number, "float", args[1])

        link = self.infrastructure_network.getEdge(link_id)
        if not link:
            raise DataIndexNotFoundException("Infrastructure link", link_id)
        link.setLoad(load)

    def process_geo_coordinates(self, args: list[str], line_number: int):
        if len(args) != 3:
            raise InputFormatException(self.infrastructure_node_coordinates_file_name, len(args), 3)

        try:
            node_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_node_coordinates_file_name, 1, line_number, "int", args[0])
        try:
            latitude = float(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_node_coordinates_file_name, 2, line_number, "float", args[1])
        try:
            longitude = float(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.infrastructure_node_coordinates_file_name, 3, line_number, "float", args[2])

        node = self.infrastructure_network.getNode(node_id)
        if not node:
            raise DataIndexNotFoundException("Infrastructure Node", node_id)

        node.setLatitude(latitude)
        node.setLongitude(longitude)

    @staticmethod
    def read(read_infrastructure_nodes: bool = True, read_infrastructure_links: bool = True, read_loads: bool = False, read_geo_coordinates: bool = False,
             infrastructure_node_file_name: str = "", infrastructure_link_file_name: str = "", infrastructure_load_file_name: str = "", infrastructure_node_coordinates_file_name: str = "",
             infrastructure_network: Graph[InfraNode, InfraLink] = None,
             conversion_factor_length: float = None,
             conversion_factor_coordinates: float = None, config: Config = Config.getDefaultConfig()) \
            -> Graph[InfraNode, InfraLink]:
        """
        Read the given files and add them to the infrastructure network.
        If no graphs are given, new ones are created. If parameters are not given but needed,
        the respective values will be read from the given config.

        :param read_infrastructure_nodes: whether to read the infrastructure nodes, defaults to True
        :type: bool, optional
        :param read_infrastructure_links: whether to read the infrastructure links, defaults to True
        :type: bool, optional
        :param read_loads: whether to read the loads on the infrastructure links, defaults to False
        :type: bool, optional
        :param read_geo_coordinates: whether to read the geographical positions of the infrastructure nodes, defaults to False
        :type: bool, optional
        :param infrastructure_node_file_name: the infrastructure node file name to read. If none is given, the file name will be read from the config, defaults to None
        :type: str, optional
        :param infrastructure_link_file_name: the file to read the infrastructure links from. If none is given, the file name will be read from the config, defaults to None
        :type: str, optional
        :param infrastructure_load_file_name: the file to read the loads of the infrastructure links from. If none is given, the file name will be read from the config, defaults to None
        :type: str, optional
        :param infrastructure_node_coordinates_file_name: the file to read the geographical positions of the nodes from. If none is given, the file name will be read from the config, defaults to None
        :type: str, optional
        :param infrastructure_network: the infrastructure network to store the read contents in. If none is given, a new one will be created, defaults to None
        :type: Graph[InfraNode, InfraLink], optional
        :param conversion_factor_length: the factor to convert the length of the links into kilometers, defaults to None
        :type: float, optional
        :param conversion_factor_coordinates: the factor to convert the distances between coordinates into kilometers, defaults to None
        :type: float, optional
        :param config: the config to query parameters from. Will only be used when parameters are needed but not given, defaults to the default config
        :type: Config, optional
        :return: the network with the added data
        :rtype: Graph[InfraNode, InfraLink]
        """
        if not infrastructure_network:
            infrastructure_network = DictGraph()
        if read_infrastructure_nodes:
            if not infrastructure_node_file_name:
                infrastructure_node_file_name = config.getStringValue("filename_infrastructure_node_file")
            if not conversion_factor_coordinates:
                conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates")
        if read_infrastructure_links:
            if not infrastructure_link_file_name:
                infrastructure_link_file_name = config.getStringValue("filename_infrastructure_link_file")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue("gen_conversion_length")
        if read_loads:
            if not infrastructure_load_file_name:
                infrastructure_load_file_name = config.getStringValue("filename_infrastructure_load_file")
        if read_geo_coordinates:
            if not infrastructure_node_coordinates_file_name:
                infrastructure_node_coordinates_file_name = config.getStringValue("filename_infrastructure_node_coordinates_file")
        reader = InfrastructureNetworkReader(infrastructure_node_file_name, infrastructure_link_file_name, infrastructure_load_file_name, infrastructure_node_coordinates_file_name,
                                      infrastructure_network,
                                      conversion_factor_length, conversion_factor_coordinates)
        if read_infrastructure_nodes:
            CsvReader.readCsv(infrastructure_node_file_name, reader.process_infrastructure_node)
        if read_infrastructure_links:
            CsvReader.readCsv(infrastructure_link_file_name, reader.process_infrastructure_link)
        if read_loads:
            CsvReader.readCsv(infrastructure_load_file_name, reader.process_infrastructure_load)
        if read_geo_coordinates:
            CsvReader.readCsv(infrastructure_node_coordinates_file_name, reader.process_geo_coordinates)
        return infrastructure_network


class InfrastructureNetworkWriter:
    """
    Class implementing the writing of the infrastructure network as a static method.
    """

    @staticmethod
    def write(infrastructure_network: Graph[InfraNode, InfraLink] = None, config: Config = Config.getDefaultConfig(),
              write_infrastructure_nodes: bool = True, write_infrastructure_links: bool = True, write_load: bool = False, write_geo_coordinates: bool = False,
              infrastructure_nodes_file_name: str = "", infrastructure_link_file_name: str = "", infrastructure_load_file_name: str = "", infrastructure_geo_coordinates_file_name: str = "",
              infrastructure_nodes_header: str = "", infrastructure_link_header: str = "", infrastructure_load_header: str = "", infrastructure_geo_coordinates_header: str = "",
              conversion_factor_coordinates: float = None, conversion_factor_length: float = None):
        """
        Write the given networks to the specified files. The parts to write can be controlled by write_infrastructure_nodes,
        write_infrastructure_links, write_loads and write_geo_coordinates. If filenames and/or headers are not given for data to write,
        the respective values will be read from the given config.

        :param infrastructure_network: the infrastructure network to write
        :type infrastructure_network: Graph[InfraNode, InfraLink], optional
        :param config: the config to read parameters from that are needed but not given, defaults to the DefaultConfig
        :type config: Config, optional
        :param write_infrastructure_nodes: whether to write the nodes. If set to true, the nodes from the infrastructure network will be written, defaults to True
        :type write_infrastructure_nodes: bool, optional
        :param write_infrastructure_links: whether to write the infrastructure edges, defaults to True
        :type write_infrastructure_links: bool, optional
        :param write_loads: whether to write the loads of the infrastructure edges, defaults to False
        :type write_loads: bool, optional
        :param write_geo_coordinates: whether to write the geo coordinates of the nodes, defaults to False
        :type write_geo_coordinates: bool, optional
        :param infrastructure_nodes_file_name: where to write the nodes to, defaults to ""
        :type infrastructure_nodes_file_name: str, optional
        :param infrastructure_link_file_name: where to write the infrastructure edges to, defaults to ""
        :type infrastructure_link_file_name: str, optional
        :param infrastructure_load_file_name: where to write the loads to, defaults to ""
        :type infrastructure_load_file_name: str, optional
        :param infrastructure_geo_coordinates_file_name: where to write the geo coordinates to, defaults to ""
        :type infrastructure_geo_coordinates_file_name: str, optional
        :param infrastructure_nodes_header: the header for the nodes file, defaults to ""
        :type infrastructure_nodes_header: str, optional
        :param infrastructure_link_header: the header for the infrastructure edges file, defaults to ""
        :type infrastructure_nodes_header: str, optional
        :param infrastructure_load_header: the header for the load file, defaults to ""
        :type infrastructure_nodes_header: str, optional
        :param infrastructure_geo_coordinates_header: the header for the geo coordinates file, defaults to ""
        :type infrastructure_nodes_header: str, optional
        :param conversion_factor_coordinates: the conversion factor for the node coordinates
        :type infrastructure_nodes_header: str, optional
        :param conversion_factor_length: the factor to converse edge lengths to km
        :type infrastructure_nodes_header: str, optional
        """
        if write_infrastructure_nodes:
            if not infrastructure_nodes_file_name:
                infrastructure_nodes_file_name = config.getStringValue("filename_infrastructure_node_file")
            if not infrastructure_nodes_header:
                infrastructure_nodes_header = config.getStringValue("infrastructure_node_header")
            if not conversion_factor_coordinates:
                conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates")
            CsvWriter.writeListStatic(infrastructure_nodes_file_name, infrastructure_network.getNodes(),
                 lambda x: x.toCsvStrings(conversion_factor_coordinates), InfraNode.getId, infrastructure_nodes_header)
        if write_infrastructure_links:
            if not infrastructure_link_file_name:
                infrastructure_link_file_name = config.getStringValue("filename_infrastructure_link_file")
            if not infrastructure_link_header:
                infrastructure_link_header = config.getStringValue("infrastructure_link_header")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue("gen_conversion_length")
            CsvWriter.writeListStatic(infrastructure_link_file_name, infrastructure_network.getEdges(),
                lambda x: x.toCsvStrings(conversion_factor_length), InfraLink.getId, infrastructure_link_header)
        if write_load:
            if not infrastructure_load_file_name:
                infrastructure_load_file_name = config.getStringValue("filename_infrastructure_load_file")
            if not infrastructure_load_header:
                infrastructure_load_header = config.getStringValue("infrastructure_load_header")
            CsvWriter.writeListStatic(infrastructure_load_file_name, infrastructure_network.getEdges(),
                InfraLink.toCsvLoadStrings, InfraLink.getId, infrastructure_load_header)
        if write_geo_coordinates:
            if not infrastructure_geo_coordinates_file_name:
                infrastructure_geo_coordinates_file_name = config.getStringValue("filename_infrastructure_node_coordinates_file")
            if not infrastructure_geo_coordinates_header:
                infrastructure_geo_coordinates_header = config.getStringValue("infrastructure_node_coordinates_header")
            CsvWriter.writeListStatic(infrastructure_geo_coordinates_file_name, infrastructure_network.getNodes(),
                InfraNode.toCsvGeoStrings, InfraNode.getId, infrastructure_geo_coordinates_header)

