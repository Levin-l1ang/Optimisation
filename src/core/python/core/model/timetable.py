from typing import Callable


class Timetable(dict):
    """
    Mapping from events (of whatever type) to long integers,
    additionally maintaining a value for "time units per minute".
    """

    def __init__(self, timeUnitsPerMinute: int):
        """
        Create a new timetable with the given "time units per minute" value.
        :param timeUnitsPerMinute: time units per minute (may be fractional)
        """
        super(Timetable, self).__init__()
        self.timeUnitsPerMinute = timeUnitsPerMinute

    def getTimeUnitsPerMinute(self) -> int:
        """
        Returns the current "time units per minute" value for this timetable.
        :return: time units per minute (may be fractional)
        """
        return self.timeUnitsPerMinute

    def setTimeUnitsPerMinute(self, newTimeUnitsPerMinute: int,
                              roundingFunction:
                              Callable[[float], int]) -> None:
        """
        Updates the "time units per minute" value and converts existing
        timetable entries.
        :param newTimeUnitsPerMinute: new value for "time units per minute"
        :param roundingFunction: a rounding method used for all recalculations
        (Double to Long)
        """
        if newTimeUnitsPerMinute == self.timeUnitsPerMinute:
            return
        factor = 1.0 * newTimeUnitsPerMinute / self.timeUnitsPerMinute
        for event, time in self.items():
            self.__setitem__(event, roundingFunction(time * factor))

    def __str__(self) -> str:
        return ("Timetable: \n" + "\n".join("{}: {}".format(event, time)
                                            for event, time in self.items()))
