import math
from typing import Callable, Any

from core.io.csv import CsvWriter
from core.model.activityType import ActivityType
from core.model.eventType import EventType
from core.model.timetable import Timetable
from core.model.graph import Edge, Node
from core.model.lines import LineDirection


class PeriodicEvent(Node):
    """
    A class representing a periodic event, i.e., a node in the periodic event
    activity network (EAN).
    """
    def __init__(self, event_id: int, stop_id: int, event_type: EventType,
                 line_id: int, time: int, number_of_passengers: float,
                 direction: LineDirection, line_frequency_repetition: int,
                 modality: str = ''):
        """
        Creates a periodic event, i.e., a node in the periodic event activity
        network.
        :param event_id: event id
        :param stop_id: id of the corresponding stop
        :param event_type: type of the event
        :param line_id: id of the corresponding line
        :param time: periodic time at which the event takes place
        :param number_of_passengers: number of passengers using the event
        :param direction: the direction of the associated line
        :param line_frequency_repetition: the repetition of the line, i.e.,
        the number of the current iteration of the line in this period
        :param modality: The modality for which this event belongs to.
        """
        self.event_id = event_id
        self.stop_id = stop_id
        self.type = event_type
        self.line_id = line_id
        self.time = time
        self.number_of_passengers = number_of_passengers
        self.direction = direction
        self.line_frequency_repetition = line_frequency_repetition
        self.modality = modality

    def setId(self, new_id: int) -> None:
        self.event_id = new_id

    def getId(self) -> int:
        return self.event_id

    def getStopId(self) -> int:
        """
        Get the id of the corresponding stop.
        :return: the id of the corresponding stop
        """
        return self.stop_id

    def getType(self) -> EventType:
        """
        Get the type of the event, which is specified in EventType.
        :return: the type of the event
        """
        return self.type

    def getLineId(self) -> int:
        """
        Get the id of the corresponding line.
        :return: the corresponding line id
        """
        return self.line_id

    def getTime(self) -> int:
        """
        Get the periodic time at which the event takes place.
        :return:
        """
        return self.time

    def setTime(self, time: int) -> None:
        """
        Set the time of the event.
        :param time: the periodic time of the event
        """
        self.time = int(time)

    def getNumberOfPassengers(self) -> float:
        """
        Get the number of passengers using the event.
        :return: the number of passengers using the event
        """
        return self.number_of_passengers

    def getDirection(self) -> LineDirection:
        """
        Get the direction of the line associated to the event
        :return: the direction
        """
        return self.direction

    def setDirection(self, new_line_direction: LineDirection) -> None:
        """
        Set the direction of the line associated to the event. Will not change
        the line, only changes the event locally.
        :param new_line_direction: the new direction
        """
        self.direction = new_line_direction

    def getLineFrequencyRepetition(self) -> int:
        """
        Get the line frequency repetition, i.e., the number of the current
        iteration of the line in this period
        :return: the line frequency repetition
        """
        return self.line_frequency_repetition

    def setLineFrequencyRepetition(self, new_line_frequency_repetition: int) -> None:
        """
        Set the line frequency repetition, i.e., the number of the current
        iteration of the line in this period.
        :param new_line_frequency_repetition:
        :return:
        """
        self.line_frequency_repetition = new_line_frequency_repetition

    def toCsvStrings(self) -> [str]:
        """
        Return a string list, representing the event for a LinTim csv file.
        :return: the csv representation of this event
        """
        return [str(self.getId()), self.getType().value, str(self.getStopId()),
                str(self.getLineId()), CsvWriter.shortenDecimalValueForOutput(self.getNumberOfPassengers()),
                self.getDirection().value,
                str(self.getLineFrequencyRepetition())]

    def toCsvTimetableStrings(self) -> [str]:
        """
        Return a string array, representing the activity for a LinTim csv file.
        :return: the csv representation of this activity
        """
        return [str(self.getId()), str(self.getTime())]

    def __hash__(self) -> int:
        result = hash((self.event_id, self.stop_id, self.type, self.line_id))
        return result

    def __eq__(self, other) -> bool:
        if not isinstance(other, PeriodicEvent):
            return False
        return (other.getId() == self.getId()
                and other.getStopId() == self.getStopId()
                and other.getLineId() == self.getLineId()
                and other.getTime() == self.getTime()
                and math.isclose(other.getNumberOfPassengers(), self.getNumberOfPassengers())
                and other.getType() == self.getType())

    def __ne__(self, other):
        return not self.__eq__(other)

    def __str__(self):
        return "Periodic Event (" + ", ".join(self.toCsvStrings()) + ")"


