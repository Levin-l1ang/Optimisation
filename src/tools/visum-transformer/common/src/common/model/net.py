import logging
from typing import Dict, List


class NetSection:
    """
    Class representing a section of a net file
    """
    def __init__(self, name: str, header: [str]):
        """
        Create a new section with the given name and header
        :param name: the name of the section
        :param header: the header of the section, i.e., the names of the included columns
        """
        self.name = name
        self.header = header
        self.content = []  # type: [[str]]

    def append_column(self, column: [str]) -> None:
        """
        Append the given column to the section
        :param column: the column to add
        """
        self.content.append(column)

    def get_row(self, index: int) -> [str]:
        """
        Get the row with the given index
        :param index: the index to look for
        :return: the row
        """
        return self.content[index]

    def get_entry(self, row_index: int, column_header: str) -> str:
        """
        Get the entry for the given row and column
        :param row_index: the index of the row
        :param column_header: the name of the column
        :return: the corresponding entry
        """
        return self.get_entry_from_row(self.content[row_index], column_header)

    def get_entry_from_row(self, row: [str], column_header: str) -> str:
        """
        Get the entry belonging to the given column from the given row
        :param row: the row to search
        :param column_header: the name of the column to search
        :return: the corresponding entry
        """
        column_index = self.header.index(column_header)
        return row[column_index]

    def has_entry(self, column_header: str) -> bool:
        return column_header in self.header

    def get_rows(self) -> List[List[str]]:
        """
        Get the rows of the section
        :return: the rows
        """
        return self.content

    def __str__(self):
        return "Section: " + self.name + "\nHeader: " + ", ".join([header_name for header_name in self.header]) + \
               "\nContent:\n" + "\n".join([", ".join([entry for entry in row]) for row in self.content])


class Net:
    """
    Class representing a net file
    """
    logger = logging.getLogger(__name__)

    def __init__(self):
        """
        Create a new empty net file object
        """
        self.sections = {}  # type: Dict[str, NetSection]

    def get_section(self, section_name: str) -> NetSection:
        """
        Get the section with the given name
        :param section_name: the name
        :return: the section with the given name
        """
        return self.sections[section_name]

    def add_section(self, section: NetSection) -> None:
        """
        Add the given section to the net file object
        :param section: the section to add
        """
        if section.name in self.sections:
            self.logger.warning("Overriding section {}".format(section.name))
        self.sections[section.name] = section

    def __str__(self):
        return "\n\n".join([str(section) for section in self.sections.values()])
