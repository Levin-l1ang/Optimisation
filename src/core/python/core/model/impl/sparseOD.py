from collections import defaultdict
from typing import List, Dict, Tuple, Mapping, Collection

from core.model.od import OD, ODPair


class SparseOD(OD):

    def __init__(self):
        self.od_pairs = {}  # type: Dict[Tuple[int, int], float]

    def getValue(self, origin: int, destination: int) -> float:
        try:
            return self.od_pairs[(origin, destination)]
        except KeyError:
            return 0

    def setValue(self, origin: int, destination: int, new_value: float) -> None:
        self.od_pairs[(origin, destination)] = new_value

    def computeNumberOfPassengers(self) -> float:
        sum = 0
        for od_value in self.od_pairs.values():
            sum += od_value
        return sum

    def getODPairs(self) -> List[ODPair]:
        return [ODPair(entry[0][0], entry[0][1], entry[1]) for entry in self.od_pairs.items()]

    def getODPairsByOriginID(self) -> Mapping[int, Collection[ODPair]]:
        output = defaultdict(list)
        for od, value in self.od_pairs.items():
            output[od[0]].append(ODPair(od[0], od[1], value))
        return output
