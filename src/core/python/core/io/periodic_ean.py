from core.exceptions.data_exceptions import (DataIndexNotFoundException,
                                             DataIllegalEventTypeException,
                                             DataIllegalActivityTypeException,
                                             DataIllegalLineDirectionException)
from core.exceptions.input_exceptions import (InputFormatException,
                                              InputTypeInconsistencyException)
from core.exceptions.graph_exceptions import (GraphEdgeIdMultiplyAssignedException,
                                              GraphIncidentNodeNotFoundException,
                                              GraphNodeIdMultiplyAssignedException)
from core.io.csv import CsvReader, CsvWriter
from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.periodic_ean import PeriodicEvent, PeriodicActivity, EventType, LineDirection, ActivityType, \
    PeriodicTimetable
from core.util.config import Config
from core.util.mm_utilities import add_modality_prefix


class PeriodicEANReader:
    """
    Class to process csv-lines, formatted in the LinTim
    Activities-periodic.giv, Events-periodic.giv or Timetable-periodic.tim
    format. Use a CsvReader with an instance of this class to read a periodic
    activities, events or timetable file.
    """

    def __init__(self, periodic_activities_file_name: str,
                 periodic_events_file_name: str,
                 periodic_timetable_file_name: str,
                 periodic_ean: Graph[PeriodicEvent, PeriodicActivity],
                 periodic_timetable: PeriodicTimetable = None):
        """
        Constructor for a PeriodicEANReader for given file names and periodic
        EAN.  The given names will not influence
        the read file but the used name in any error message, so be sure to use
        the same name in here and in the CsvReader!
        :param periodic_activities_file_name: source file name for periodic
        activities
        :param periodic_events_file_name: source file name for periodic events
        :param periodic_timetable_file_name: source file name for periodic
        timetable
        :param periodic_ean: periodic event activity network (Graph instance)
        to be filled
        :param periodic_timetable: PeriodicTimetable instance to be filled.
        Will only be used if any is given.
        """
        self.periodic_activities_file_name = periodic_activities_file_name
        self.periodic_events_file_name = periodic_events_file_name
        self.periodic_timetable_file_name = periodic_timetable_file_name
        self.periodic_ean = periodic_ean
        self.periodic_timetable = periodic_timetable

    @staticmethod
    def parse_eventType(input_type: str, eventId: int) -> EventType:
        """
        Parse the given input as an event type. Will raise, if it is not valid.
        :param input_type: the input to parse
        :param eventId: the event id. Only used for error handling
        :return: the parsed event type
        """
        if input_type.lower() == "arrival" or input_type.lower() == "\"arrival\"":
            result = EventType.ARRIVAL
        elif input_type.lower() == "departure" or input_type.lower() == "\"departure\"":
            result = EventType.DEPARTURE
        elif input_type.lower() == "fix" or input_type.lower() == "\"fix\"":
            result = EventType.FIX
        elif input_type.lower() == "virtual" or input_type.lower() == "\"virtual\"":
            result = EventType.VIRTUAL
        else:
            raise DataIllegalEventTypeException(eventId, input_type)
        return result

    @staticmethod
    def parse_line_direction(input_direction: str, eventId: int) -> LineDirection:
        """
        Parse the given input as a line direction. Will raise, if it is not valid.
        :param input_direction: the input to parse
        :param eventId:  the event id. Only used for error handling
        :return: the parsed line direction
        """
        if input_direction == ">":
            result = LineDirection.FORWARDS
        elif input_direction == "<":
            result = LineDirection.BACKWARDS
        else:
            raise DataIllegalLineDirectionException(eventId, input_direction)
        return result

    @staticmethod
    def parse_activity_type(input_type: str, activity_id: int) -> ActivityType:
        """
        Parse the given input as an activity type. Will raise, if it is not valid.
        :param input_type: the input to parse
        :param activity_id: the activity id. Only used for error handling.
        :return: the parsed activity type
        """
        if input_type.lower() == "drive" or input_type.lower() == "\"drive\"":
            result = ActivityType.DRIVE
        elif input_type.lower() == "wait" or input_type.lower() == "\"wait\"":
            result = ActivityType.WAIT
        elif input_type.lower() == "change" or input_type.lower() == "\"change\"":
            result = ActivityType.CHANGE
        elif input_type.lower() == "headway" or input_type.lower() == "\"headway\"":
            result = ActivityType.HEADWAY
        elif input_type.lower() == "turnaround" or input_type.lower() == "\"turnaround\"":
            result = ActivityType.TURNAROUND
        elif input_type.lower() == "sync" or input_type.lower() == "\"sync\"":
            result = ActivityType.SYNC
        elif input_type.lower() == "virtual" or input_type.lower() == "\"virtual\"":
            result = ActivityType.VIRTUAL
        elif input_type.lower() == "nonscheduled" or input_type.lower() == "\"nonscheduled\"":
            result = ActivityType.NONSCHEDULED
        else:
            raise DataIllegalActivityTypeException(activity_id, input_type)
        return result

    def processPeriodicEvent(self, args: [str], lineNumber: int) -> None:
        """
        Process the content of a periodic event file
        :param args: the content of the line
        :param lineNumber: the line number, used for error handling
        """
        if len(args) != 7:
            raise InputFormatException(self.periodic_events_file_name, len(args), 7)
        try:
            eventId = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_events_file_name, 1, lineNumber, "int", args[0])
        eventType = PeriodicEANReader.parse_eventType(args[1], eventId)
        try:
            stopId = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_events_file_name, 3, lineNumber, "int", args[2])
        try:
            lineId = int(args[3])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_events_file_name, 4, lineNumber, "int", args[3])
        try:
            passengers = float(args[4])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_events_file_name, 5, lineNumber, "float", args[4])
        direction = PeriodicEANReader.parse_line_direction(args[5], eventId)
        try:
            frequency_repetition = int(args[6])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_events_file_name, 7, lineNumber, "int", args[6])
        event = PeriodicEvent(eventId, stopId, eventType, lineId, 0, passengers, direction, frequency_repetition)
        could_add_event = self.periodic_ean.addNode(event)
        if not could_add_event:
            raise GraphNodeIdMultiplyAssignedException(eventId)

    def processPeriodicActivity(self, args: [str], lineNumber: int) -> None:
        """
        Process the content of a periodic activity file.
        :param args: the content of the line
        :param lineNumber: the line number, used for error handling
        """
        if len(args) != 7:
            raise InputFormatException(self.periodic_activities_file_name, len(args), 7)
        try:
            activity_id = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_activities_file_name, 1, lineNumber, "int", args[0])
        activity_type = PeriodicEANReader.parse_activity_type(args[1], activity_id)
        try:
            source_eventId = int(args[2])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_activities_file_name, 3, lineNumber, "int", args[2])
        try:
            target_eventId = int(args[3])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_activities_file_name, 4, lineNumber, "int", args[3])
        try:
            lower_bound = int(args[4])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_activities_file_name, 5, lineNumber, "int", args[4])
        try:
            upper_bound = int(args[5])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_activities_file_name, 6, lineNumber, "int", args[5])
        try:
            number_of_passengers = float(args[6])
        except ValueError:
            raise(InputTypeInconsistencyException(
                self.periodic_activities_file_name, 7, lineNumber, "float",
                args[6]))

        source_event = self.periodic_ean.getNode(source_eventId)
        if not source_event:
            raise GraphIncidentNodeNotFoundException(activity_id, source_eventId)
        target_event = self.periodic_ean.getNode(target_eventId)
        if not target_event:
            raise GraphIncidentNodeNotFoundException(activity_id, target_eventId)

        new_activity = PeriodicActivity(activity_id, activity_type, source_event, target_event, lower_bound,
                                        upper_bound, number_of_passengers)
        could_add_activity = self.periodic_ean.addEdge(new_activity)

        if not could_add_activity:
            raise GraphEdgeIdMultiplyAssignedException(activity_id)

    def processPeriodicTimetable(self, args: [str], lineNumber: int) -> None:
        """
        Process the content of a periodic timetable file. Will write the data to the timetable object, if some was given
        in the __init__ method.
        :param args: the content of the line
        :param lineNumber: the line number, used for error handling
        """
        if len(args) != 2:
            raise InputFormatException(self.periodic_timetable_file_name, len(args), 2)
        try:
            eventId = int(args[0])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_timetable_file_name, 1, lineNumber, "int", args[0])
        try:
            event_time = int(args[1])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_timetable_file_name, 2, lineNumber, "int", args[1])
        periodic_event = self.periodic_ean.getNode(eventId)
        if not periodic_event:
            raise DataIndexNotFoundException("Periodic Event", eventId)

        periodic_event.setTime(event_time)
        if self.periodic_timetable is not None:
            self.periodic_timetable[periodic_event] = event_time

    @staticmethod
    def read(read_events: bool = True, read_activities: bool = True, read_timetable: bool = False,
             event_file_name: str = "", activity_file_name: str = "", timetable_file_name: str = "",
             periodic_ean: Graph[PeriodicEvent, PeriodicActivity]=None, periodic_timetable: PeriodicTimetable = None,
             time_units_per_minute: int = None, period_length: int = None, modality: str = "", config: Config = Config.getDefaultConfig()) \
            -> tuple[Graph[PeriodicEvent, PeriodicActivity], PeriodicTimetable]:
        """
        Read the periodic EAN defined by the given filenames. Will read the timetable, if a filename is given. The data
        will be appended to the given ean and timetable objects, if they are given. Otherwise, a new ean will be
        created.
        :param config:
        :param period_length:
        :param time_units_per_minute:
        :param read_timetable:
        :param read_activities:
        :param read_events:
        :param event_file_name: the filename to read the events
        :param activity_file_name: the filename to read the activities
        :param timetable_file_name: the filename to read the timetable
        :param periodic_ean: the ean to add the data to. If none is given, a new one will be created
        :param periodic_timetable: the periodic timetable to store the read timetable in. May be None.
        :param modality: if given, this string is prefixed to the file name as a
        prefix separated by a dot.
        :return: the ean with the added data.
        """
        if not periodic_ean:
            periodic_ean = SimpleDictGraph()
        if read_events and not event_file_name:
            event_file_name = config.getStringValue("default_events_periodic_file")
        if read_activities and not activity_file_name:
            activity_file_name = config.getStringValue("default_activities_periodic_file")
        if read_timetable and not timetable_file_name:
            timetable_file_name = config.getStringValue("default_timetable_periodic_file")
        if read_timetable and not periodic_timetable:
            if not time_units_per_minute:
                time_units_per_minute = config.getIntegerValue("time_units_per_minute")
            if not period_length:
                period_length = config.getIntegerValue("period_length")
            periodic_timetable = PeriodicTimetable(time_units_per_minute, period_length)
        if modality != "":
            event_file_name = add_modality_prefix(modality, event_file_name)
            activity_file_name = add_modality_prefix(modality, activity_file_name)
            timetable_file_name = add_modality_prefix(modality, timetable_file_name)
        reader = PeriodicEANReader(activity_file_name, event_file_name,
                                   timetable_file_name, periodic_ean, periodic_timetable)

        if read_events:
            CsvReader.readCsv(event_file_name, reader.processPeriodicEvent)
        if read_activities:
            CsvReader.readCsv(activity_file_name, reader.processPeriodicActivity)
        if read_timetable:
            CsvReader.readCsv(timetable_file_name, reader.processPeriodicTimetable)
        return periodic_ean, periodic_timetable


