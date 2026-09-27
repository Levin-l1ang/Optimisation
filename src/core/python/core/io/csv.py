import logging
import os
from typing import TypeVar, Callable, Any, List

from core.exceptions.output_exceptions import OutputFileException
from core.exceptions.input_exceptions import InputFileException


class CsvReader:
    """
    File to process a csv file. Comments in the form of "#..." are trimmed from
    the end of the lines. Empty lines are
    ignored. Other lines will be split by ";" and processed by a given
    Processor method.
    """
    @staticmethod
    def readCsv(file_name: str, processor: Callable[[List[str], int], None]):
        """
        Read the file with the given name line by line and process each line
        with the given processor. The lines are
        trimmed for comments ("#...") and whitespaces before splitted by ";".
        The tokens are then given to a processor
        function to process the content of the line
        :param file_name: the name of the file to read
        :param processor: the processor to process every line. First argument
        are the tokens of the line, second
        argument the line number (used for error messages)
        """
        logger = logging.getLogger(__name__)
        logger.debug("Reading file {}".format(file_name))
        try:
            input_file = open(file_name, newline='', encoding="utf8")
        except IOError:
            raise InputFileException(file_name)

        with input_file:
            for line_number, line in enumerate(input_file):
                line = line.split('#')[0].strip()
                if not line:
                    continue
                else:
                    tokens = line.split(";")
                    tokens = [token.strip() for token in tokens]
                    processor(tokens, line_number)


class CsvWriter:
    """
    Helper class to write csv-Files, used for the formatting of the files. All
    IOExceptions are
    raised to the caller.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, file_name: str, header: str=None):
        """
        Create a new CsvWriter instance for a specific file. The file is opened
        with the given relative file name.
        :param file_name: the relative file name
        :param header: the header to write in the first line as a comment
        """
        try:
            self.logger.debug("Writing to file {}".format(file_name))

            if os.path.dirname(file_name) and not os.path.exists(os.path.dirname(file_name)):
                os.makedirs(os.path.dirname(file_name))
            self.file = open(file_name, 'w')
            if header:
                self.file.write("# {}\n".format(header))
        except (IOError, OSError) as e:
            if self.file:
                self.file.close()
            self.logger.error(e)
            raise OutputFileException(file_name)

    def writeLine(self, values: List[str]) -> None:
        """
        Write a line to the csv-file. The given values are csv-formatted and
        written to the file of this writer
        instance.
        :param values: the values to write
        """
        self.file.write("; ".join(values) + "\n")

    def close(self) -> None:
        """
        Close the file after all writing has been done
        """
        self.file.close()

    T = TypeVar("T")

    def writeList(self, output_list: List[T], output_function: Callable[[T], List[str]],
                  key_function: Callable[[T], Any]=None) -> None:

        """
        Write the given list. The list is sorted by the key_function
        beforehand, if it is given. For each element in the
        output_list, the output_function is used to determine the string to
        output.
        :param output_list: the list of elements to write
        :param output_function: the function to determine the actual output
        :param key_function: the function to sort the elements by
        """
        sorted_list = list(output_list)
        if key_function:
            sorted_list.sort(key=key_function)
        for element in sorted_list:
            self.writeLine(output_function(element))

    @staticmethod
    def writeListStatic(file_name: str, output_list: List[T],
                        output_function: Callable[[T], List[str]],
                        key_function: Callable[[T], Any]=None,
                        header: str = None) -> None:
        """
        Write the given list to the given file. The list is sorted by the
        key_function beforehand, if it is
        given. For each element in the collection, the output_function is used
        to determine the string to output.
        :param file_name: the path of the file to write
        :param output_list: the list of elements to write
        :param output_function: the function to determine the actual output
        :param key_function: the function to sort the elements by
        :param header: the header to use
        """
        writer = CsvWriter(file_name, header)
        writer.writeList(output_list, output_function, key_function)
        writer.close()

    @staticmethod
    def shortenDecimalValueForOutput(value: float) -> str:
        """
        Shorten the given double value to a string representation with at most five decimal places.
        :param value: the value to shorten
        :return: the shortened representation
        """
        return "{:.5f}".format(value).rstrip("0").rstrip(".")


    @staticmethod
    def shortenDecimalValueIfItsDecimal(value: str) -> str:
        """
        Use {@link #shortenDecimalValueForOutput(double)} on numbers, return the input for the rest
        :param value: the value to shorten
        :return: the input value or the shortened representation, if its a number
        """
        try:
            return CsvWriter.shortenDecimalValueForOutput(float(value))
        except ValueError:
            return value
