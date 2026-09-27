from typing import List, Optional

from core.exceptions.input_exceptions import (InputFormatException,
                                              InputTypeInconsistencyException)
from core.model.graph import Graph
from core.model.impl.fullOD import FullOD
from core.model.impl.mapOD import MapOD
from core.model.od import OD, ODPair
from core.io.csv import CsvReader, CsvWriter
from core.model.ptn import Stop, Link
from core.model.infrastructure_network import InfraNode, InfraLink
from core.util.config import Config
from core.util.mm_utilities import add_modality_prefix


class ODReader:
    """
    Class to read files of od matrices.
    """

    def __init__(self, source_file_name: str, od: OD):
        """
        Constructor of an ODReader for a demand collection and a given file
        name. The given name will not influence the read file but the used name
        in any error message, so be sure to tuse the same name in here and in
        the CsvReader!
        """
        self.sourceFileName = source_file_name
        self.od = od

    def process_od_line(self, args: List[str], lineNumber: int) -> None:
        """
        Process the contents of an od matric line.
        :param args     the content of the line
        :param lineNumber   the number used for error handling
        :raise exceptions   if the line does not contain exactly 3 entries
                            if the specific types of the entries do not match
                            the expectations.
        """
        if len(args) != 3:
            raise InputFormatException(self.sourceFileName, len(args), 3)
        try:
            origin = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 1,
                                                  lineNumber, "int", args[0])
        try:
            destination = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 2,
                                                  lineNumber, "int", args[1])
        try:
            passengers = float(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 3,
                                                  lineNumber, "float", args[2])

        self.od.setValue(origin, destination, passengers)

    @staticmethod
    def read(od: Optional[OD] = None, size: Optional[int] = None, file_name: str = "",
             config: Config = Config.getDefaultConfig(), modality: str = "") -> OD:
        """
        Read the given file into an od object. If parameters are not given but needed,
        the respective values will be read from the given config.
        :param od: the od to fill. If not given, an empty MapOD will be used. If a size is given, a FullOD of the
        corresponding size will be used
        :param size: the size of the FullOD to use (if no od is given directly)
        :param file_name: the file name to read the od matrix from
        :param config: the config to read the parameters from that are not given
        :param modality: if given, this string is prefixed to the file name as a
        prefix separated by a dot.
        :return the read of matrix
        """
        if not od:
            if size:
                od = FullOD(size)
            else:
                od = MapOD()
        if not file_name:
            file_name = config.getStringValue("default_od_file")
        if modality != "":
            file_name = add_modality_prefix(modality, file_name)
        reader = ODReader(file_name, od)
        CsvReader.readCsv(file_name, reader.process_od_line)
        return od

    @staticmethod
    def readInfrastructureOd(od: OD | None, size: int | None = None, file_name: str = "", config: Config = Config.getDefaultConfig()) -> OD:
        """
        Read the given file into an od object. If parameters are not given but needed,
        the respective values will be read from the given config.
        :param od: the od to fill. If not given, an empty MapOD will be used. If a size is given, a FullOD of the
        corresponding size will be used
        :param size: the size of the FullOD to use (if no od is given directly)
        :param file_name: the file name to read the od matrix from
        :param config: the config to read the parameters from that are not given
        :return the read of matrix
        """
        if not file_name:
            file_name = config.getStringValue("filename_infrastructure_od_file")
        return ODReader.read(od, size, file_name, config)

    @staticmethod
    def readNodeOd(od: OD | None, size: int | None = None, file_name: str = "", config: Config = Config.getDefaultConfig()) -> OD:
        """
        Read the given file into an od object. If parameters are not given but needed,
        the respective values will be read from the given config.
        :param od: the od to fill. If not given, an empty MapOD will be used. If a size is given, a FullOD of the
        corresponding size will be used
        :param size: the size of the FullOD to use (if no od is given directly)
        :param file_name: the file name to read the od matrix from
        :param config: the config to read the parameters from that are not given
        :return the read of matrix
        """
        if not file_name:
            file_name = config.getStringValue("filename_od_nodes_file")
        return ODReader.read(od, size, file_name, config)


