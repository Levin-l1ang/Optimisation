from enum import Enum
import logging
import math
from typing import Dict, List

from core.io.csv import CsvWriter
from core.model.impl.list_path import ListPath
from core.model.path import Path
from core.model.ptn import Stop, Link


class LineDirection(Enum):
    """
    Enum containing forwards and backwards direction for a line. Used in giving
    periodic events a direction.
    """
    FORWARDS = ">"
    BACKWARDS = "<"


class Line:
    """
    A class for representing a line as path of Stop and Link.
    """
    logger = logging.getLogger(__name__)

    def __init__(self, line_id: int, directed: bool, length: float = 0,
                 cost: float = 0, frequency: int = 0,
                 line_path: Path[Stop, Link] = None, name: str = "", merged_name: str = "", color: str = ""):
        """
        Constructor for a line with given information.
        :param line_id: the id of the new line
        :param directed: whether the line should be directed
        :param length: length of the line
        :param cost: cost of the line
        :param frequency: frequency of the line
        :param line_path: path of the line
        :param name: name of the line as a string
        :param merged_name: merged name of the line. Multiple lines can be summarized with the same merged name
        :param color: color of the line as a string
        """
        self.line_id = line_id
        self.directed = directed
        self.length = length
        self.cost = cost
        self.frequency = frequency
        self.line_path = line_path
        self.name = name
        self.merged_name = merged_name
        self.color = color
        if not line_path:
            self.line_path = ListPath(directed)

    def addLink(self, link: Link, compute_cost_and_length: bool = False,
                factor_cost_length: float = 0,
                factor_cost_link: float = 0) -> bool:
        """
        Method to add a new link to the line and factor_cost_link and
        factor_cost_length*length to the line cost, if the
        parameter is set accordingly
        :param link: the link to add
        :param compute_cost_and_length: whether to update the cost and the
                                        length of the line
        :param factor_cost_length: factor of the cost depending on the length
                                   of the line
        :param factor_cost_link: factor of the cost depending on the number of
                                 links in the line
        :return: whether the link could be added
        """
        if self.line_path.containsNode(link.getLeftNode()) and self.line_path.containsNode(link.getRightNode()):
            self.logger.warning("Line {} now contains a loop, closed by link\
                                {}. This may create problems in the LinTim\
                                algorithms!".format(self.getId(),
                                                    link.getId()))
        result = self.line_path.addLastEdge(link)
        if compute_cost_and_length and result:
            self.length += link.getLength()
            self.cost += (link.getLength()
                          * factor_cost_length
                          + factor_cost_link)
        return result

    def toLineCostCsvStrings(self, conversion_factor_length: float = 1) -> list[str]:
        """
        Return a string list, representing the line's attributes used in the pool
        :return: the cost csv representation of this line
        """
        return [str(self.getId()),
                CsvWriter.shortenDecimalValueForOutput(self.getLength()/conversion_factor_length),
                CsvWriter.shortenDecimalValueForOutput(self.getCost())
                ]

    def getId(self) -> int:
        """
        Gets the id of the line.
        :return: line id
        """
        return self.line_id

    def getLength(self) -> float:
        """
        Gets the length of the line.
        :return: length of the line
        """
        return self.length

    def getCost(self) -> float:
        """
        Gets the cost of the line.
        :return: cost of the line
        """
        return self.cost

    def getFrequency(self) -> int:
        """
        Gets the frequency of the line.
        :return: frequency of the line
        """
        return self.frequency

    def getLinePath(self) -> Path[Stop, Link]:
        """
        Gets the path belonging to the line.
        :return: the path of the line
        """
        return self.line_path

    def setFrequency(self, frequency: int) -> None:
        """
        Sets the frequency of the line.
        :param frequency: line frequency
        """
        self.frequency = frequency

    def setLength(self, length: float) -> None:
        """
        Sets the length of the line.
        :param length: length of the line
        """
        self.length = length

    def setCost(self, cost: float) -> None:
        """
        Sets the cost of a line
        :param cost: cost of the line
        """
        self.cost = cost

    def getName(self) -> str:
        """
        Gets the name of a line

        :return: the name of the line
        :rtype: str
        """
        return self.name

    def setName(self, name: str) -> None:
        """
        Sets the name of a line

        :param name: name of the line
        :type name: str
        """
        self.name = name

    def getMergedName(self) -> str:
        """
        Gets the merged name of a line. Multiple lines can be summarized with the same merged name

        :return: the merged name of the line
        :rtype: str
        """
        return self.merged_name

    def setMergedName(self, name: str) -> None:
        """
        Sets the merged name of a line. Multiple lines can be summarized with the same merged name

        :param name: the merged name of the line
        :type name: str
        """
        self.merged_name = name

    def getLineColor(self) -> str:
        """
        Gets the color of the line

        :return: color of the line
        :rtype: str
        """
        return self.color

    def setLineColor(self, color: str) -> None:
        """
        Sets the color of the line

        :param color: color of the line
        :type color: str
        """
        self.color = color

    def compareEdges(self, other) -> bool:
        """
        Method to compare the edge sets of lines.
        """
        return set(self.getLinePath().getEdges()) == set(other.getLinePath().getEdges())

    def __eq__(self, other):
        if not isinstance(other, Line):
            return False
        return (self.getId() == other.getId()
                and math.isclose(self.getLength(), other.getLength())
                and math.isclose(self.getCost(), other.getCost())
                and self.getLinePath() == other.getLinePath())

    def __ne__(self, other):
        return not self.__eq__(other)

    def __hash__(self) -> int:
        return self.line_id

    def __str__(self):
        return ("Line " + ", ".join(self.toLineCostCsvStrings()) + ", Path "
                + str(self.line_path))


