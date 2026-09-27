from typing import List

from core.exceptions.data_exceptions import (DataIndexNotFoundException,
                                             DataIllegalEventTypeException,
                                             DataIllegalActivityTypeException)
from core.exceptions.input_exceptions import (InputFormatException,
                                              InputTypeInconsistencyException)
from core.exceptions.graph_exceptions import (GraphEdgeIdMultiplyAssignedException,
                                              GraphIncidentNodeNotFoundException,
                                              GraphNodeIdMultiplyAssignedException)

from core.io.csv import CsvReader, CsvWriter
from core.model.aperiodic_ean import AperiodicEvent, AperiodicActivity
from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.periodic_ean import ActivityType, EventType
from core.model.timetable import Timetable
from core.util.config import Config, default_config


class AperiodicEANReader:
    """
    Class to process csv-lines, formatted in the LinTim
    Activities-expanded.giv, Events-expanded.giv or Timetable-expanded.tim
    format. Use a CsvReader with an instance of this class to read periodic
    activities, events or timetable files. For convenience, :func:`~core.io.aperiodic_ean.read` is provided, that does
    all the necessary work.
    """

    def __init__(self, event_file_name: str, activity_file_name: str, aperiodic_timetable_file_name: str,
                 aperiodic_ean: Graph[AperiodicEvent, AperiodicActivity], timetable: Timetable = None):
        """
        Constructor for a PeriodicEANReader for given file names and periodic
        EAN. The given names will not influence the read file but the used name
        in any error message, so be sure to use the same name in here aswell as
        in the CsvReader!
        :param activity_file_name  source file name for aperiodic
        activities
        :param event_file_name  source file name for aperiodic events.
        :param aperiodic_timetable_file_name   source file name for aperiodic
        timetable.
        :param aperiodic_ean     aperiodic event activity network.
        :param timetable   aperiodic timetable
        """
        self.aperiodicActivitiesFileName = activity_file_name
        self.aperiodicEventsFileName = event_file_name
        self.aperiodicTimetableFileName = aperiodic_timetable_file_name
        self.aperiodicEAN = aperiodic_ean
        self.timetable = timetable

    @staticmethod
    def parse_event_type(input_type: str, event_id: int) -> EventType:
        """
        Parse the given input as an event type. Will raise, if it is not valid.
        :param input_type: the input to parse
        :param event_id: the event id. Only used for error handling
        :return: the parsed event type
        """
        if input_type.lower() == "arrival" or input_type.lower() == "\"arrival\"":
            result = EventType.ARRIVAL
        elif (input_type.lower() == "departure" or
              input_type.lower() == "\"departure\""):
            result = EventType.DEPARTURE
        else:
            raise DataIllegalEventTypeException(event_id, input_type)
        return result

    @staticmethod
    def parse_activity_type(input_type: str, activity_id: int) -> ActivityType:
        """
        Parse the given input as an activity type. Will raise, if it is not
        valid.
        :param input_type: the input to parse
        :param activity_id: the activity id. Only used for error handling.
        :return: the parsed activity type
        """
        if input_type.lower() == "drive" or input_type.lower() == "\"drive\"":
            result = ActivityType.DRIVE
        elif (input_type.lower() == "wait" or
              input_type.lower() == "\"wait\""):
            result = ActivityType.WAIT
        elif (input_type.lower() == "change" or
              input_type.lower() == "\"change\""):
            result = ActivityType.CHANGE
        elif (input_type.lower() == "headway" or
              input_type.lower() == "\"headway\""):
            result = ActivityType.HEADWAY
        elif (input_type.lower() == "turnaround" or
              input_type.lower() == "\"turnaround\"" or
              input_type.lower() == "fixed-turnaround" or
              input_type.lower() == "\"fixed-turnaround\""):
            result = ActivityType.TURNAROUND
        else:
            raise DataIllegalActivityTypeException(activity_id, input_type)
        return result

    def process_aperiodic_event(self, args: List[str], line_number: int) -> None:
        """
        Process the content of an aperiodic event file.
        :param args     the content of the line.
        :param line_number   the line number, used for error handling
        :raise exceptions    if the line does not contain exactly 6 entries
                             if the specific types of the entries do not match
                             the expectations
                             if ht event type is not defined
                             if the event cannot be added to the EAN
        """
        if len(args) != 6:
            raise InputFormatException(self.aperiodicEventsFileName, len(args),
                                       6)
        try:
            event_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.aperiodicEventsFileName,
                                                  1, line_number, "int",
                                                  args[0])
        try:
            periodic_event_id = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.aperiodicEventsFileName,
                                                  2, line_number, "int",
                                                  args[1])
        event_type = AperiodicEANReader.parse_event_type(args[2], event_id)
        try:
            time = int(float(args[3]))
        except ValueError:
            raise InputTypeInconsistencyException(self.aperiodicEventsFileName,
                                                  4, line_number, "int",
                                                  args[3])
        try:
            passengers = float(args[4])
        except ValueError:
            raise InputTypeInconsistencyException(self.aperiodicEventsFileName,
                                                  5, line_number, "float",
                                                  args[4])
        try:
            stopId = int(args[5])
        except ValueError:
            raise InputTypeInconsistencyException(self.aperiodicEventsFileName,
                                                  6, line_number, "int",
                                                  args[5])

        aperiodicEvent = AperiodicEvent(event_id, periodic_event_id, stopId,
                                        event_type, time, passengers)
        eventAdded = self.aperiodicEAN.addNode(aperiodicEvent)
        if not eventAdded:
            raise GraphNodeIdMultiplyAssignedException(event_id)
        if self.timetable is not None:
            self.timetable[aperiodicEvent] = time


    def process_aperiodic_activity(self, args: [str], line_number: int) -> None:
        """
        Process the content of a periodic activity file.
        :param args     the content of the line
        :param line_number   the line number, used for error handling
        :raise exceptions   if the line does not contain exactly 8 entries
                            if the specific types of the entries do not match
                            the expectations
                            if the activity type is not defined
                            if the activity cannot be added to the EAN.
        """

        if len(args) != 8:
            raise InputFormatException(self.aperiodicActivitiesFileName,
                                        len(args), 8)
        try:
            activityId = int(args[0])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicActivitiesFileName,
                                                  1, line_number, "int",
                                                  args[0]))
        try:
            periodicActivityId = int(args[1])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicActivitiesFileName,
                                                  2, line_number, "int",
                                                  args[1]))
        activityType = AperiodicEANReader.parse_activity_type(args[2], activityId)
        try:
            sourceEventId = int(args[3])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicActivitiesFileName,
                                                  4, line_number, "int",
                                                  args[3]))
        try:
            targetEventId = int(args[4])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicActivitiesFileName,
                                                  5, line_number, "int",
                                                  args[4]))
        try:
            lowerBound = int(args[5])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicActivitiesFileName,
                                                  6, line_number, "int",
                                                  args[5]))
        try:
            upperBound = int(args[6])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicActivitiesFileName,
                                                  7, line_number, "int",
                                                  args[6]))
        try:
            passengers = float(args[7])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicActivitiesFileName,
                                                  8, line_number, "int",
                                                  args[7]))

        sourceEvent = self.aperiodicEAN.getNode(sourceEventId)
        if not sourceEvent:
            raise GraphIncidentNodeNotFoundException(activityId, sourceEventId)

        targetEvent = self.aperiodicEAN.getNode(targetEventId)
        if not targetEvent:
            raise GraphIncidentNodeNotFoundException(activityId, targetEventId)

        aperiodicActivity = AperiodicActivity(activityId, periodicActivityId,
                                              activityType, sourceEvent,
                                              targetEvent, lowerBound,
                                              upperBound, passengers)
        activityAdded = self.aperiodicEAN.addEdge(aperiodicActivity)
        if not activityAdded:
            raise GraphEdgeIdMultiplyAssignedException(activityId)

    def process_aperiodic_timetable_entry(self, args: [str], line_number) -> None:
        """
        Process the content of a timetable file.
        :param args     the content of the line
        :param line_number   the line number, used for error handling
        :raise exceptions   if the line does not exactly contain 2 entries
                            if the specific types of the entries do not match
                            the expectations
                            if the event does not exist
        """
        if len(args) != 2:
            raise InputFormatException(self.aperiodicTimetableFileName,
                                       len(args), 2)
        try:
            eventId = int(args[0])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicTimetableFileName,
                                                  1, line_number, "int",
                                                  args[0]))
        try:
            time = int(args[1])
        except ValueError:
            raise(InputTypeInconsistencyException(self.aperiodicTimetableFileName,
                                                  2, line_number, "int",
                                                  args[1]))

        event = self.aperiodicEAN.getNode(eventId)
        if not event:
            raise DataIndexNotFoundException("Aperiodic event", eventId)
        event.setTime(time)
        if self.timetable is not None:
            self.timetable[event] = time

    @staticmethod
    def read(read_events: bool=True, read_activities: bool=True, read_seperate_timetable: bool=False,
             read_disposition_timetable: bool=False, config: Config=default_config, event_file_name: str = "",
             activity_file_name: str = "", timetable_file_name: str = "",
             ean: Graph[AperiodicEvent, AperiodicActivity] = None, timetable: Timetable = None,
             time_units_per_minute: int=0) -> (Graph[AperiodicEvent, AperiodicActivity], Timetable):
        """"
        Read the aperiodic EAN defined by the given file names. Will read the
        timetable, if a file name is given. The data will be appended to the
        fivfiven EAN and timetable object, if they are given. Otherwie, a new
        EAN will be created.
        :param read_events:
        :param read_activities:
        :param read_seperate_timetable:
        :param read_disposition_timetable:
        :param config:
        :param time_units_per_minute:
        :param event_file_name  the file name to read the events
        :param activity_file_name  the file name to read the activities
        :param timetable_file_name   the file name to read the timetable
        :param ean     the aperiodic ean to store the read values in
        :param timetable  the aperiodic timetable to store the read
        timetable in.
        """
        if not ean:
            ean = SimpleDictGraph()
        if time_units_per_minute == 0:
            time_units_per_minute = config.getIntegerValue("time_units_per_minute")
        if not timetable:
            timetable = Timetable(time_units_per_minute)

        if read_events and not event_file_name:
            event_file_name = config.getStringValue("default_events_expanded_file")

        if read_activities and not activity_file_name:
            activity_file_name = config.getStringValue("default_activities_expanded_file")

        if read_disposition_timetable and not timetable_file_name:
            timetable_file_name = config.getStringValue("default_disposition_timetable_file")
        elif read_seperate_timetable and not timetable_file_name:
            timetable_file_name = config.getStringValue("default_timetable_expanded_file")

        reader = AperiodicEANReader(activity_file_name, event_file_name, timetable_file_name,
                                    ean, timetable)

        if read_events:
            CsvReader.readCsv(event_file_name,
                              reader.process_aperiodic_event)
        if read_activities:
            CsvReader.readCsv(activity_file_name,
                              reader.process_aperiodic_activity)
        if read_disposition_timetable or read_seperate_timetable:
            CsvReader.readCsv(timetable_file_name,
                              reader.process_aperiodic_timetable_entry)
        return ean, timetable


