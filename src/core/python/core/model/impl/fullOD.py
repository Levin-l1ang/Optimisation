from collections import defaultdict
from typing import List, Mapping, Collection

import numpy

from core.model.od import OD, ODPair
from core.exceptions.index_exceptions import IndexOutOfBoundsException


class FullOD(OD):
    """
    Implements the od interface using a two dimensional numpy array. Therefore
    accessing the od pairs by index is cheap but getting nonempty od-pairs by
    getODPairs may be expensive.
    :param size     the size of the matrix
    """

    def __init__(self, size: int):
        self.matrix = numpy.zeros(shape=(size, size), dtype=float)

    def raise_for_invalid_index_pair(self, origin: int, destination: int):
        if self.matrix.shape[0] < origin or origin <= 0:
            exception_key = "Origin"
            raise IndexOutOfBoundsException(exception_key, origin,
                                            len(self.matrix))
        if self.matrix.shape[1] < destination or destination <= 0:
            exception_key = "Destination"
            raise IndexOutOfBoundsException(exception_key, origin,
                                            len(self.matrix))

    def getValue(self, origin: int, destination: int) -> float:
        self.raise_for_invalid_index_pair(origin, destination)
        return self.matrix[origin - 1][destination - 1]

    def setValue(self, origin: int, destination: int, new_value: float) -> None:
        self.raise_for_invalid_index_pair(origin, destination)
        self.matrix[origin - 1][destination - 1] = new_value

    def computeNumberOfPassengers(self) -> float:
        sum_value = 0
        for value in numpy.nditer(self.matrix):
                sum_value += value
        return sum_value

    def getODPairs(self) -> List[ODPair]:
        all_od_pairs = []
        it = numpy.nditer(self.matrix, flags=["multi_index"])
        while not it.finished:
            if not numpy.isclose(it[0], 0):
                all_od_pairs.append(ODPair(it.multi_index[0]+1, it.multi_index[1]+1, it[0]))
            it.iternext()
        return all_od_pairs

    def getODPairsByOriginID(self) -> Mapping[int, Collection[ODPair]]:
        all_od_pairs = defaultdict(list)
        it = numpy.nditer(self.matrix, flags=["multi_index"])
        while not it.finished:
            if not numpy.isclose(it[0], 0):
                all_od_pairs[it.multi_index[0]+1].append(ODPair(it.multi_index[0]+1, it.multi_index[1]+1, it[0]))
            it.iternext()
        return all_od_pairs

    def __str__(self) -> str:
        return "Full OD:\n" + "\n".join([str(pair) for pair in self.getODPairs()])

    def __eq__(self, other) -> bool:
        if not isinstance(other, FullOD):
            return False
        return numpy.allclose(self.matrix, other.matrix)

    def __ne__(self, other):
        return not self.__eq__(other)

    def __hash__(self):
        return hash(self.matrix)
