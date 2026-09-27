from typing import Dict, List

from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.io.csv import CsvReader
from core.model.station_limit import StationLimit
from core.util.config import Config


class StationLimitReader:
    """
    Class containing all methods to read station limits. Use a CsvReader with appropriate processing methods as an
    argument to read the files.
    """

    @staticmethod
    def read(station_limits: Dict[int, StationLimit] = None, file_name: str = "",
             config: Config = Config.getDefaultConfig()) -> Dict[int, StationLimit]:
        """
        Read the given file and add them to the dict. If no dict is given, a new one is created.
        :param station_limits: the dict to store the station limits. Limits are stored by stop id
        :param file_name: the file name to read
        :param config: the config to read the file name from. This will only happen if the file name is not
        given
        """
        if not station_limits:
            station_limits = {}
        if not file_name:
            file_name = config.getStringValue("filename_station_limit_file")
        reader = StationLimitReader(file_name, station_limits)
        CsvReader.readCsv(file_name, reader._process_station_limit_line)
        return station_limits

    def __init__(self, file_name: str, station_limits: Dict[int, StationLimit]):
        self._file_name = file_name
        self._station_limits = station_limits

    def _process_station_limit_line(self, args: List[str], line_number: int) -> None:
        if len(args) != 5:
            raise InputFormatException(self._file_name, len(args), 5)
        try:
            stop_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 1, line_number, "int", args[0])
        try:
            min_wait_time = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 2, line_number, "int", args[1])
        try:
            max_wait_time = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 3, line_number, "int", args[2])
        try:
            min_change_time = int(args[3])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 4, line_number, "int", args[3])
        try:
            max_change_time = int(args[4])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 5, line_number, "int", args[4])
        self._station_limits[stop_id] = StationLimit(stop_id, min_wait_time, max_wait_time, min_change_time,
                                                     max_change_time)
