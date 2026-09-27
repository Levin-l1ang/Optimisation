import logging
from typing import Optional

from core.exceptions.data_exceptions import DataIndexNotFoundException, DataLinePoolCostInconsistencyException
from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.exceptions.line_exceptions import LineLinkNotAddableException
from core.io.csv import CsvReader, CsvWriter
from core.model.graph import Graph
from core.model.lines import LinePool, Line
from core.model.ptn import Link, Stop
from core.util.config import Config
from core.util.mm_utilities import add_modality_prefix


class LineReader:
    """
    Class to process csv-lines, formatted in the LinTim Line-Pool.giv, Line-Concept.lin  or Line-Pool-Cost.giv format.
    Use a CsvReader with the appropriate processing methods as an argument to read the files.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, line_collection_file_name: str,
                 line_pool_cost_file_name: str, merged_lines_file_name: str, line_names_file_name: str, line_colors_file_name: str, line_pool: LinePool,
                 ptn: Graph[Stop, Link], directed: bool,
                 read_frequencies: bool, conversion_factor_length: float = 1, modality: str = ""):
        """
        Constructor of a LinePoolReader for a line pool or line concept (depending on read_frequencies) and a given
        filename. The given name will not influence the read file but the used name in any error message, so be sure to
        use the same name in here and in the CsvReader!
        :param line_collection_file_name: source file name for exceptions
        :param line_pool_cost_file_name: source file name for exceptions
        :param merged_lines_file_name: source file name for exceptions
        :param line_names_file_name: source file name for exceptions
        :param line_colors_file_name: source file name for exceptions
        :param line_pool: line pool
        :param ptn: the base ptn
        :param directed: whether the links are directed
        :param read_frequencies: whether a line pool or a line concept with frequencies is read
        """
        self.line_collection_file_name = line_collection_file_name
        self.line_pool_cost_file_name = line_pool_cost_file_name
        self.merged_lines_file_name = merged_lines_file_name
        self.line_names_file_name = line_names_file_name
        self.line_colors_file_name = line_colors_file_name
        self.line_pool = line_pool
        self.ptn = ptn
        self.directed = directed
        self.read_frequencies = read_frequencies
        self.conversion_factor_length = conversion_factor_length
        self.read_lines = set()
        self.modality = modality

    def process_line_pool_line(self, args: [str], line_number: int) -> None:
        """
        Process the contents of a linepool or lineconcept line.
        :param args: the content of the line
        :param line_number: the line number, used for error handling
        """
        if not self.read_frequencies and len(args) != 3:
            raise InputFormatException(self.line_collection_file_name, len(args), 3)
        elif self.read_frequencies and len(args) != 4:
            raise InputFormatException(self.line_collection_file_name, len(args), 4)
        try:
            line_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.line_collection_file_name, 1, line_number, "int", args[0])
        try:
            link_number = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.line_collection_file_name, 2, line_number, "int", args[1])
        try:
            link_id = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.line_collection_file_name, 3, line_number, "int", args[2])
        if link_number == 1:
            line = Line(line_id, self.directed)
            self.line_pool.addLine(line)
        else:
            try:
                line = self.line_pool.getLine(line_id)
            except KeyError:
                raise DataIndexNotFoundException("Line", line_id)
        link = self.ptn.getEdge(link_id)
        if not link:
            raise DataIndexNotFoundException("Link", link_id)

        if not line.addLink(link):
            raise LineLinkNotAddableException(link_id, line_id)

        if self.read_frequencies and link_number == 1:
            try:
                frequency = int(args[3])
            except ValueError:
                raise InputTypeInconsistencyException(self.line_collection_file_name, 4, line_number, "int", args[3])
            line.setFrequency(frequency)

    def process_line_cost_line(self, args: [str], line_number: int) -> None:
        """
        Process the contents of a line cost line.
        :param args: the content of the line
        :param line_number: the line number, used for error handling
        """
        if len(args) != 3:
            raise InputFormatException(self.line_pool_cost_file_name, len(args), 3)
        try:
            line_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.line_pool_cost_file_name, 1, line_number, "int", args[0])
        try:
            length = float(args[1]) * self.conversion_factor_length
        except ValueError:
            raise InputTypeInconsistencyException(self.line_pool_cost_file_name, 2, line_number, "float", args[1])
        try:
            cost = float(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.line_pool_cost_file_name, 3, line_number, "float", args[2])
        try:
            line = self.line_pool.getLine(line_id)
        except KeyError:
            raise DataIndexNotFoundException("Line", line_id)
        line.setLength(length)
        line.setCost(cost)
        self.read_lines.add(line)

    def process_merged_lines(self, args: list[str], line_number: int) -> None:
        if len(args) != 3:
            raise InputFormatException(self.merged_lines_file_name, len(args), 3)

        modality = args[0]
        # Only process the line, if it belongs to the considered modality. Otherwise, nothing is to be done
        if modality == self.modality:
            try:
                line_id = int(args[1])
            except ValueError:
                raise InputTypeInconsistencyException(self.merged_lines_file_name, 2, line_number, "int", args[1])
            name = args[2]
            try:
                line = self.line_pool.getLine(line_id)
            except KeyError:
                raise DataIndexNotFoundException("Line", line_id)
            line.setMergedName(name)

    def process_line_names(self, args: list[str], line_number: int) -> None:
        if len(args) != 2:
            raise InputFormatException(self.line_names_file_name, len(args), 2)

        try:
            line_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.line_names_file_name, 1, line_number, "int", args[0])
        name = args[1]
        try:
            line = self.line_pool.getLine(line_id)
        except KeyError:
            raise DataIndexNotFoundException("Line", line_id)
        line.setName(name)

    def process_line_colors(self, args: list[str], line_number: int) -> None:
        if len(args) != 2:
            raise InputFormatException(self.line_colors_file_name, len(args), 2)

        try:
            line_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.line_colors_file_name, 1, line_number, "int", args[0])
        color = args[1]
        try:
            line = self.line_pool.getLine(line_id)
        except KeyError:
            raise DataIndexNotFoundException("Line", line_id)
        line.setLineColor(color)

    @staticmethod
    def read(ptn: Graph[Stop, Link], read_lines: bool = True, read_costs: bool = True, read_frequencies: bool = True,
             read_merged_lines: bool = False, read_names: bool = False, read_colors: bool = False,
             line_file_name: str = "", line_cost_file_name: str = None, merged_lines_file_name: str = None,
             line_names_file_name: str = None, line_colors_file_name: str = None, line_pool: LinePool = None,
             create_directed_lines: bool = None, conversion_factor_length: Optional[float] = None,
             conversion_factor_coordinates: Optional[float] = None, config: Config = Config.getDefaultConfig(), modality: str = "") \
            -> LinePool:
        """
        Read the line pool or the line concept, depending on read_frequencies. The cost file will be read, if
        line_pool_cost_file_name is not empty or None. Read lines will be added to the given linepool, if there is some.
        :param line_file_name: the line collection file name to read.
        :param read_lines
        :param read_costs
        :param create_directed_lines
        :param config
        :param ptn: the base ptn
        :param line_pool: the line pool to add the lines to. If there is none, a new linepool will be created.
        :param line_cost_file_name: the cost file name to read. Can be None or empty.
        :param read_frequencies: whether to read a line collection or a line pool.
        :param modality: Modality prefix that will be added to the filenames
            separated by a dot.
        :return: the line pool with the added lines.
        """
        if not read_lines and read_frequencies:
            LineReader.logger.warning("Can not read frequencies but no lines, will read lines as well!")
            read_lines = True
        if not line_pool:
            line_pool = LinePool()
        if read_lines and not line_file_name:
            if read_frequencies:
                line_file_name = config.getStringValue("default_lines_file")
            else:
                line_file_name = config.getStringValue("default_pool_file")
        if read_costs and not line_cost_file_name:
            line_cost_file_name = config.getStringValue("default_pool_cost_file")
        if read_merged_lines and not merged_lines_file_name:
            merged_lines_file_name = config.getStringValue("filename_merged_lines")
        if read_names and not line_names_file_name:
            line_names_file_name = config.getStringValue("filename_line_names")
        if read_colors and not line_colors_file_name:
            line_colors_file_name = config.getStringValue("filename_line_colors")
        if create_directed_lines is None:
            create_directed_lines = ptn.isDirected()
        if not conversion_factor_length:
            conversion_factor_length = config.getDoubleValue(
                "gen_conversion_length")
        if modality != "":
            line_file_name = add_modality_prefix(modality, line_file_name)
            if read_costs:
                line_cost_file_name = add_modality_prefix(modality, line_cost_file_name)
            if read_merged_lines:
                merged_lines_file_name = add_modality_prefix(modality, merged_lines_file_name)
            if read_names:
                line_names_file_name = add_modality_prefix(modality, line_names_file_name)
            if read_colors:
                line_colors_file_name = add_modality_prefix(modality, line_colors_file_name)
        reader = LineReader(line_file_name, line_cost_file_name, merged_lines_file_name, line_names_file_name, line_colors_file_name, line_pool, ptn, create_directed_lines,
                            read_frequencies, conversion_factor_length, modality)
        if read_lines:
            CsvReader.readCsv(line_file_name, reader.process_line_pool_line)
        if read_costs:
            CsvReader.readCsv(line_cost_file_name, reader.process_line_cost_line)
            if len(reader.read_lines) != len(line_pool.getLines()):
                raise DataLinePoolCostInconsistencyException(len(line_pool.getLines()), len(reader.read_lines),
                                                             line_cost_file_name)
        if read_merged_lines:
            CsvReader.readCsv(merged_lines_file_name, reader.process_merged_lines)
        if read_names:
            CsvReader.readCsv(line_names_file_name, reader.process_line_names)
        if read_colors:
            CsvReader.readCsv(line_colors_file_name, reader.process_line_colors)
        return line_pool


class LineWriter:
    """
    Class implementing writing line pools and concepts as static methods. Use the static methods to write.
    """
    logger = logging.getLogger(__name__)

    @staticmethod
    def write(pool: LinePool, write_pool: bool = True, write_costs: bool = True, write_line_concept: bool = True,
              pool_file_name: str = "", pool_header: str = "", cost_file_name: str = "", cost_header: str = "",
              concept_file_name: str = "", concept_header: str = "", conversion_factor_length: Optional[float] = None,
              write_line_names: bool = False, line_names_file_name : str = "", line_names_header: str = "",
              write_merged_lines: bool = False, merged_lines_file_name: str = "", merged_lines_header: str = "",
              write_line_colors: bool = False, line_colors_file_name: str = "", line_colors_header: str = "",
              modality: str = "", config: Config = Config.getDefaultConfig()):
        """
        Write the given linepool, with or without the cost file. write_cost_file and write_pool_file can be used to
        determine what to write. If no file name or header is given and the respective file should be written, the
        values are read from the given config (or the default config, if there is none).
        :param write_line_concept:
        :param concept_header:
        :param concept_file_name:
        :param pool: the pool to write
        :param config: the config to use to read file names or headers, if necessary. Will use the default config if
        none is given
        :param write_costs: whether to write the cost file
        :param cost_file_name: the name of the file to write the cost file to
        :param cost_header: the header to write in the cost file
        :param write_pool: whether to write the pool file
        :param pool_file_name: the name of the file to write the pool to
        :param pool_header: the header to write in the pool file
        :param conversion_factor_length: conversion factor for edge length to km
        :param modality: if given, this string is prefixed to the file name as a
        prefix separated by a dot.
        """
        # Sort the lines first, we may need to write them three times
        lines = pool.getLines()
        lines.sort(key=Line.getId)
        if write_pool:
            if not pool_file_name:
                pool_file_name = config.getStringValue("default_pool_file")
            if not pool_header:
                pool_header = config.getStringValue("lpool_header")
            if modality != "":
                pool_file_name = add_modality_prefix(modality, pool_file_name)
            pool_writer = CsvWriter(pool_file_name, pool_header)
            for line in lines:
                edge_index = 1
                for link in line.getLinePath().getEdges():
                    pool_writer.writeLine([str(line.getId()), str(edge_index), str(link.getId())])
                    edge_index += 1
            pool_writer.close()
        if write_costs:
            if not cost_file_name:
                cost_file_name = config.getStringValue("default_pool_cost_file")
            if not cost_header:
                cost_header = config.getStringValue("lpool_cost_header")
            if not conversion_factor_length:
                conversion_factor_length = config.getDoubleValue("gen_conversion_length")
            if modality != "":
                cost_file_name = add_modality_prefix(modality, cost_file_name)
            CsvWriter.writeListStatic(cost_file_name, lines, lambda x: x.toLineCostCsvStrings(conversion_factor_length), header=cost_header)
        if write_line_concept:
            if not concept_file_name:
                concept_file_name = config.getStringValue("default_lines_file")
            if not concept_header:
                concept_header = config.getStringValue("lines_header")
            if modality != "":
                concept_file_name = add_modality_prefix(modality, concept_file_name)
            line_writer = CsvWriter(concept_file_name, concept_header)
            for line in lines:
                edge_index = 1
                for link in line.getLinePath().getEdges():
                    line_writer.writeLine(
                        [str(line.getId()), str(edge_index), str(link.getId()), str(line.getFrequency())])
                    edge_index += 1
            line_writer.close()

        if write_line_names:
            if not line_names_file_name:
                line_names_file_name = config.getStringValue("filename_line_names")
            if not line_names_header:
                line_names_header = config.getStringValue("line_names_header")
            if modality != "":
                line_names_file_name = add_modality_prefix(modality, line_names_file_name)
            name_writer = CsvWriter(line_names_file_name, line_names_header)
            for line in lines:
                name_writer.writeLine([str(line.getId()), line.getName()])

        if write_merged_lines:
            if not merged_lines_file_name:
                merged_lines_file_name = config.getStringValue("filename_merged_lines")
            if not merged_lines_header:
                merged_lines_header = config.getStringValue("merged_lines_header")
            merged_writer = CsvWriter(merged_lines_file_name, merged_lines_header)
            for line in lines:
                merged_writer.writeLine([modality, str(line.getId()), line.getMergedName()])

        if write_line_colors:
            if not line_colors_file_name:
                line_colors_file_name = config.getStringValue("filename_line_colors")
            if not line_colors_header:
                line_colors_header = config.getStringValue("line_colors_header")
            color_writer = CsvWriter(line_colors_file_name, line_colors_header)
            for line in lines:
                color_writer.writeLine([str(line.getId()), line.getLineColor()])