class LinePool:
    """
    A class to represent the line pool.
    """

    def __init__(self):
        """
        Constructor of a new empty line pool.
        """
        self.pool = {}  # type: Dict[int, Line]

    def addLine(self, line: Line) -> bool:
        """
        Method to add a line, if not already a line with the same id is in the
        pool.
        :param line: the line to add
        :return: whether the line could be added.
        """
        if line.getId() in self.pool:
            return False
        self.pool[line.getId()] = line
        return True

    def removeLine(self, line_id: int) -> bool:
        """
        Method to remove line with given id, if it exists in pool.
        :param line_id: id of the line to remove
        :return: whether a line was removed
        """
        if line_id not in self.pool:
            return False
        del self.pool[line_id]
        return True

    def getLines(self) -> List[Line]:
        """
        Gets a list of the lines. This is a copy, i.e., removing or adding
        lines to the list will not change the linepool.
        :return: the lines in the pool
        """
        return list(self.pool.values())

    def getLine(self, line_id: int) -> Line:
        """
        Gets the line for a given id or raises KeyError if it is not in the
        pool.
        :param line_id: id of the line to get
        :return: the line with the given id
        """
        return self.pool[line_id]

    def getLineConcept(self) -> List[Line]:
        """
        Method to get a list of all lines with frequency > 0.
        :return: a list of all lines with frequency > 0
        """
        return [line for line in self.pool.values() if line.getFrequency() > 0]

    def getMergedLines(self, merged_name: str) -> List[Line]:
        """
        Method to get a list of all lines with the specified merged name

        :param merged_name: the merged name
        :type merged_name: str
        :return: list of all lines of specifies merged name
        :rtype: List[Line]
        """
        return [line for line in self.pool.values() if line.getMergedName()==merged_name]

    def getMergedLinesDict(self) -> dict[str, List[Line]]:
        merged_dict = {}
        for line in self.pool.values():
            if line.getMergedName():
                if line.getMergedName() in merged_dict:
                    merged_dict[line.getMergedName()].append(line)
                else:
                    merged_dict[line.getMergedName()] = [line]
        return merged_dict

    def getLineByName(self, name: str) -> Line | None:
        """
        Get a line by its name

        :param name: the name of the line
        :type name: str
        :return: line with specified name or None if no such line exists
        :rtype: Line | None
        """
        for line in self.pool.values():
            if line.getName() == name:
                return line
        return None

    def __eq__(self, other):
        if not isinstance(other, LinePool):
            return False
        return self.pool == other.pool

    def __ne__(self, other):
        return not self.__eq__(other)

    def __str__(self):
        return ("LineConcept:\n"
                + "\n".join(["{}:{}".format(line_id, line)
                             for line_id, line in self.pool.items()]))
