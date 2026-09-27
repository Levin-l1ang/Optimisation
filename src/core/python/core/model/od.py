from abc import ABCMeta, abstractmethod
from math import isclose
from typing import List, Mapping, Collection

from core.io.csv import CsvWriter


class ODPair:
    """
    Class impementing an od pair, i.e., a triple of origin, destination and
    value.
    """

    def __init__(self, origin: int, destination: int, value: float):
        """
        Create a new od pair with the given attributes.
        :param origin   the origin of the od pair. This is the id of the node
        in the PTN where the passengers start.
        :param destination  the destination of the od pair. This is the id of
        the node in the PTN where the passengers want to arrive at.
        :param value    the value of the od pair, i.e., how many passengers
        want to travel from origin to destination in the planning period.
        """
        self.origin = origin
        self.destination = destination
        self.value = value

    def getOrigin(self) -> int:
        """
        Get the origin of the od pair. This is the id of the node in the PTN
        where the passengers start.
        :return     the id of the origin.
        """
        return self.origin

    def getDestination(self) -> int:
        """
        Get the destination of the od pair. This is the id of the node on the
        PTN where the passengers want to arrive at.
        :return     the id of the destination.
        """
        return self.destination

    def getValue(self) -> float:
        """
        Get the value of the od pair, i.e., how many passengers want to travel
        from the origin to the destination in the planning period.
        :return     the value of the od pair.
        """
        return self.value

    def setValue(self, newValue: float) -> None:
        """
        Set the value of the od pair, i.e., how many passengers want to travel
        from the origin to the destination in the planning period. The old
        valuevalue will be overwritten.
        :param newValue     the new value of the od pair.
        """
        self.value = newValue

    def toCsvStrings(self) -> List[str]:
        """
        Return a string array, representing the od pair for a LinTim csv file.
        :return     the csv representation of this od pair.
        """
        return [str(self.getOrigin()), str(self.getDestination()),
                CsvWriter.shortenDecimalValueForOutput(self.getValue())]

    def __str__(self) -> str:
        return "OD" + ", ".join(self.toCsvStrings())

    def __eq__(self, other) -> bool:
        if not isinstance(other, ODPair):
            return False
        return (self.getOrigin() == other.getOrigin()
                and self.getDestination() == other.getDestination()
                and isclose(self.getValue(), other.getValue()))

    def __ne__(self, other) -> bool:
        return not self.__eq__(other)

    def __hash__(self) -> int:
        return hash((self.origin, self.destination))


class OD(metaclass=ABCMeta):
    """
    Interface for an od matrix. Implementations can be found in  {@link
    net.lintim.mmodel.impl}.
    """

    @abstractmethod
    def getValue(self, origin: int, destination: int) -> float:
        """
        Get the value for a specific origin, destination pair.
        :param origin       the id of the origin, i.e., the id of the node in
        the PTN where the passengers start.
        :param destination  the id of the destination, i.e., the id of the node
        in the PTN, where the passengers want to arrive at.
        :return     the value for the given od pair.
        """
        raise NotImplementedError

    @abstractmethod
    def setValue(self, origin: int, destination: int, new_value: float) -> None:
        """
        Set the value for a specific origin, destination pair.
        :param origin       the id of the origin, i.e., the id of the node in
        the PTN where the passengers start.
        :param destination  the id of the destination, i.e., the id of the node
        in the PTN, where the passengers want to arrive at.
        :param new_value     the new value for the given od pair. The old value
        will be overwritten.
        """
        raise NotImplementedError

    @abstractmethod
    def computeNumberOfPassengers(self) -> float:
        """
        Get the total number of passengers in the od matrix
        :return     the number of passengers
        """
        raise NotImplementedError

    @abstractmethod
    def getODPairs(self) -> List[ODPair]:  # not sure what type is returned
        """
        Get a collection of all od pairs that have a non zero entry.
        :return     all non emtpy od pairs.
        """
        raise NotImplementedError

    def getODPairsByOriginID(self) -> Mapping[int, Collection[ODPair]]:
        raise NotImplementedError