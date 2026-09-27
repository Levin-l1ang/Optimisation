from typing import Tuple, Set, List

from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.io.csv import CsvWriter, CsvReader
from core.util.config import Config


class RestrictedTurnReader:
    """
    Class containing all methods to read restricted turns. Use a CsvReader with the appropriate processing methods as
    an argument to read the files or simply call the read method.
    """

    def __init__(self, file_name: str, restricted_turns: Set[Tuple[int, int]]):
        self._file_name = file_name
        self._restricted_turns = restricted_turns

    def process_restricted_turn_line(self, args: List[str], line_number: int) -> None:
        if len(args) != 2:
            raise InputFormatException(self._file_name, len(args), 2)
        try:
            first_link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 1, line_number, "int", args[0])
        try:
            second_link_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self._file_name, 1, line_number, "int", args[0])
        self._restricted_turns.add((first_link_id, second_link_id))

    @staticmethod
    def read(file_name: str = "", is_infrastructure: bool = False, restricted_turns: Set[Tuple[int, int]] = None,
             config: Config = Config.getDefaultConfig()) -> Set[Tuple[int, int]]:
        """
        Read the given file and return the read restricted turns. If parameters are not given but needed, the respective
        values will be read from the given config.
        :param file_name: the file to read
        :param is_infrastructure: whether the restricted turns should be read for an infrastructure network (true) or
        a ptn (false). This influences the default file name to read.
        :param restricted_turns: the set to add the restricted turns to. Restricted turns are represented as a pair
        of link ids. Will use an empty set if none is given.
        :param config the config to read values from that are needed but not given
        """
        if not restricted_turns:
            restricted_turns = set()
        if not file_name and is_infrastructure:
            file_name = config.getStringValue("filename_turn_restrictions_infrastructure")
        elif not file_name:
            file_name = config.getStringValue("filename_turn_restrictions")
        reader = RestrictedTurnReader(file_name, restricted_turns)
        CsvReader.readCsv(file_name, reader.process_restricted_turn_line)
        return restricted_turns


class RestrictedTurnWriter:
    """
    Class implementing the writing of restricted turns
    """

    @staticmethod
    def write(restricted_turns: Set[Tuple[int, int]], is_infrastructure: str = False, file_name: str = "",
              header: str = "", config: Config = Config.getDefaultConfig()):
        """
        Write the given restricted turns. If the filename or the header is not given, the values will be read from
        the given config object.
        :param restricted_turns: the restricted turns to write
        :param is_infrastructure: whether we are writing turns for the infrastructure or the ptn. This will determine
        the default file name to write to (if no other filename is given)
        :param file_name: the filename to write to
        :param header: the header to use
        :param config: the config to read values from that are needed but not given
        """
        if not file_name:
            if is_infrastructure:
                file_name = config.getStringValue("filename_turn_restrictions_infrastructure")
            else:
                file_name = config.getStringValue("filename_turn_restrictions")
        if not header:
            header = config.getStringValue("restricted_turns_header")
        CsvWriter.writeListStatic(file_name, list(restricted_turns), lambda x: [str(x[0]), str(x[1])], header=header)