import math
from typing import List

from core.io.csv import CsvWriter
from core.model.activityType import ActivityType
from core.model.eventType import EventType
from core.model.graph import Edge, Node
from core.model.timetable import Timetable


class AperiodicEvent(Node):
    """
    A class representing an aperiodic event, i.e., a node in the aperiodic
    event activity network (EAN)
    """

    def __init__(self, eventId: int, periodicEventId: int, stopId: int,
                 eventType: EventType, time: int, numberOfPassengers: float):
        """
        Create an aperiodic event, i.e., a node in the aperiodic event activity
        network.
        :param eventId            id of the event
        :param periodicEventId    id of the corresponding periodic event
        :param stopId             id of the corresponding stop
        :param eventType          type of the event
        :param time               periodic time at which the envent takes place
        :param numberOfPassengers number of passengers using the activity
        """
        self.eventId = eventId
        self.periodicEventId = periodicEventId
        self.stopId = stopId
        self.eventType = eventType
        self.time = time
        self.numberOfPassengers = numberOfPassengers

    def getId(self) -> int:
        return self.eventId

    def setId(self, newId: int) -> None:
        self.eventId = newId

    def getPeriodicEventId(self) -> int:
        """
       Get the id of the corresponding periodic event.
       :return the id of the corresponding periodic event
       """
        return self.periodicEventId

    def getStopId(self) -> int:
        """
        Get the id of the corresponding stop.
        :return the id of the corresponding stop
        """
        return self.stopId

    def getType(self) -> EventType:
        """
        Get the type of the event, which is specified in EventType.
        :return the type of the event
        """
        return self.eventType

    def getTime(self) -> int:
        """
        Get the periodic time at which the event takes place.
        :return the time of the event
        """
        return self.time

    def setTime(self, time: int) -> None:
        """
        Set the time of the event.
        :param time the time of the event
        """
        self.time = time

    def getNumberOfPassengers(self) -> float:
        """
        Get the number of passengers using the event.
        :return: the number of passengers using the event
        """
        return self.numberOfPassengers

    def toCsvStrings(self, timetable: Timetable=None) -> List[str]:
        """
        Return a string list, representing the activity for a LinTim csv file
        :return: the csv representation of this activity
        """
        if timetable:
            time = timetable.get(self)
        else:
            time = self.getTime()
        return [str(self.getId()),
                str(self.getPeriodicEventId()),
                self.getType().value,
                str(time),
                CsvWriter.shortenDecimalValueForOutput(self.getNumberOfPassengers()),
                str(self.getStopId())]

    def toCsvStringsForTimetable(self, timetable: Timetable=None) -> List[str]:
        """
        Return a string list, representing the activity for a LinTim csv file
        :return: the csv representation of this activity
        """
        if timetable:
            time = timetable.get(self)
        else:
            time = self.getTime()
        return [str(self.getId()),
                str(time)]


    def __str__(self) -> str:
        return "Aperiodic Event (" + ", ".join(self.toCsvStrings()) + ")"

    def __eq__(self, other) -> bool:
        if not isinstance(other, AperiodicEvent):
            return False
        return (other.getId() == self.getId()
                and other.getPeriodicEventId() == self.getPeriodicEventId()
                and other.getStopId() == self.getStopId()
                and other.getTime() == self.getTime()
                and math.isclose(other.getNumberOfPassengers(), self.getNumberOfPassengers())
                and other.getType() == self.getType())

    def __ne__(self, other) -> bool:
        return not self == other

    def __hash__(self) -> int:
        return hash((self.eventId, self.periodicEventId, self.stopId))


