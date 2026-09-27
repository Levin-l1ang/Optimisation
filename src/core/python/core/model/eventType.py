from enum import Enum


class EventType(Enum):
    """
    Enumeration of all possible event types.
    Arrival and departure are the classical event types, fix is for fixed events, i.e., when parts of a timetable should
    be fixed for the optimization.
    """
    ARRIVAL = "\"arrival\""
    DEPARTURE = "\"departure\""
    FIX = "\"fix\""
    VIRTUAL = "\"virtual\""

    def __init__(self, representation: str):
        self.representation = representation

    def __str__(self) -> str:
        return self.representation
