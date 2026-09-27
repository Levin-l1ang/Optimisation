from typing import List, Dict, Mapping, Collection

from core.model.od import OD, ODPair


class MapOD(OD):
    """
    Class implementing the od interface with a simple map. Especially suited for sparse od matrices.
    """
    def __init__(self):
        self.od: Dict[int, Dict[int, float]] = {}

    def setValue(self, origin: int, destination: int, new_value: float) -> None:
        self.od.setdefault(origin, {})[destination] = new_value

    def computeNumberOfPassengers(self) -> float:
        return sum(value for inner_dict in self.od.values() for value in inner_dict.values())

    def getODPairs(self) -> List[ODPair]:
        return [ODPair(origin, destination, value)
                for origin in self.od.keys()
                for destination, value in self.od[origin].items()
                if value > 0]

    def getValue(self, origin: int, destination: int) -> float:
        if origin in self.od and destination in self.od[origin]:
            return self.od[origin][destination]
        return 0

    def getODPairsByOriginID(self) -> Mapping[int, Collection[ODPair]]:
        return {origin: {ODPair(origin, destination, value)
                         for destination, value in destval.items()}
                for origin, destval in self.od.items()}