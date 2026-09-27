import logging
from typing import List

from core.exceptions.data_exceptions import DataIndexNotFoundException, DataRidepoolingAreaNotConnectedException
from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.exceptions.output_exceptions import OutputFileException
from core.io.csv import CsvReader, CsvWriter
from core.model.graph import Graph
from core.model.ridepooling import RidepoolingPool, RidepoolingArea
from core.model.ptn import Link, Stop
from core.util.config import Config
from core.util.mm_utilities import add_modality_prefix


class RidepoolingPoolReader:
    """
    Class to process csv-lines, formatted in the LinTim Ridepooling-Pool.giv or Ridepooling-Concept.lin format.
    Use a CsvReader with the appropriate processing methods as an argument to read the files.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, ridepool_file_name: str, ridepool_vehicle_frequency_file_name: str, ridepool_stretch_file_name: str,
                 ride_pool: RidepoolingPool, ptn: Graph[Stop, Link], read_number_vehicles: bool, read_vehicle_frequencies: bool,
                 read_stretch_factors: bool):
        """
        Constructor of a RidepoolingPoolReader for a ride pool or ridepooling concept (depending on read_number_vehicles) and a given
        filename. The given name will not influence the read file but the used name in any error message, so be sure to
        use the same name in here and in the CsvReader!
        :param ridepool_file_name: source file name for exceptions
        :param ridepool_vehicle_frequency_file_name: source file name (vehicle frequencies) for exceptions
        :param ridepool_stretch_file_name: source file name (edge stretch factors) for exceptions
        :param ride_pool: ride pool
        :param ptn: the base ptn
        :param read_number_vehicles: whether a ride pool or a ridepooling concept with number of vehicles is read
        :param read_vehicle_frequencies: Whether to read the vehicle frequencies values or not
        :param read_stretch_factors: Whether to read the edge stretch factors or not
        """
        self.ridepool_file_name = ridepool_file_name
        self.ridepool_vehicle_frequency_file_name = ridepool_vehicle_frequency_file_name
        self.ridepool_stretch_file_name = ridepool_stretch_file_name
        self.ride_pool = ride_pool
        self.ptn = ptn
        self.read_number_vehicles = read_number_vehicles
        self.read_vehicle_frequencies = read_vehicle_frequencies
        self.read_stretch_factros = read_stretch_factors

    def process_ride_pool_line(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a ridepooling pool or rideconcept line.
        :param args: the content of the line
        :param line_number: the line number, used for error handling
        :param period_length: period length to compute the alpha if not read froom the file
        """
        if not self.read_number_vehicles and len(args) != 2:
            raise InputFormatException(self.ridepool_file_name, len(args), 2)
        elif self.read_number_vehicles and len(args) != 3:
            raise InputFormatException(self.ridepool_file_name, len(args), 3)
        try:
            area_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.ridepool_file_name, 1, line_number, "int", args[0])
        try:
            link_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.ridepool_file_name, 2, line_number, "int", args[1])
        if self.read_number_vehicles:
            try:
                nb_vehicles = int(args[2])
            except ValueError:
                raise InputTypeInconsistencyException(self.ridepool_file_name, 3, line_number, "int", args[2])
        else:
            nb_vehicles = 0
        flag = False
        try:
            area = self.ride_pool.getArea(area_id)
        except KeyError:
            area = RidepoolingArea(area_id, nb_vehicles=nb_vehicles)
            flag = True
        link = self.ptn.getEdge(link_id)
        if not link:
            raise DataIndexNotFoundException("Link", link_id)
        area.addLink(link)
        if flag: #add the area only if it is a new one
            # We must allow to add multiple areas with the same edge set and vehicle frequencies. The area which is now read and
            # may now coincide with some other already contained area, can be extended in a further line with more edges to become a distinct area
            self.ride_pool.addArea(area, allow_multiple_areas=True)


    def process_ride_pool_distribution_line(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a ridepooling vehicle frequencies file.
        :param args: the content of the line
        :param line_number: the line number, used for error handling
        """
        try:
            area_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.ridepool_vehicle_frequency_file_name, 1, line_number, "int", args[0])
        try:
            link_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.ridepool_vehicle_frequency_file_name, 2, line_number, "int", args[1])
        try:
            distribution = float(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.ridepool_vehicle_frequency_file_name, 3, line_number, "float", args[2])
        success = self.ride_pool.getArea(area_id).setDistribution(link_id, distribution)
        if not success:
            if area_id not in [area.getId() for area in self.ride_pool.getAreas()]:
                raise DataIndexNotFoundException("area", area_id)
            else:
                raise DataIndexNotFoundException(f"Link with index {link_id} in area", area_id)


    def process_ride_pool_stretch_line(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a ridepooling stretch factors file.
        :param args: the content of the line
        :param line_number: the line number, used for error handling
        """
        try:
            area_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.ridepool_stretch_file_name, 1, line_number, "int", args[0])
        try:
            link_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.ridepool_stretch_file_name, 2, line_number, "int", args[1])
        try:
            stretch_factor = float(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.ridepool_stretch_file_name, 3, line_number, "float", args[2])
        success = self.ride_pool.getArea(area_id).setStretchFactor(link_id, stretch_factor)
        if not success:
            if area_id not in [area.getId() for area in self.ride_pool.getAreas()]:
                raise DataIndexNotFoundException("area", area_id)
            else:
                raise DataIndexNotFoundException(f"Link with index {link_id} in area", area_id)

    @staticmethod
    def read(ptn: Graph[Stop, Link], read_areas: bool = True, read_number_vehicles: bool = True, read_vehicle_frequencies: bool = False,
             read_stretch_factors: bool = False, ridepool_file_name: str = "", ridepool_vehicle_frequency_file_name: str = "", ridepool_stretch_file_name: str = "",
             ride_pool: RidepoolingPool = None, rpool_cost: int = None, config: Config = Config.getDefaultConfig(), modality: str = "") -> RidepoolingPool:
        """
        Read the ride pool or the ridepooling concept, depending on read_number_vehicles. Read areas will be added to
        the given RidepoolingPool, if there is one.
        :param ptn: the base PTN
        :param read_areas: Whether to read the areas
        :param read_number_vehicles: Whether to read the number of vehicles of each area, i.e. whether to read a ride pool or ridepooling concept
        :param read_vehicle_frequencies: whether to read the vehicle frequencies from the ridepool_vehicle frequenies_file
        :param read_stretch factors: whether to read stretch factors for the length of every edge in every area from the ridepool_stretch_file
        :param ridepool_file_name: the ride pool file name to read.
        :param ridepool_vehicle_frequency_file_name: the ride pool distribution file name to read. Contains information on the vehicle frequencies on each edge.
        :param ridepool_stretch_file_name: the ride pool stretch factors file name to read. Contains stretch factors for the length (usually minimal duration) of every edge.
        :param ride_pool: a given ridepooling pool to add the read areas to. If there is none, a new RidepoolingPool will be created.
        :param rpool_cost: costs of a ridepooling vehicle. If not specified, config value is used.
        :param config: The config to use
        :param modality: Modality prefix that will be added to the filenames separated by a dot
        :type modality: str
        :return: the ride pool with the added areas.
        """
        if not read_areas and read_number_vehicles:
            RidepoolingPoolReader.logger.warning("Can not read number of vehicles but no areas, will read areas as well!")
            read_areas = True
        if not rpool_cost:
            rpool_cost = config.getDoubleValue("rpool_costs_fixed", modality)
        if not ride_pool:
            ride_pool = RidepoolingPool(rpool_cost)
        if read_areas and not ridepool_file_name:
            if read_number_vehicles:
                ridepool_file_name = config.getStringValue("filename_rc_file")
            else:
                ridepool_file_name = config.getStringValue("filename_rpool_file")
        if read_vehicle_frequencies and not ridepool_vehicle_frequency_file_name:
            if read_number_vehicles:
                ridepool_vehicle_frequency_file_name = config.getStringValue("filename_rc_vehicle_frequencies_file")
            else:
                ridepool_vehicle_frequency_file_name = config.getStringValue("filename_rpool_vehicle_frequencies_file")
        if read_stretch_factors and not ridepool_stretch_file_name:
            ridepool_stretch_file_name = config.getStringValue("filename_rpool_stretch_factors_file")

        if modality != "":
            ridepool_file_name = add_modality_prefix(modality, ridepool_file_name)
            ridepool_vehicle_frequency_file_name = add_modality_prefix(modality, ridepool_vehicle_frequency_file_name)
            ridepool_stretch_file_name = add_modality_prefix(modality, ridepool_stretch_file_name)
        reader = RidepoolingPoolReader(ridepool_file_name, ridepool_vehicle_frequency_file_name, ridepool_stretch_file_name, ride_pool, ptn, read_number_vehicles, read_vehicle_frequencies, read_stretch_factors)
        if read_areas:
            CsvReader.readCsv(ridepool_file_name, reader.process_ride_pool_line)
        area_id = ride_pool.testConnected()
        if area_id != -1:
            raise DataRidepoolingAreaNotConnectedException(area_id)
        if read_vehicle_frequencies:
            CsvReader.readCsv(ridepool_vehicle_frequency_file_name, reader.process_ride_pool_distribution_line)
        if read_stretch_factors:
            CsvReader.readCsv(ridepool_stretch_file_name, reader.process_ride_pool_stretch_line)
        return ride_pool


