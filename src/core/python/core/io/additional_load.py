from typing import Tuple, Dict, List

from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.io.csv import CsvReader
from core.util.config import Config


class AdditionalLoadReader:
    """
    Class containing all methods to read additional loads. Use a CsvReader with appropriate processing methods as an
    argument to read the files.
    """

    @staticmethod
    def read(loads: Dict[int, Dict[Tuple[int, int], float]] = None, file_name: str = "",
             config: Config = Config.getDefaultConfig) -> Dict[int, Dict[Tuple[int, int], float]]:
        """
        Read the given file and add the read values to the dict. If no dict is given, a new one is created.
        :param loads: the dict to store the loads in. Loads are stored by link id and (directed) pair of stops
        :param file_name: the file name to read
        :param config: the config to read the file name from. This will only happen if the file name is not given
        """
        if not loads:
            loads = {}
        if not file_name:
            file_name = config.getStringValue("filename_additional_load_file")
        reader = AdditionalLoadReader(file_name, loads)
        CsvReader.readCsv(file_name, reader._process_additional_load_line)
        return loads

    def __init__(self, file_name: str, loads: Dict[int, Dict[Tuple[int, int], float]]):
        self._file_name = file_name
        self._loads = loads

    def _process_additional_load_line(self, args: List[str], line_number: int) -> None:
        if len(args) != 4:
            raise InputFormatException(self._file_name, len(args), 4)
        try:
            link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 1, line_number, "int", args[0])
        try:
            left_stop_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 2, line_number, "int", args[1])
        try:
            right_stop_id = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 3, line_number, "int", args[2])
        try:
            load = float(args[3])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 4, line_number, "float", args[3])
        if link_id not in self._loads:
            self._loads[link_id] = {}
        self._loads[link_id][left_stop_id, right_stop_id] = load
