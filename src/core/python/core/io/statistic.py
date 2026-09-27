import logging
import os.path
from typing import List

from core.exceptions.input_exceptions import InputFormatException, InputFileException
from core.io.csv import CsvReader, CsvWriter
from core.util.statistic import Statistic
from core.util.config import Config


class StatisticReader:
    """
    Class to process csv-lines, formatted in the LinTim statistic format. Use
    CsvReader with the appropriate processing methods as a BiConsumer argument
    to read the files.
    """

    def __init__(self, sourceFileName: str, statistic: Statistic):
        self.sourceFileName = sourceFileName
        self.statistic = statistic

    def process_statistic_line(self, args: List[str], lineNumber: int):
        """
        Process the contents of a statistic line.
        :param args     the content of the line
        :param lineNumber   the line number, used for error handling
        :raise exceptions   if the line does not exactly contain 2 entries
        """
        if len(args) != 2:
            raise InputFormatException(self.sourceFileName, len(args), 2)
        key = args[0]
        value = args[1]
        if len(value) > 2 and value.startswith("\"") and value.endswith("\""):
            value = value.strip("\"")
        self.statistic.setValue(key, value)

    @staticmethod
    def read(file_name: str = "", statistic: Statistic = Statistic.getDefaultStatistic(),
             config: Config = Config.getDefaultConfig()) -> Statistic:
        """
        Read the statistic defined by the given file name.
        :param config:
        :param file_name: the filename to read the statistic
        :param statistic    if statistic exists
        :return: the statistic
        """
        if not file_name:
            file_name = config.getStringValue("default_statistic_file")
        reader = StatisticReader(file_name, statistic)
        CsvReader.readCsv(file_name, reader.process_statistic_line)
        return statistic


class StatisticWriter:
    """
    Class implementing a static method to write a statistic. Call
    writeStatistic.
    """
    logger = logging.getLogger(__name__)

    @staticmethod
    def write(statistic: Statistic = Statistic.getDefaultStatistic(), file_name: str = "",
              config: Config = Config.getDefaultConfig(), append: bool = True) -> None:
        """
        Write the given statistic (or the default statistic, when none is given).
        :param statistic: the statistic to write. Will default to the static default statistic
        :param file_name: the file to write to. When none is given, this will be read from the config
        :param config: used to read the file name to write to when none is given
        :param append: whether to append to the statistic already present on disc or overwrite. When set to true,
            the current statistic will be read from disc.
        """
        if not file_name:
            file_name = config.getStringValue("default_statistic_file")
        toWrite = statistic
        if append:
            toWrite = StatisticWriter.getAppendedStatistic(statistic=statistic, file_name=file_name)

        CsvWriter.writeListStatic(file_name, list(toWrite.getData().items()),
                                  lambda item: [item[0], CsvWriter.shortenDecimalValueIfItsDecimal(item[1])],
                                  lambda item: item[0])

    @staticmethod
    def getAppendedStatistic(statistic: Statistic, file_name: str) -> Statistic:
        """
        Read the statistic at file_name, overwrite with all values from the provided statistic and returned the new
        statistic object
        :param statistic: the statistic containing the current values. Will be used to overwrite the values read
            from disc
        :param file_name: the file to read from disc
        :return: the appended statistic
        """
        if not os.path.exists(file_name):
            return statistic

        discStatistic = StatisticReader.read(statistic=Statistic(), file_name=file_name)
        for key in list(statistic.getData().keys()):
            discStatistic.setValue(key, statistic.getData().get(key))
        return discStatistic
