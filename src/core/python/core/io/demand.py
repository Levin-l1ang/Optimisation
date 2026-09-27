from typing import List

from core.exceptions.input_exceptions import (InputFormatException,
                                              InputTypeInconsistencyException)

from core.io.csv import CsvReader
from core.model.demandPoint import DemandPoint
from core.util.config import Config


class DemandReader:
    """
    Class to process csv-lines, formatted in the LinTim Demand.giv format. Use
    a CsvReader with the appropriated processiong methods as a BiConsumer
    argument to tread the files.
    """

    def __init__(self, sourceFileName: str, demand: List[DemandPoint],
                 conversion_factor_coordinates: float = 1):
        """
        Constructor of a DemandReader for a demand collection and a given
        filename. The fiven name will not influence the read file but hte used
        name in any error message, so be sure to use the same name in here and
        in the CsvReader.
        :param sourceFileName   source file name for exceptions
        :param demand   collection of demand points
        :param conversion_factor_coordinates: the conversion factor for the demand point coordinates
        """
        self.sourceFileName = sourceFileName
        self.demand = demand
        self.conversion_factor_coordinates = conversion_factor_coordinates


    def process_demand_line(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a demand line.
        :param args     the content of the line.
        :param line_number   the line number, used fot error handling
        :raise exceptions   if the line does not contain exactly 6 entries
                            if the specific types of the entries do not match
                            the expectations
        """
        if len(args) != 6:
            raise InputFormatException(self.sourceFileName, len(args), 6)
        try:
            stopId = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 1,
                                                  line_number, "int", args[0])
        shortName = args[1]
        longName = args[2]
        try:
            xCoordinate = float(args[3]) * self.conversion_factor_coordinates
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 4,
                                                  line_number, "float", args[3])
        try:
            yCoordinate = float(args[4])  * self.conversion_factor_coordinates
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 5,
                                                  line_number, "float", args[4])
        try:
            demandAtDemandPoint = int(args[5])
        except ValueError:
            raise InputTypeInconsistencyException(self.sourceFileName, 6,
                                                  line_number, "int", args[5])
        demandPoint = DemandPoint(stopId, shortName, longName, xCoordinate,
                                  yCoordinate, demandAtDemandPoint)
        self.demand.append(demandPoint)

    @staticmethod
    def read(demand: List[DemandPoint] = None, demand_file_name: str = "", conversion_factor_coordinates: float = None,
             config: Config = Config.getDefaultConfig()) -> List[DemandPoint]:
        if not demand:
            demand = []
        if not demand_file_name:
            demand_file_name = config.getStringValue("default_demand_file")
        if not conversion_factor_coordinates:
            conversion_factor_coordinates = config.getDoubleValue("gen_conversion_coordinates")
        reader = DemandReader(demand_file_name, demand, conversion_factor_coordinates)
        CsvReader.readCsv(demand_file_name, reader.process_demand_line)
        return demand