class AperiodicEANWriter:
    """
    Implementation of an aperiodic EAN writer as a static method. Just call
    writeEAN.
    """

    @staticmethod
    def write(ean: Graph[AperiodicEvent, AperiodicActivity], timetable: Timetable = None,
              config: Config = default_config, write_events: bool = True, events_file_name: str = "",
              events_header: str = None, write_activities: bool = False, activities_file_name: str = "",
              activities_header: str = "", write_timetable: bool=False, write_disposition_timetable: bool=False,
              timetable_file_name: str="", timetable_header: str="") -> None:
        """
        Write the given ean. Which data should be written can be controlled
        with writeEvents, writeActivities and writeAperiodicTimetable. If no
        filenames or headers are given for a datatype and it should be written,
        the corresponding values are read from the given config (or the
        default config, if none is given). For the timetable, the data will be
        read from the events, if no timetable object is given.
        :param write_timetable:
        :param write_disposition_timetable:
        :param timetable_file_name:
        :param timetable_header:
        :param ean: the ean to write
        :param config: the config to read from, if necessary values are not given
        :param write_events: whether to write the events
        :param events_file_name: the file name to write the events to
        :param events_header: the header to write in the event file
        :param write_activities: whether to write the activities
        :param activities_file_name  the file name to write the activities to
        :param activities_header: the header to write in the activities file
        :param timetable
        """

        if write_events:
            if not events_file_name:
                events_file_name = config.getStringValue("default_events_expanded_file")
            if not events_header:
                events_header = config.getStringValue("events_header")
            CsvWriter.writeListStatic(events_file_name, ean.getNodes(), lambda e: e.toCsvStrings(None),
                                      header=events_header)

        if write_activities:
            if not activities_file_name:
                activities_file_name = config.getStringValue("default_activities_expanded_file")
            if not activities_header:
                activities_header = config.getStringValue("activities_header")
            CsvWriter.writeListStatic(activities_file_name, ean.getEdges(), AperiodicActivity.toCsvStrings,
                                      header=activities_header)

        if write_disposition_timetable:
            if not timetable_file_name:
                timetable_file_name = config.getStringValue("default_disposition_timetable_file")
            if not timetable_header:
                timetable_header = config.getStringValue("timetable_header_disposition")
        elif write_timetable:
            if not timetable_file_name:
                timetable_file_name = config.getStringValue("default_timetable_expanded_file")
            if not timetable_header:
                timetable_header = config.getStringValue("timetable_header")

        if write_disposition_timetable or write_timetable:
            CsvWriter.writeListStatic(timetable_file_name, ean.getNodes(),
                                      lambda e: e.toCsvStringsForTimetable(timetable), header=timetable_header)

    @staticmethod
    def setEventTimesFromTimetable(ean: Graph[AperiodicEvent, AperiodicActivity], timetable: Timetable) -> None:
        """
        Set the time members in the aperiodic events according to the values
        mapped to by the given timetable object.
        :param ean  the EAN in which the event times are to be updated.
        :param timetable    the timetable object from which the updated times
        shall be taken.
        """
        for event in ean.getNodes():
            try:
                event.setTime(timetable[event])
            except KeyError:
                #Ignore this. For events, that are not in the timetable, we dont want to set a new time.
                pass
