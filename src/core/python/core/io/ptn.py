from typing import List, Optional
import logging

from core.exceptions.data_exceptions import DataIndexNotFoundException, DataPTNInfrastructureMapInvalidPathException, DataPTNInfrastructureMapStopMismatchException, DataPTNInfrastructureMapPathEndpointsMismatchException
from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.exceptions.graph_exceptions import GraphEdgeIdMultiplyAssignedException, GraphIncidentNodeNotFoundException, \
    GraphNodeIdMultiplyAssignedException
from core.exceptions.exceptions import LinTimException
from core.io.csv import CsvReader, CsvWriter
from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.ptn import Stop, Link
from core.model.infrastructure_network import InfraLink, InfraNode, InfraPath
from core.util.config import Config
from core.util.mm_utilities import add_modality_prefix


class PTNReader:
    """
    Class containing all methods to read a PTN, i.e., methods for reading stops and links.

    Use a CsvReader with the appropriate processing methods as an argument to read the files.
    """

    def __init__(
        self,
        stop_file_name: str,
        link_file_name: str,
        ptn: Graph[Stop, Link],
        directed: bool,
        infrastructure_network: Graph[InfraNode, InfraLink] = None,
        load_file_name: str = None,
        headway_file_name: str = None,
        geo_coordinate_file_name: str = None,
        ptn_infrastructure_map_file_name: str = None,
        conversion_factor_length: float = 1,
        conversion_factor_coordinates: float = 1,
        modality: str = '',
    ):
        """
        Initialize a new PTN reader with the source files that should be read.

        The names of the files given here have no influence on the read files, but will be used
        for error handling, so be sure to give the same names as in the processor method.

        :param stop_file_name: The name of the stops file
        :type stop_file_name: str
        :param link_file_name: The name of the link file
        :type link_file_name: str
        :param ptn: The ptn to add the stops and links to
        :type ptn: Graph[Stop, Link]
        :param directed: Whether the ptn should be directed
        :type directed: bool
        :param infrastructure_network: The infrastructure network graph
        :type infrastructure_network: Graph[InfraNode, InfraLink], optional
        :param load_file_name: The name of the load file
        :type load_file_name: str, optional
        :param headway_file_name: The name of the headway file
        :type headway_file_name: str, optional
        :param geo_coordinate_file_name: The name of the geo-coordinate file
        :type geo_coordinate_file_name: str, optional
        :param ptn_infrastructure_map_file_name: The name of the PTN infrastructure map file
        :type ptn_infrastructure_map_file_name: str, optional
        :param conversion_factor_length: The factor to convert the input edge length into kilometers
        :type conversion_factor_length: float
        :param conversion_factor_coordinates: The factor to convert the coordinates such that the euclidean distance between coordinates correspond to kilometers
        :type conversion_factor_coordinates: float
        :param modality: Modality tag used for read stops
        :type modality: str
        """
        self.stop_file_name = stop_file_name
        self.link_file_name = link_file_name
        self.ptn = ptn
        self.directed = directed
        self.infrastructure_network = infrastructure_network
        self.load_file_name = load_file_name
        self.headway_file_name = headway_file_name
        self.geo_coordinate_file_name = geo_coordinate_file_name
        self.ptn_infrastructure_map_file_name = ptn_infrastructure_map_file_name
        self.conversion_factor_length = conversion_factor_length
        self.conversion_factor_coordinates = conversion_factor_coordinates
        self.modality = modality

    def process_stop(self, args: List[str], line_number: int):
        """
        Process the contents of a stop line.

        :param args: The content of the line
        :type args: List[str]
        :param line_number: The line number, used for error handling
        :type line_number: int
        :raises InputFormatException: If the line format is incorrect
        :raises InputTypeInconsistencyException: If data types cannot be converted
        :raises GraphNodeIdMultiplyAssignedException: If stop ID is already assigned
        """
        if len(args) != 5:
            raise InputFormatException(self.stop_file_name, len(args), 5)
        try:
            stop_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.stop_file_name, 1, line_number, "int", args[0])
        short_name = args[1]
        long_name = args[2]
        try:
            x_coordinate = float(args[3]) * self.conversion_factor_coordinates
        except ValueError:
            raise InputTypeInconsistencyException(self.stop_file_name, 4, line_number, "float", args[3])
        try:
            y_coordinate = float(args[4]) * self.conversion_factor_coordinates
        except ValueError:
            raise InputTypeInconsistencyException(self.stop_file_name, 5, line_number, "float", args[4])
        if not self.ptn.addNode(Stop(stop_id, short_name, long_name, x_coordinate, y_coordinate, modality=self.modality)):
            raise GraphNodeIdMultiplyAssignedException(stop_id)

    def process_link(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a link line.

        :param args: The content of the line
        :type args: List[str]
        :param line_number: The line number, used for error handling
        :type line_number: int
        :raises InputFormatException: If the line format is incorrect
        :raises InputTypeInconsistencyException: If data types cannot be converted
        :raises GraphIncidentNodeNotFoundException: If referenced stops are not found
        :raises GraphEdgeIdMultiplyAssignedException: If link ID is already assigned
        """
        if len(args) != 6:
            raise InputFormatException(self.link_file_name, len(args), 6)

        try:
            link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.link_file_name, 1, line_number, "int", args[0])
        try:
            left_stop_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.link_file_name, 2, line_number, "int", args[1])
        try:
            right_stop_id = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.link_file_name, 3, line_number, "int", args[2])
        try:
            length = float(args[3]) * self.conversion_factor_length
        except ValueError:
            raise InputTypeInconsistencyException(self.link_file_name, 4, line_number, "float", args[3])
        try:
            lower_bound = int(args[4])
        except ValueError:
            raise InputTypeInconsistencyException(self.link_file_name, 5, line_number, "int", args[4])
        try:
            upper_bound = int(args[5])
        except ValueError:
            raise InputTypeInconsistencyException(self.link_file_name, 6, line_number, "int", args[5])

        left_stop = self.ptn.getNode(left_stop_id)
        if not left_stop:
            raise GraphIncidentNodeNotFoundException(link_id, left_stop_id)
        right_stop = self.ptn.getNode(right_stop_id)
        if not right_stop:
            raise GraphIncidentNodeNotFoundException(link_id, right_stop_id)
        link = Link(link_id, left_stop, right_stop, length, lower_bound, upper_bound, self.directed, modality=self.modality)
        if not self.ptn.addEdge(link):
            raise GraphEdgeIdMultiplyAssignedException(link_id)

    def process_load(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a load line.

        :param args: The content of the line
        :type args: List[str]
        :param line_number: The line number, used for error handling
        :type line_number: int
        :raises InputFormatException: If the line format is incorrect
        :raises InputTypeInconsistencyException: If data types cannot be converted
        :raises DataIndexNotFoundException: If the referenced link is not found
        """
        if len(args) != 4:
            raise InputFormatException(self.load_file_name, len(args), 4)

        try:
            link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.load_file_name, 1, line_number, "int", args[0])
        try:
            load = float(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.load_file_name, 2, line_number, "int", args[1])
        try:
            lower_frequency_bound = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.load_file_name, 3, line_number, "int", args[2])
        try:
            upper_frequency_bound = int(args[3])
        except ValueError:
            raise InputTypeInconsistencyException(self.load_file_name, 4, line_number, "int", args[3])

        link = self.ptn.getEdge(link_id)
        if not link:
            raise DataIndexNotFoundException("Link", link_id)

        link.setLoad(load)
        link.setLowerFrequencyBound(lower_frequency_bound)
        link.setUpperFrequencyBound(upper_frequency_bound)

    def process_headway(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a headway line.

        :param args: The content of the line
        :type args: List[str]
        :param line_number: The line number, used for error handling
        :type line_number: int
        :raises InputFormatException: If the line format is incorrect
        :raises InputTypeInconsistencyException: If data types cannot be converted
        :raises DataIndexNotFoundException: If the referenced link is not found
        """
        if len(args) != 2:
            raise InputFormatException(self.load_file_name, len(args), 2)

        try:
            link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.load_file_name, 1, line_number, "int", args[0])
        try:
            headway = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.load_file_name, 2, line_number, "int", args[1])

        link = self.ptn.getEdge(link_id)
        if not link:
            raise DataIndexNotFoundException("Link", link_id)

        link.setHeadway(headway)

    def process_geo_coordinate(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a geo-coordinate line.

        :param args: The content of the line
        :type args: List[str]
        :param line_number: The line number, used for error handling
        :type line_number: int
        :raises InputFormatException: If the line format is incorrect
        :raises InputTypeInconsistencyException: If data types cannot be converted
        :raises DataIndexNotFoundException: If the referenced stop is not found
        """
        if len(args) != 3:
            raise InputFormatException(self.geo_coordinate_file_name, len(args), 2)
        try:
            stop_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.geo_coordinate_file_name, 1, line_number, "int", args[0])
        try:
            latitude = float(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.geo_coordinate_file_name, 2, line_number, "float", args[1])
        try:
            longitude = float(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.geo_coordinate_file_name, 3, line_number, "float", args[2])

        stop = self.ptn.getNode(stop_id)
        if not stop:
            raise DataIndexNotFoundException("Stop", stop_id)

        stop.setLatitude(latitude)
        stop.setLongitude(longitude)

    def process_ptn_infrastructure_map(self, args: list[str], line_number: int) -> None:
        """
        Process the contents of a PTN infrastructure map line.

        :param args: The content of the line
        :type args: List[str]
        :param line_number: The line number, used for error handling
        :type line_number: int
        :raises InputFormatException: If the line format is incorrect
        :raises InputTypeInconsistencyException: If data types cannot be converted
        :raises DataIndexNotFoundException: If referenced PTN link or infrastructure link is not found
        :raises DataPTNInfrastructureMapInvalidPathException: If the infrastructure path is invalid
        """
        if len(args) != 3:
            raise InputFormatException(self.ptn_infrastructure_map_file_name, len(args), 3)
        try:
            ptn_link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.ptn_infrastructure_map_file_name, 1, line_number, "int", args[0])
        try:
            infrastructure_link_number = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.ptn_infrastructure_map_file_name, 2, line_number, "int", args[1])
        try:
            infrastructure_link_id = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.ptn_infrastructure_map_file_name, 3, line_number, "int", args[2])
        ptn_link = self.ptn.getEdge(ptn_link_id)
        if not ptn_link:
            raise DataIndexNotFoundException("PTN Link", ptn_link_id)
        path = ptn_link.getUnderlyingInfrastructure()
        infrastructure_link = self.infrastructure_network.getEdge(infrastructure_link_id)

        if not infrastructure_link:
            raise DataIndexNotFoundException("Infrastructure Link", infrastructure_link_id)
        if len(path.getEdges()) != infrastructure_link_number - 1:
            raise DataPTNInfrastructureMapInvalidPathException(ptn_link_id, infrastructure_link_id)
        if not path.addLastEdge(infrastructure_link):
            # consistency checks of modalities direction are performed internally by the InfraPath class
            raise DataPTNInfrastructureMapInvalidPathException(ptn_link_id, infrastructure_link_id)

        ptn_link.setUnderlyingInfrastructure(path)


    @staticmethod
    def read(read_stops: bool = True, read_links: bool = True, read_loads: bool = False, read_headways: bool = False,
             read_geo_coordinates: bool = False, read_ptn_infrastructure_map: bool = False, stop_file_name: str = "", link_file_name: str = "",
             load_file_name: str = "", headway_file_name: str = "", geo_coordinate_file_name: str = "", ptn_infrastructure_map_file_name: str = "",
             directed: bool = None, ptn: Graph = None, infrastructure_network: Graph = None, conversion_factor_length: Optional[float] = None,
             conversion_factor_coordinates: Optional[float] = None, config: Config = Config.getDefaultConfig(), modality: str = "") \
            -> Graph[Stop, Link]:
        """
        Read the given files and add them to the ptn. If no ptn is given, a new one is created.

        :param read_stops: Whether to read the stops
        :type read_stops: bool
        :param read_links: Whether to read the links
        :type read_links: bool
        :param read_loads: Whether to read the loads
        :type read_loads: bool
        :param read_headways: Whether to read the headways
        :type read_headways: bool
        :param read_geo_coordinates: Whether to read the geo coordinates as well
        :type read_geo_coordinates: bool
        :param read_ptn_infrastructure_map: Whether to read the PTN infrastructure map
        :type read_ptn_infrastructure_map: bool
        :param stop_file_name: The stop file name
        :type stop_file_name: str
        :param link_file_name: The link file name
        :type link_file_name: str
        :param load_file_name: The load file name
        :type load_file_name: str
        :param headway_file_name: The headway file name
        :type headway_file_name: str
        :param geo_coordinate_file_name: The file to read the geo coordinates from
        :type geo_coordinate_file_name: str
        :param ptn_infrastructure_map_file_name: The PTN infrastructure map file name
        :type ptn_infrastructure_map_file_name: str
        :param directed: Whether the ptn is directed
        :type directed: bool, optional
        :param ptn: The ptn to add the data to
        :type ptn: Graph, optional
        :param infrastructure_network: The infrastructure network graph
        :type infrastructure_network: Graph, optional
        :param conversion_factor_length: The factor to convert the length of a link into kilometers
        :type conversion_factor_length: float, optional
        :param conversion_factor_coordinates: How to convert the given x/y-coordinates such that the euclidean distance refers to kilometers
        :type conversion_factor_coordinates: float, optional
        :param config: The config to read the default values for all parameters from
        :type config: Config
        :param modality: Modality prefix that will be added to the filenames separated by a dot. The stops will also be tagged with this string
        :type modality: str
        :returns: The ptn with the added data
        :rtype: Graph[Stop, Link]
        :raises LinTimException: If PTN infrastructure map should be read but no infrastructure network is provided
        """

        logger = logging.getLogger(__name__)

        if not ptn:
            ptn: Graph[Stop, Link] = SimpleDictGraph()
        if not infrastructure_network:
            if read_ptn_infrastructure_map:
                raise LinTimException("To read a PTN with underlying infrastructure, first read the infrastructure network and pass it to the reader!")
            infrastructure_network: Graph[InfraNode, InfraLink] = SimpleDictGraph()
        if read_stops:
            if not stop_file_name:
                stop_file_name = config.getStringValue("default_stops_file")
            if not conversion_factor_coordinates:
                conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates")
        if read_links:
            if not link_file_name:
                link_file_name = config.getStringValue("default_edges_file")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue("gen_conversion_length")
            if directed is None:
                directed = not config.getBooleanValue("ptn_is_undirected", modality)
        if read_loads and not load_file_name:
            load_file_name = config.getStringValue("default_loads_file")
        if read_headways and not headway_file_name:
            headway_file_name = config.getStringValue("default_headways_file")
        if read_geo_coordinates and not geo_coordinate_file_name:
            geo_coordinate_file_name = config.getStringValue("default_stops_coordinates_file")
        if read_ptn_infrastructure_map:
            if not ptn_infrastructure_map_file_name:
                ptn_infrastructure_map_file_name = config.getStringValue("filename_ptn_infrastructure_map_file")
            if directed is None:
                directed = not config.getBooleanValue("ptn_is_undirected", modality)
            vehicle_speed = config.getDoubleValue("gen_vehicle_speed", modality)
            max_coordinates_distance = config.getDoubleValue("isn_max_distance_coordinates")
        if modality != "":
            stop_file_name = add_modality_prefix(modality, stop_file_name)
            link_file_name = add_modality_prefix(modality, link_file_name)
            load_file_name = add_modality_prefix(modality, load_file_name)
            headway_file_name = add_modality_prefix(modality, headway_file_name)
            geo_coordinate_file_name = add_modality_prefix(modality, geo_coordinate_file_name)
            ptn_infrastructure_map_file_name = add_modality_prefix(modality, ptn_infrastructure_map_file_name)
        else:
            # Check, if one of the file names contains a modality prefix. If this is the case, the filename has an overwrite in State-Config.
            # Then, set modality name correctly, to check consistency of modalities when reading the infrastructure information.
            if len(ptn_infrastructure_map_file_name.split(".")) == 3:
                modality = ptn_infrastructure_map_file_name.split(".")[0].split("/")[1]
        reader = PTNReader(stop_file_name, link_file_name, ptn, directed, infrastructure_network, load_file_name, headway_file_name,
                           geo_coordinate_file_name, ptn_infrastructure_map_file_name, conversion_factor_length, conversion_factor_coordinates, modality=modality)
        if read_stops:
            CsvReader.readCsv(stop_file_name, reader.process_stop)
        if read_links:
            CsvReader.readCsv(link_file_name, reader.process_link)
        if read_loads:
            CsvReader.readCsv(load_file_name, reader.process_load)
        if read_headways:
            CsvReader.readCsv(headway_file_name, reader.process_headway)
        if read_geo_coordinates:
            CsvReader.readCsv(geo_coordinate_file_name, reader.process_geo_coordinate)
        if read_ptn_infrastructure_map:
            CsvReader.readCsv(ptn_infrastructure_map_file_name, reader.process_ptn_infrastructure_map)

            for ptn_edge in ptn.getEdges():
                path: InfraPath = ptn_edge.getUnderlyingInfrastructure()
                # check, if Endnodes of paths match with the expected ids
                if not path.checkEndpointsConsistency():
                    raise DataPTNInfrastructureMapPathEndpointsMismatchException(ptn_edge, path.getNodes()[0], path.getNodes()[-1])
                # check, if sum of lengths of infrastrusture links equals length of PTN edge
                if not path.checkPathLengthConsistency():
                    logger.debug(f"Note: Length of {modality} PTN edge {ptn_edge.getId()} is {ptn_edge.getLength()}, but length of underlying infrastructure is different!")
                # check, if lower time bound of edge is at least the length of the infrastructure/vehicle speed
                if not path.checkLowerBoundConsistency(vehicle_speed):
                    logger.debug(f"Note: Lower bound of {modality} PTN edge {ptn_edge.getId()} is {ptn_edge.getLowerBound()} minutes, but this is not enough time to travel the length of the underlying infrastructure path with a speed of {vehicle_speed} km/h!")

            for stop in ptn.getNodes():
                infra_node = infrastructure_network.getNode(stop.getId())
                if not infra_node:
                    raise DataIndexNotFoundException("Infrastructure Node", stop.getId())

                # check consistency of Node id and stop id:
                if not stop.setInfrastructureNode(infra_node, max_coordinates_distance):
                    raise DataPTNInfrastructureMapStopMismatchException(stop.getId(), infra_node.getId())

        return ptn