class RidepoolingPoolWriter:
    """
    Class implementing writing ridepools and concepts as static methods. Use the static methods to write.
    """
    logger = logging.getLogger(__name__)

    @staticmethod
    def write(pool: RidepoolingPool, write_pool: bool = True, write_ride_concept: bool = True, write_distribution: bool = False, write_stretch_factors: bool = False,
              pool_file_name: str = "", pool_header: str = "", distribution_file_name: str = "", distribution_header: str = "",
              concept_file_name: str = "", concept_header: str = "", stretch_file_name: str = "", stretch_header: str = "", config: Config = Config.getDefaultConfig(), modality: str = ""):
        """
        Write the given ridepool, with or without the number of vehicles per area. write_pool and write_ride_concept
        can be used to determine what to write. If no file name or header is given and the respective file should be
        written, the values are read from the given config (or the default config, if there is none).
        :param pool: the ridepooling pool to write
        :param write_pool: whether to write the given pool
        :param write_ride_concept: whether to write additionally the number of vehicles per area
        :param write_distribution: whether to write the vehicle frequencies file
        :param write_stretch_factors: whether to write the stretch factors file
        :param pool_file_name: the name of the file to write the pool to
        :param pool_header: the header to write in the pool file
        :param distribution_file_name: the name of the file to write the distribution to
        :param distribution_header: the header to write in the distribution file
        :param concept_file_name: the name of the file to write the ridepooling concept to
        :param concept_header: the header to write in the ridepooling concept file
        :param stretch_file_name: the name of the file to write the stretch factors to
        :param stretch_header: the header to write in the stretch factors file
        :param config: the config to use to read file names or headers, if necessary. Will use the default config if none is given
        :param modality: If given, this string is prefixed to the file name as a prefix separated by a dot
        :type modality: str
        """
        # Sort the areas first
        areas = pool.getAreas()
        areas.sort(key=RidepoolingArea.getId)
        if write_pool:
            if not pool_file_name:
                pool_file_name = config.getStringValue("filename_rpool_file")
            if not pool_header:
                pool_header = config.getStringValue("rpool_header")
            if modality != "":
                pool_file_name = add_modality_prefix(modality, pool_file_name)
            pool_writer = CsvWriter(pool_file_name, pool_header)
            for area in areas:
                for link in area.getEdges():
                    pool_writer.writeLine([str(area.getId()), str(link.getId())])
            pool_writer.close()
        if write_distribution:
            if write_pool:
                if not distribution_file_name:
                    distribution_file_name = config.getStringValue("filename_rpool_vehicle_frequencies_file")
                if not distribution_header:
                    distribution_header = config.getStringValue("rpool_vehicle_frequencies_header")
                if modality != "":
                    distribution_file_name = add_modality_prefix(modality, distribution_file_name)
                distribution_writer = CsvWriter(distribution_file_name, distribution_header)
                for area in areas:
                    for link in area.getEdges():
                        distribution_writer.writeLine([str(area.getId()), str(link.getId()), CsvWriter.shortenDecimalValueForOutput(pool.getVehicleFrequency(area.getId(), link.getId()))])
                distribution_writer.close()
            if write_ride_concept:
                if not distribution_file_name:
                    distribution_file_name = config.getStringValue("filename_rc_vehicle_frequencies_file")
                if not distribution_header:
                    distribution_header = config.getStringValue("rc_vehicle_frequencies_header")
                if modality != "":
                    distribution_file_name = add_modality_prefix(modality, distribution_file_name)
                distribution_writer = CsvWriter(distribution_file_name, distribution_header)
                for area in areas:
                    for link in area.getEdges():
                        distribution_writer.writeLine([str(area.getId()), str(link.getId()), CsvWriter.shortenDecimalValueForOutput(pool.getVehicleFrequency(area.getId(), link.getId()))])
                distribution_writer.close()
        if write_ride_concept:
            if not concept_file_name:
                concept_file_name = config.getStringValue("filename_rc_file")
            if not concept_header:
                concept_header = config.getStringValue("rc_header")
            if modality != "":
                concept_file_name = add_modality_prefix(modality, concept_file_name)
            concept_writer = CsvWriter(concept_file_name, concept_header)
            for area in areas:
                for link in area.getEdges():
                    concept_writer.writeLine(
                        [str(area.getId()), str(link.getId()), str(area.getNumberOfVehicles())])
            concept_writer.close()
        if write_stretch_factors:
            if not stretch_file_name:
                stretch_file_name = config.getStringValue("filename_rpool_stretch_factors_file")
            if not stretch_header:
                stretch_header = config.getStringValue("rpool_stretch_header")
            if modality != "":
                stretch_file_name = add_modality_prefix(modality, stretch_file_name)
            stretch_writer = CsvWriter(stretch_file_name, stretch_header)
            for area in areas:
                for link in area.getEdges():
                    stretch_writer.writeLine([str(area.getId()), str(link.getId()), CsvWriter.shortenDecimalValueForOutput(area.getStretchFactor(link.getId()))])
            stretch_writer.close()