class AperiodicActivity(Edge[AperiodicEvent]):
    """
    A class representing an aperiodic activity, i.e., an arc in the aperiodic
    event activity network (EAN).
    """

    def __init__(self, activityId: int, periodicActivityId: int, activityType:\
                 ActivityType, sourceEvent: AperiodicEvent, targetEvent:\
                 AperiodicEvent, lowerBound: int, upperBound: int,
                 numberOfPassengers: float):
        """
        Create a new aperiodic activity, i.e., an arc in the aperiodic event
        activity network.
        :param activityId         id of the activity
        :param periodicActivityId id of the corresponding periodic activity
        :param activityType       type of the activity
        :param sourceEvent        source event
        :param targetEvent        target event
        :param lowerBound         lower bound on the time the activity is
                                  allowed to take
        :param upperBound         upper bound on the time the activity is
                                  allowed to take
        :param numberOfPassengers number of passengers using the activity
        """
        self.activityId = activityId
        self.periodicActivityId = periodicActivityId
        self.activityType = activityType
        self.sourceEvent = sourceEvent
        self.targetEvent = targetEvent
        self.lowerBound = lowerBound
        self.upperBound = upperBound
        self.numberOfPassengers = numberOfPassengers

    def getId(self) -> int:
        return self.activityId

    def setId(self, newId: int) -> None:
        self.activityId = newId

    def getLeftNode(self) -> AperiodicEvent:
        return self.sourceEvent

    def getRightNode(self) -> AperiodicEvent:
        return self.targetEvent

    def isDirected(self) -> bool:
        # An activity is always directed
        return True

    def getPeriodicActivityId(self) -> int:
        """
        Get the id of the corresponding periodic activity.
        :return the id of the corresponding activity
        """
        return self.periodicActivityId

    def getType(self) -> ActivityType:
        """
        Get the type of an activity, which is specified in ActivityType.
        :return the type of the activity
        """
        return self.activityType

    def getLowerBound(self) -> int:
        """
        Get the lower bound of the time the activity is allowed to take.
        :return the lower bound of the activity
        """
        return self.lowerBound

    def getUpperBound(self) -> int:
        """
        Get the upper bound of the time the activity is allowed to take.
        :return: the upper bound of the activity
        """
        return self.upperBound

    def getNumberOfPassengers(self) -> float:
        """
        Get the number of passengers using the activity.
        :return: the number of passengers using the activity
        """
        return self.numberOfPassengers

    def setNumberOfPassengers(self, number_of_passengers: float) -> None:
        """
        Set the number of passengers using the activity
        :param number_of_passengers: the number of passengers
        """
        self.numberOfPassengers = number_of_passengers

    def checkFeasibilityDuration(self) -> bool:
        """
        Check whether the duration of the activity, i.e., the difference
        between its start and end time, is feasible,
        i.e., between the lower and the upper bound of the activity.
        :return: whether the duration of the activity is feasible
        """
        startTime = self.getLeftNode().getTime()
        endTime = self.getRightNode().getTime()
        return (endTime - startTime >= self.getLowerBound() & endTime -
                startTime <= self.getUpperBound())

    def toCsvStrings(self) -> [str]:
        """
        Return a string list, representing the activity for a LinTim csv file
        :return: the csv representation of this activity
        """
        return [str(self.getId()),
                str(self.getPeriodicActivityId()),
                self.getType().value,
                str(self.getLeftNode().getId()),
                str(self.getRightNode().getId()),
                str(self.getLowerBound()),
                str(self.getUpperBound()),
                CsvWriter.shortenDecimalValueForOutput(self.getNumberOfPassengers())]

    def __str__(self) -> str:
        return "Aperiodic Activity (" + ", ".join(self.toCsvStrings()) + ")"

    def __eq__(self, other) -> bool:
        if not isinstance(other, AperiodicActivity):
            return False
        return (other.getId() == self.getId()
                and other.getPeriodicActivityId() == self.getPeriodicActivityId()
                and other.getLowerBound() == self.getLowerBound()
                and other.getUpperBound() == self.getUpperBound()
                and math.isclose(other.getNumberOfPassengers(), self.getNumberOfPassengers())
                and other.getType() == self.getType()
                and other.getLeftNode() == self.getLeftNode()
                and other.getRightNode() == self.getRightNode())

    def __ne__(self, other) -> bool:
        return not self == other

    def __hash__(self) -> int:
        return hash((self.activityId, self.getPeriodicActivityId(), self.activityType))


class AperiodicHeadway(AperiodicActivity):
    """
    Create a new aperiodic headway actiity, i.e., an arc in the aperiodic
    envent activity network that is a headway. The corresponding headway
    activity can be accessed and set with {:link #getCorrespondingHeadway()}
    and {:link #setCorrespondingHeadway(AperiodicHeadway)} respectively.
    :param activityId           id of the activity
    :param periodicActivityId   id of the corresponding periodic activity
    :param activityType         type of the activity
    :param sourceEvent          source envent
    :param targetEvent          target event
    :param lowerBound           lower bound on the time the activity is allowed
    to take
    :param upperBound           upper bound on the time the activity is allowed
    to take
    :param numberOfPassengers   number of passengers using the activity
    """

    def __init__(self, activityId: int, periodicActivityId: int, activityType:
                 ActivityType, sourceEvent: AperiodicEvent, targetEvent:
                 AperiodicEvent, lowerBound: int, upperBound: int,
                 numberOfPassengers: float, correspondingHeadway:
                 AperiodicActivity):
        super().__init__(activityId, periodicActivityId, activityType,
                         sourceEvent, targetEvent, lowerBound, upperBound,
                         numberOfPassengers)
        self.correspondingHeadway = correspondingHeadway

    def getCorrespondingHeadway(self) -> AperiodicActivity:
        return self.correspondingHeadway

    def setCorrespondingHeadway(self, correspondingHeadway:
                                AperiodicActivity) -> None:
        self.correspondingHeadway = correspondingHeadway