class PTNWriter:
    """
    Class implementing the writing of the ptn as a static method.

    Just call write to write the PTN.
    """

    @staticmethod
    def write(ptn: Graph[Stop, Link], config: Config = Config.getDefaultConfig(), write_stops: bool = True,
              stop_file_name: str = "", stop_header: str = "", write_links: bool = True,
              link_file_name: str = "", link_header: str = "", write_loads: bool = False,
              load_file_name: str = "", load_header: str = "", write_headways: bool = False,
              headway_file_name: str = "", headway_header: str = "", write_ptn_infrastructure_map: bool = False,
              ptn_infrastructure_map_file_name: str = "", ptn_infrastructure_map_header: str = "",
              write_geo_coordinates: bool = False, geo_coordinates_file_name: str = "", geo_coordinates_header: str = "", modality: str = "",
              conversion_factor_coordinates: Optional[float] = None,
              conversion_factor_length: Optional[float] = None):
        """
        Write the given ptn graph to the specified files.

        The parts to write can be controlled by write_stops, write_links, write_load and write_headways.
        If filename and/or header are not given for data to write, the respective values will be read
        from the given config (or from the default config, if none is given).

        :param ptn: The ptn to write
        :type ptn: Graph[Stop, Link]
        :param config: The config to read. Will be used, if some values are not given for data to write
        :type config: Config
        :param write_stops: Whether to write the stops
        :type write_stops: bool
        :param stop_file_name: The name of the file to write the stops
        :type stop_file_name: str
        :param stop_header: The header to write in the stop file
        :type stop_header: str
        :param write_links: Whether to write the links
        :type write_links: bool
        :param link_file_name: The name of the file to write the links
        :type link_file_name: str
        :param link_header: The header to write in the link file
        :type link_header: str
        :param write_loads: Whether to write the loads
        :type write_loads: bool
        :param load_file_name: The name of the file to write the loads
        :type load_file_name: str
        :param load_header: The header to write in the load file
        :type load_header: str
        :param write_headways: Whether to write the headways
        :type write_headways: bool
        :param headway_file_name: The name of the file to write the headways
        :type headway_file_name: str
        :param headway_header: The header to write in the headway file
        :type headway_header: str
        :param write_ptn_infrastructure_map: Whether to write the PTN infrastructure map
        :type write_ptn_infrastructure_map: bool
        :param ptn_infrastructure_map_file_name: The name of the file to write the PTN infrastructure map
        :type ptn_infrastructure_map_file_name: str
        :param ptn_infrastructure_map_header: The header to write in the PTN infrastructure map file
        :type ptn_infrastructure_map_header: str
        :param modality: If given, this string is prefixed to the file name as a prefix separated by a dot
        :type modality: str
        :param conversion_factor_coordinates: Resets coordinates to read values
        :type conversion_factor_coordinates: float, optional
        :param conversion_factor_length: Resets lengths to read values
        :type conversion_factor_length: float, optional
        """
        if write_stops:
            if not stop_file_name:
                stop_file_name = config.getStringValue("default_stops_file")
            if not stop_header:
                stop_header = config.getStringValue("stops_header")
            if not conversion_factor_coordinates:
                conversion_factor_coordinates = config.getDoubleValue(
                    "gen_conversion_coordinates")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue(
                    "gen_conversion_length")
            if modality != "":
                stop_file_name = add_modality_prefix(modality, stop_file_name)
            CsvWriter.writeListStatic(stop_file_name, ptn.getNodes(),
                lambda s: s.toCsvStrings(conversion_factor_coordinates),
                Stop.getId, stop_header)

        links = ptn.getEdges()
        if write_links or write_loads or write_headways:
            links.sort(key=Link.getId)

        if write_links:
            if not link_file_name:
                link_file_name = config.getStringValue("default_edges_file")
            if not link_header:
                link_header = config.getStringValue("edges_header")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue("gen_conversion_length")
            if modality != "":
                link_file_name = add_modality_prefix(modality, link_file_name)
            CsvWriter.writeListStatic(link_file_name, links,
                lambda l: l.toCsvStrings(conversion_factor_length), header=link_header)

        if write_loads:
            if not load_file_name:
                load_file_name = config.getStringValue("default_loads_file")
            if not load_header:
                load_header = config.getStringValue("loads_header")
            if modality != "":
                load_file_name = add_modality_prefix(modality, load_file_name)
            CsvWriter.writeListStatic(load_file_name, links, Link.toCsvLoadStrings, header=load_header)

        if write_headways:
            if not headway_file_name:
                headway_file_name = config.getStringValue("default_headways_file")
            if not headway_header:
                headway_header = config.getStringValue("headways_header")
            if modality != "":
                headway_file_name = add_modality_prefix(modality, headway_file_name)
            CsvWriter.writeListStatic(headway_file_name, links, Link.toCsvHeadwayStrings, header=headway_header)

        if write_ptn_infrastructure_map:
            if not ptn_infrastructure_map_file_name:
                ptn_infrastructure_map_file_name = config.getStringValue("filename_ptn_infrastructure_map_file")
            if not ptn_infrastructure_map_header:
                ptn_infrastructure_map_header = config.getStringValue("ptn_infrastructure_map_header")
            if modality != "":
                ptn_infrastructure_map_file_name = add_modality_prefix(modality, ptn_infrastructure_map_file_name)
            map_writer = CsvWriter(ptn_infrastructure_map_file_name, ptn_infrastructure_map_header)
            for ptn_link in links:
                order_index = 1
                for infra_link in ptn_link.getUnderlyingInfrastructure().getEdges():
                    map_writer.writeLine([str(ptn_link.getId()), str(order_index), str(infra_link.getId())])
                    order_index += 1
            map_writer.close()

        if write_geo_coordinates:
            if not geo_coordinates_file_name:
                geo_coordinates_file_name = config.getStringValue("default_stops_coordinates_file")
            if not geo_coordinates_header:
                geo_coordinates_header = config.getStringValue("stops_coordinates_header")
            if modality != "":
                geo_coordinates_file_name = add_modality_prefix(modality, geo_coordinates_file_name)
            CsvWriter.writeListStatic(geo_coordinates_file_name, ptn.getNodes(), lambda s: [str(s.getId()), str(s.getLatitude()), str(s.getLongitude())], Stop.getId, geo_coordinates_header)

