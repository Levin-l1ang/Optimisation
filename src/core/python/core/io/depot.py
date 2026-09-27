from core.model.vehicle_scheduling import Depot
from typing import List

from core.exceptions.input_exceptions import (InputFormatException,
                                              InputTypeInconsistencyException)
from core.exceptions.depot_exceptions import NoDepotExceptionInDepotFile
from core.io.csv import CsvReader,CsvWriter
from core.util.config import Config

class DepotReader:
    """
    Class to process csv-lines, formatted in the LinTim Depot.giv format. Use
    a CsvReader with the appropriated processing methods as a BiConsumer
    argument to tread the files.
    """

    def __init__(self, sourceFileName: str, depot_list: List[Depot],
                 conversion_factor_coordinates: float = 1):
        """
        Constructor of a DepotReader for a depot list and a given
        filename. The given name will not influence the read file but the used
        name in any error message, so be sure to use the same name in here and
        in the read method.
        :param sourceFileName   source file name for exceptions
        :param depot_list   collection of depots points
        :param conversion_factor_coordinates: the conversion factor for the depot coordinates
        """
        self.sourceFileName = sourceFileName
        self.depot_list = depot_list
        self.conversion_factor_coordinates = conversion_factor_coordinates


    def process_depot_line(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a depot line.
        :param args     the content of the line.
        :param line_number   the line number, used for error handling
        :raise exceptions   if the line does not contain exactly 4 entries
                            if the specific types of the entries do not match
                            the expectations
        """
        if len(args) != 4:
            raise InputFormatException(self.sourceFileName, len(args), 4)
        try:
            depot_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 1,
                                                  line_number, "int", args[0])
        try:
            xCoordinate = float(args[1]) * self.conversion_factor_coordinates  # transform coordinates to get right units
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 2,
                                                  line_number, "float", args[1])
        try:
            yCoordinate = float(args[2])  * self.conversion_factor_coordinates  # transform coordinates to get right units
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 3,
                                                  line_number, "float", args[2])
        try:
            number_of_vehicles = int(args[3])
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 4,
                                                  line_number, "int", args[3])
        depot = Depot(depot_id, xCoordinate, yCoordinate, number_of_vehicles)
        self.depot_list.append(depot)

    @staticmethod
    def read(depot_list: List[Depot] = None, depot_file_name: str = "", conversion_factor_coordinates: float = None,
             config: Config = Config.getDefaultConfig()) -> List[Depot]:
        if not depot_list:
            depot_list = []
        if not depot_file_name:
            depot_file_name = config.getStringValue("filename_depot_file")
        if not conversion_factor_coordinates:
            conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates")
        reader = DepotReader(depot_file_name, depot_list, conversion_factor_coordinates)
        CsvReader.readCsv(depot_file_name, reader.process_depot_line)
        if depot_list == []:
            raise NoDepotExceptionInDepotFile(depot_file_name)
        return depot_list

class DepotWriter:
    """
    Class implementing write methods for a list of depots.
    """

    @staticmethod
    def write(depots: List[Depot],
              config: Config = Config.getDefaultConfig(),
              file_name: str = None,
              header: str = None,
              conversion_factor_coordinates: float = None) -> None:
        """
        Write the given depots to the file given in the config. Output will be
        LinTim compatible format. If no file names, conversion_factors or headers are given,
        default names will be used instead.
        :param depots    the depots to write.
        :param config   the config to read from if necessary values are not
        given
        :param file_name    the file name to write the depots to
        :param header      the header to write in the depot file
        """

        if not file_name:
            file_name = config.getStringValue("filename_depot_file")
        if not header:
            header = config.getStringValue("depot_header")
        if not conversion_factor_coordinates:
            conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates")
        for depot in depots: # transform the coordinates back
            depot.setXCoordinate(depot.getXCoordinate()/conversion_factor_coordinates)
            depot.setYCoordinate(depot.getYCoordinate()/conversion_factor_coordinates)

        CsvWriter.writeListStatic(file_name, depots, Depot.toCsvStrings, header=header)