class ODWriter:
    """
    Class implementing the writing of an od matrix as a static method. Just
    call write(Graph, OD, Config) to write the od matrix to the file
    specified in the config.
    """
    @staticmethod
    def write(ptn: Graph[Stop, Link], od: OD, file_name: str= "", header: str= "",
              config: Config = Config.getDefaultConfig(), modality: str = "", write_complete_matrix = True):
        """
        Write the given od matrix to the file specified in the config by
        default_od_file. Will write all od pairs, including those with weight
        0.
        :param ptn     the ptn the od matrix is based on
        :param od   the od matrix to write
        :param config   Used for reading the values of default_od_file and
        od_header
        :param file_name   the file name to write the od matrix to
        :param header     the header to write in the od file
        :param modality   if given, this string is prefixed to the file name as a
        prefix separated by a dot. This can be used to write modality-specific
        od matrices.
        """

        od_pairs = []
        if not file_name:
            file_name = config.getStringValue("default_od_file")
        if not header:
            header = config.getStringValue("od_header")

        if modality != "":
            file_name = add_modality_prefix(modality, file_name)

        for origin in ptn.getNodes():
            for destination in ptn.getNodes():
                if not write_complete_matrix:
                    if od.getValue(origin.getId(), destination.getId()) > 0:
                        od_pairs.append(ODPair(origin.getId(), destination.getId(), od.getValue(origin.getId(), destination.getId())))
                else:
                    od_pairs.append(ODPair(origin.getId(), destination.getId(), od.getValue(origin.getId(), destination.getId())))
        CsvWriter.writeListStatic(file_name, od_pairs, ODPair.toCsvStrings, key_function=lambda x: (x.getOrigin(), x.getDestination()), header=header)


    @staticmethod
    def writeInfrastructureOd(infrastructure_network: Graph[InfraNode, InfraLink], od: OD, file_name: str= "", header: str= "",
              config: Config = Config.getDefaultConfig(), write_complete_matrix = True):
        """
        Write the given od matrix to the file specified in the config by
        default_od_file. Will write all od pairs, including those with weight
        0.
        :param infrastructure_graph: the infrastructure graph the od matrix is based on
        :type infrastructure_graph:Graph[InfraNode, InfraLink]
        :param od: the od matrix to write
        :type od: OD
        :param file_name: the file name to write the od matrix to
        :type file_name: str, optional
        :param header: the header to write in the od file
        :type header: str, optional
        :param config: Used for reading the values of default_od_file and od_header
        :type config: Config, optional
        """

        od_pairs = []
        if not file_name:
            file_name = config.getStringValue("filename_infrastructure_od_file")
        if not header:
            header = config.getStringValue("od_infrastructure_header")

        for origin in infrastructure_network.getNodes():
            for destination in infrastructure_network.getNodes():
                if not write_complete_matrix:
                    if od.getValue(origin.getId(), destination.getId()) > 0:
                        od_pairs.append(ODPair(origin.getId(), destination.getId(), od.getValue(origin.getId(), destination.getId())))
                else:
                    od_pairs.append(ODPair(origin.getId(), destination.getId(), od.getValue(origin.getId(), destination.getId())))
        CsvWriter.writeListStatic(file_name, od_pairs, ODPair.toCsvStrings, key_function=lambda x: (x.getOrigin(), x.getDestination()), header=header)

    @staticmethod
    def writeNodeOd(od: OD, file_name: str="", header: str="",
                    config: Config = Config.getDefaultConfig()):
        """
        Write the given od matrix to the file specified or the corresponding file name from the config. Will write
        only the od pairs with positive demand
        :param od: the od object to write
        :param file_name: the file to write the od data to
        :param header: the header to use
        :param config: the config to read parameters from that are needed but not given
        """
        if not file_name:
            file_name = config.getStringValue("filename_od_nodes_file")
        if not header:
            header = config.getStringValue("od_nodes_header")
        od_pairs = od.getODPairs()
        CsvWriter.writeListStatic(file_name, od_pairs, ODPair.toCsvStrings, header=header)
