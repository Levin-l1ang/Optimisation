from typing import List, Dict

from core.model.od import OD, ODPair


class DictOD(OD):

    def __init__(self):
        self.matrix = {}  # type: Dict[int, Dict[int, float]]

    def getValue(self, origin: int, destination: int) -> float:
        try:
            return self.matrix[origin][destination]
        except KeyError:
            return 0

    def setValue(self, origin: int, destination: int, new_value: float) -> None:
        if origin not in self.matrix:
            self.matrix[origin] = {}
        self.matrix[origin][destination] = new_value

    def computeNumberOfPassengers(self) -> float:
        sum_od = 0
        for values in self.matrix.values():
            for value in values.values():
                sum_od += value
        return sum_od

    def getODPairs(self) -> List[ODPair]:
        return [ODPair(origin, destination, value) for origin, values in self.matrix.items()
                for destination, value in values.items() if value != 0]