class PeriodicEANWriter:
    """
    Implementation of a periodic ean writer as a static method, just call writeEAN.
    """

    @staticmethod
    def write(ean: Graph[PeriodicEvent, PeriodicActivity], config: Config = Config.getDefaultConfig(),
              write_events: bool = True, events_file_name: str = "", events_header: str = "",
              write_activities: bool = True, activities_file_name: str = "", activities_header: str = "",
              write_timetable: bool = False, timetable: PeriodicTimetable = None, timetable_file_name: str = None,
              timetable_header: str = None, modality: str = ""):
        """
        Write the given ean. Which data should be written can be controlled with writeEvents, writeActivities and
        write_timetable. If no filenames or headers are given for a datatype and it should be written, the corresponding
        values are read from the given config (or the default config, if none is given). For the timetable, the data
        will be read from the events, if no timetable object is given.
        :param ean: the ean to write
        :param config: the config to read from, if necessary values are not given
        :param write_events: whether to write the events
        :param events_file_name: the file name to write the events to
        :param events_header: the header to write in the event file
        :param write_activities: whether to write the activities
        :param activities_file_name: the file name to write the activities to
        :param activities_header: the header to write in the activities file
        :param write_timetable: whether to write a timetable
        :param timetable: the timetable object to write.
        :param timetable_file_name: the file name to write the timetable to
        :param timetable_header: the header to write in the timetable file
        :param modality: if given, this string is prefixed to the file name as a
        prefix separated by a dot.
        """
        events = ean.getNodes()
        if write_events or write_timetable:
            # Sort events first, we may need to write them twice
            events.sort(key=PeriodicEvent.getId)
        if write_events:
            if not events_file_name:
                events_file_name = config.getStringValue("default_events_periodic_file")
            if not events_header:
                events_header = config.getStringValue("events_header_periodic")
            if modality != "":
                events_file_name = add_modality_prefix(modality, events_file_name)
            CsvWriter.writeListStatic(events_file_name, events, PeriodicEvent.toCsvStrings, header=events_header)

        if write_activities:
            if not activities_file_name:
                activities_file_name = config.getStringValue("default_activities_periodic_file")
            if not activities_header:
                activities_header = config.getStringValue("activities_header_periodic")
            if modality != "":
                activities_file_name = add_modality_prefix(modality, activities_file_name)
            CsvWriter.writeListStatic(activities_file_name, ean.getEdges(), PeriodicActivity.toCsvStrings,
                                      header=activities_header)

        if write_timetable:
            if not timetable_file_name:
                timetable_file_name = config.getStringValue("default_timetable_periodic_file")
            if not timetable_header:
                timetable_header = config.getStringValue("timetable_header_periodic")
            if modality != "":
                timetable_file_name = add_modality_prefix(modality, timetable_file_name)
            # Decide whether to write times from timetable object or from ean
            if timetable:
                def output_function(e):
                    return [str(e.getId()), str(timetable[e])]
            else:
                output_function = PeriodicEvent.toCsvTimetableStrings
            CsvWriter.writeListStatic(timetable_file_name, events, output_function, header=timetable_header)