class PeriodicActivity(Edge[PeriodicEvent]):
    """
    A class representing a periodic activity, i.e., an arc in the periodic
    event activity network (EAN).
    """
    def __init__(self, activityId: int, activityType: ActivityType,
                 sourceEvent: PeriodicEvent, targetEvent: PeriodicEvent,
                 lowerBound: float, upperBound: float,
                 numberOfPassengers: float):
        """
        Create a new activity, i.e., an arc in the event activity network.
        :param activityId: id of the activity
        :param activityType: type of the activity
        :param sourceEvent: source event
        :param targetEvent: target event
        :param lowerBound: lower bound on the time the activity is allowed to
                           take
        :param upperBound: upper bound on the time the activity is allowed to
                           take
        :param numberOfPassengers: number of passengers using the activity
        """
        self.activityId = activityId
        self.type = activityType
        self.sourceEvent = sourceEvent
        self.targetEvent = targetEvent
        self.lowerBound = lowerBound
        self.upperBound = upperBound
        self.numberOfPassengers = numberOfPassengers

    def checkFeasibilityDuration(self, periodLength: int) -> bool:
        """
        Check whether the periodic duration of the activity, i.e., the
        difference between its start and end time mod periodLength, is
        feasible, i.e., between the lower and the upper bound of the
        activity.
        :param periodLength: the length of the period
        :return: whether the duration of the activity is feasible
        """
        startTime = self.getLeftNode().getTime()
        endTime = self.getRightNode().getTime()
        return (self.getLowerBound() <=
                ((endTime - startTime) % periodLength + periodLength) %
                periodLength <= self.getUpperBound())

    def getDuration(self, period_length: int) -> int:
        """
        Get the duration of the activity. This will compute the length of the acitivity, the difference between the
        target and the source event, but respect the crossing of a time period. I.e., this method will return the
        smallest non-negative integer, such that
        - duration >= lower_bound
        - duration mod period_length == (end_time - start_time) mod period_length
        :param period_length: the period length
        :return: the duration of the activity
        """
        if (self.getType() == ActivityType.NONSCHEDULED) and ((self.getLeftNode().modality == "OD helper") or (self.getRightNode().modality == "OD helper")):
            # For multimodal EANs with nonscheduled modalities, handle cases where passenger, e.g., walks to or from an OD node => no slack on activity
            return self.lowerBound 
        start_time = self.getLeftNode().getTime()
        end_time = self.getRightNode().getTime()
        duration = end_time - start_time
        while duration < self.lowerBound:
            duration += period_length
        return duration

    def getId(self) -> int:
        return self.activityId

    def setId(self, newId: int) -> None:
        self.activityId = newId

    def getLeftNode(self) -> PeriodicEvent:
        return self.sourceEvent

    def getRightNode(self) -> PeriodicEvent:
        return self.targetEvent

    def isDirected(self) -> bool:
        # An activity is always directed
        return True

    def getType(self) -> ActivityType:
        """
        Get the type of an activity, which is specified in ActivityType.
        :return: the type of the activity
        """
        return self.type

    def getLowerBound(self) -> float:
        """
        Get the lower bound of the time the activity is allowed to take.
        :return: the lower bound of the activity
        """
        return self.lowerBound

    def getUpperBound(self) -> float:
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
        """
        self.numberOfPassengers = number_of_passengers

    def toCsvStrings(self) -> [str]:
        """
        Return a string list, representing the activity for a LinTim csv file
        :return: the csv representation of this activity
        """
        return [str(self.getId()),
                self.getType().value,
                str(self.getLeftNode().getId()),
                str(self.getRightNode().getId()),
                CsvWriter.shortenDecimalValueForOutput(self.getLowerBound()),
                CsvWriter.shortenDecimalValueForOutput(self.getUpperBound()),
                CsvWriter.shortenDecimalValueForOutput(self.getNumberOfPassengers())]

    def __str__(self) -> str:
        return "Periodic Activity (" + ", ".join(self.toCsvStrings()) + ")"

    def __eq__(self, other) -> bool:
        if not isinstance(other, PeriodicActivity):
            return False
        return (other.getId() == self.getId()
                and math.isclose(other.getLowerBound(), self.getLowerBound())
                and math.isclose(other.getUpperBound(), self.getUpperBound())
                and math.isclose(other.getNumberOfPassengers(),
                                 self.getNumberOfPassengers())
                and other.getType() == self.getType()
                and other.getLeftNode() == self.getLeftNode()
                and other.getRightNode() == self.getRightNode())

    def __ne__(self, other) -> bool:
        return not self == other

    def __hash__(self) -> int:
        result = self.activityId
        # self.type or self.activityType?
        result = 31 * result + hash(self.type)
        # result = 31 * result + hash(self.activityType)
        result = 31 * result + hash(self.sourceEvent)
        result = 31 * result + hash(self.targetEvent)
        result = 31 * result + hash(self.lowerBound)
        result = 31 * result + hash(self.upperBound)
        return result


class PeriodicHeadway(PeriodicActivity):
    """
    Create a new headway activity, i.e., an arc in the event activity
    network that is a headway. Headway activity can be accessed and set with
    {:link #getCorrespondingHeadway()} and {:link
    #setCorrespondingHeadway(PeriodicHeadway)} respectively.
    :param activityId: id of the activity
    :param activityType: type of the activity
    :param sourceEvent: source event
    :param targetEvent: target event
    :param lowerBound: lower bound on the time the activity is allowed to take
    :param upperBound: upper bound on the time the activity is allowed to take
    :param numberOfPassengers: number of passengers using the activity
    """

    def __init__(self, activityId: int, activityType: ActivityType,
                 sourceEvent: PeriodicEvent, targetEvent: PeriodicEvent,
                 lowerBound: float, upperBound: float, numberOfPassengers:
                 float):
        super().__init__(activityId, activityType, sourceEvent, targetEvent,
                         lowerBound, upperBound, numberOfPassengers)

    def getCorrespondingHeadway(self):
        return self.correspondingHeadway

    def setCorrespondingHeadway(self, correspondingHeadway) -> None:
        self.correspondingHeadway = correspondingHeadway


class PeriodicTimetable(Timetable):
    """
    Mapping from events (of whatever type) to long integers, additionally
    maintaining a value for "time units per
    minute" and an integer "period length".
    """

    def __init__(self, timeUnitsPerMinute: int, period: int):
        """
        Create a new periodic timetable with the given "time units per minute"
        and "period length" values.
        :param timeUnitsPerMinute: time units per minute (may be fractional)
        :param period: period length
        """
        super().__init__(timeUnitsPerMinute)
        self.period = period

    def getRepetitionTimesInPeriod(self, key: Any, frequency: int,
                                   roundingFunction: Callable[[float], int]) -> [int]:
        """
        Returns a list of times expanding one event by a given frequency within
        the timetable's period length.
        :param key: the event for which the list of actual occurrence times
        shall be calculated
        :param frequency: the frequency with which the event should be spread;
        must be at least 1. Otherwise an empty
        list will be returned.
        :param roundingFunction: a rounding method used for all instance times
        (Double to Long)
        :return: a list of integer times of length "frequency"
        """
        if frequency < 1:
            return []
        interval = float(self.period) / frequency
        return [(self[key] + roundingFunction(i * interval))
                % self.period for i in range(0, frequency)]

    def getPeriod(self) -> int:
        """
        Get the period of this timetable.
        :return: the period length
        """
        return self.period

    def __str__(self):
        return ("Periodic Timetable: Period "
                + str(self.period)
                + "\n"
                + super().__str__())
