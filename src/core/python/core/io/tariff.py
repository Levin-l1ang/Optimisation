from typing import List, Dict, Set

from core.exceptions.exceptions import LinTimException
from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.exceptions.data_exceptions import DataZonePriceListInconsistencyException, DataStopInNoZoneException, DataStopInMultipleZonesException, DataPriceMatrixNotCompleteException
from core.io.csv import CsvReader, CsvWriter
from core.model.graph import Graph
from core.model.ptn import Stop, Link
from core.model.tariff import Zone, PriceMatrix, ZonePrices
from core.util.config import Config

class ZoneReader:
    """
    Class containing all methods to read zone information for tariff planning. Use a CsvReader with the
    appropriate processing methods as an argument to read the files.
    """

    def __init__(self, zone_file_name: str, ptn: Graph[Stop, Link], zones: List[Zone]):
        """
        Initialize a new Zone reader with the source files that should be read. The name of the file given here has no
        influence on the read file, but will be used for error handling, so be sure to give the same name as in the
        processor method.
        :param zone_file_name: the name of the zones file
        :param ptn: the ptn the zones correspond to
        :param zones: a list of zones that can be added aditionally
        """
        self._zone_file_name = zone_file_name
        self._ptn = ptn
        self._zones: Dict[int, Zone] = {}
        for zone in zones:
            self._zones[zone.getZoneId()] = zone
        self._stops: Set[Stop] = set(sum([zone.getStops() for zone in zones],[]))

    def process_zone_entry(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a zone line.
        :param args: the content of the line
        :param line_number: the line number, used for error handling
        """
        if len(args) != 2:
            raise InputFormatException(self._zone_file_name, len(args), 2)
        try:
            zone_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self._zone_file_name, 1, line_number, "int", args[0])
        try:
            stop_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self._zone_file_name, 2, line_number, "int", args[1])
        if zone_id not in self._zones:
            self._zones[zone_id] = Zone(zone_id, set())
        stop = self._ptn.getNode(stop_id)
        if not stop:
            raise LinTimException(f"Could not find stop with id {stop_id}, present in {self._zone_file_name}")
        if stop in self._stops:
            raise DataStopInMultipleZonesException(stop_id)
        self._stops.add(stop)
        self._zones[zone_id].addStop(stop)

    @staticmethod
    def read(ptn: Graph[Stop, Link], zone_file_name: str = "", zones: List[Zone] = None,
             config: Config = Config.getDefaultConfig()) -> List[Zone]:
        """
        Read the given file and return a list of the zones.
        :param ptn: the ptn the zones refer to
        :param zone_file_name: the name of the zone file
        :param zones: list of zones 
        :param config: the config to read. Will be used, if some values are not given.
        :return: a list of zones that can be added aditionally
        """
        if zones is None:
            zones = []
        if not zone_file_name:
            zone_file_name = config.getStringValue("filename_tariff_zone_file")
        reader = ZoneReader(zone_file_name, ptn, zones)
        CsvReader.readCsv(zone_file_name, reader.process_zone_entry)
        zones = list(reader._zones.values())

        # Test, if each node is assigned to at least one zone
        for stop in ptn.getNodes():
            if stop not in reader._stops:
                raise DataStopInNoZoneException(stop.getId())

        return zones


class ZoneWriter:
    """
    Class implementing the writing of a zone file as a static method.
    """

    @staticmethod
    def write(zones: List[Zone], zone_file_name: str = "", zone_header: str = "",
              config = Config.getDefaultConfig()) -> None:
        """
        Write the given zones list to the specified file. If filename and/or header are not given for data to write, the
        respective values will be read from the given config (or from the default config, if none is given).
        :param zones: the list of zones to write
        :param config: the config to read. Will be used, if some values are not given for data to write.
        :param zone_file_name: the name of the file to write the zones
        :param zone_header: the header to write in the zone file
        """
        if not zone_file_name:
            zone_file_name = config.getStringValue("filename_tariff_zone_file")
        if not zone_header:
            zone_header = config.getStringValue("zone_header")
        zones.sort(key=Zone.getZoneId)
        writer = CsvWriter(zone_file_name, zone_header)
        for zone in zones:
            stop_list = list(zone.getStops())
            stop_list.sort(key=Stop.getId)
            for stop in stop_list:
                writer.writeLine([str(zone.getZoneId()), str(stop.getId())])
        writer.close()


class ZonePriceReader:
    """
    Class containing all methods to read zone price information for tariff planning. Use a CsvReader with the
    appropriate processing methods as an argument to read the files.
    """

    def __init__(self, zone_price_file_name: str, prices: ZonePrices):
        """
        Initialize a new price reader with the source files that should be read. The name of the file given here has no
        influence on the read file, but will be used for error handling, so be sure to give the same name as in the
        processor method.
        :param zone_price_file_name: the name of the price file
        :param prices: zone prices that can be added aditionally
        """
        self._zone_price_file_name = zone_price_file_name
        self._prices = prices
        self._nb_zones = set()

    def process_price(self, args: List[str], line_number: int) -> None:
        """
        Process the contents of a price line.
        :param args: the content of the line
        :param line_number: the line number, used for error handling
        """
        if len(args) != 2:
            raise InputFormatException(self._zone_price_file_name, len(args), 2)
        try:
            nr_zone = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self._zone_price_file_name, 1, line_number, "int", args[0])
        try:
            price = float(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self._zone_price_file_name, 2, line_number, "float", args[1])
        self._prices.setPrice(nr_zone, price)
        self._nb_zones.add(nr_zone)

    @staticmethod
    def read(zone_price_file_name: str = "", prices: ZonePrices = ZonePrices(),
             config: Config = Config.getDefaultConfig()) -> ZonePrices:
        """
        Read the given file and return a list of the prices indexed by the number of zones. First entry of returned list is None.
        :param zone_price_file_name: the name of the price file
        :param prices: zone prices object
        :param config: the config to read. Will be used, if some values are not given.
        :return: the zone prices.
        """
        if not zone_price_file_name:
            zone_price_file_name = config.getStringValue("filename_tariff_zone_price_file")
        reader = ZonePriceReader(zone_price_file_name, prices)
        CsvReader.readCsv(zone_price_file_name, reader.process_price)

        # Test, if prices are specified for all values from 1...n
        for i in range(1,reader._prices.getMaxZones()+1):
            if i not in reader._nb_zones:
                raise DataZonePriceListInconsistencyException()
        return reader._prices


class ZonePriceWriter:
    """
    Class implementing the writing of a price file as a static method.
    """

    @staticmethod
    def write(prices: ZonePrices, zone_price_file_name: str = "", zone_price_header: str = "",
              config: Config = Config.getDefaultConfig()) -> None:
        """
        Write the given prices list to the specified file. If filename and/or header are not given for data to write, the
        respective values will be read from the given config (or from the default config, if none is given).
        :param prices: the zone prices to write
        :param zone_price_file_name: the name of the file to write the prices
        :param zone_price_header: the header to write in the price file
        :param config: the config to read. Will be used, if some values are not given for data to write.
        """
        if not zone_price_file_name:
            zone_price_file_name = config.getStringValue("filename_tariff_zone_price_file")
        if not zone_price_header:
            zone_price_header = config.getStringValue("zone_price_header")
        writer = CsvWriter(zone_price_file_name, zone_price_header)
        for nr_zone in range(1, prices.getMaxZones()+1):
            writer.writeLine([str(nr_zone), str(prices.getPrice(nr_zone))])
        writer.close()


class PriceMatrixReader:
    """
    Class containing all methods to read a price matrix for tariff planning. Use a CsvReader with the
    appropriate processing methods as an argument to read the files.
    """

    def __init__(self, matrix: PriceMatrix, file_name: str, ptn: Graph[Stop, Link]):
        self._matrix = matrix
        self._file_name = file_name
        self._ptn = ptn

    def process_matrix_entry(self, args: List[str], line_number) -> None:
        if len(args) != 3:
            raise InputFormatException(self._file_name, len(args), 3)
        try:
            origin_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 1, line_number, "int", args[0])
        try:
            destination_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 2, line_number, "int", args[1])
        try:
            price = float(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 3, line_number, "float", args[2])
        self._matrix.setValue(origin_id, destination_id, price)

    @staticmethod
    def read(ptn: Graph[Stop, Link], file_name: str = "", matrix: PriceMatrix = None,
             config: Config = Config.getDefaultConfig()) -> PriceMatrix:
        if not file_name:
            file_name = config.getStringValue("filename_tariff_price_matrix_file")
        if not matrix:
            matrix = PriceMatrix()
        reader = PriceMatrixReader(matrix, file_name, ptn)
        CsvReader.readCsv(file_name, reader.process_matrix_entry)
        for origin in ptn.getNodes():
            for destination in ptn.getNodes():
                if origin != destination:
                    try: 
                        value = matrix.getValue(origin.getId(), destination.getId())
                    except KeyError:
                        raise DataPriceMatrixNotCompleteException(origin.getId(), destination.getId())
        return matrix


class PriceMatrixWriter:
    """
    Class implementing the writing of a price matrix file as a static method.
    """

    @staticmethod
    def write(matrix: PriceMatrix, file_name: str = "", header: str = "",
              config: Config = Config.getDefaultConfig()) -> None:
        """
        Write the given price matrix to the specified file. If filename and/or header are not given for data to write, the
        respective values will be read from the given config (or from the default config, if none is given).
        :param matrix: the price matrix to write
        :param file_name: the name of the file to write the price matrix
        :param header: the header to write in the price matrix file
        :param config: the config to read. Will be used, if some values are not given for data to write.
        """
        if not file_name:
            file_name = config.getStringValue("filename_tariff_price_matrix_file")
        if not header:
            header = config.getStringValue("price_matrix_header")
        CsvWriter.writeListStatic(file_name, matrix.getAllEntries(),
                                  lambda x: [str(x[0]), str(x[1]), str(x[2])],
                                  key_function=lambda x: x, header=header)

