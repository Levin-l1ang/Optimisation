from core.exceptions.graph_exceptions import GraphNodeIdMultiplyAssignedException, GraphIncidentNodeNotFoundException, \
    GraphEdgeIdMultiplyAssignedException
from core.exceptions.input_exceptions import InputFormatException, InputTypeInconsistencyException
from core.io.csv import CsvReader
from core.io.periodic_ean import PeriodicEANReader
from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.periodic_ean import PeriodicEvent, PeriodicActivity, PeriodicTimetable
from core.util.config import Config


class PeriodicEANReaderTimPassLib(PeriodicEANReader):

    def processPeriodicEventTimPassLib(self, args: [str], lineNumber: int) -> None:
        """
        Process the content of a periodic event file
        :param args: the content of the line
        :param lineNumber: the line number, used for error handling
        """
        if len(args) != 6:
            raise InputFormatException(self.periodic_events_file_name, len(args), 6)
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
        direction = PeriodicEANReader.parse_line_direction(args[4], eventId)
        try:
            frequency_repetition = int(args[5])
        except ValueError:
            raise InputTypeInconsistencyException(self.periodic_events_file_name, 6, lineNumber, "int", args[5])
        event = PeriodicEvent(eventId, stopId, eventType, lineId, 0, 0, direction, frequency_repetition)
        could_add_event = self.periodic_ean.addNode(event)
        if not could_add_event:
            raise GraphNodeIdMultiplyAssignedException(eventId)


    def processPeriodicActivityTimPassLib(self, args: [str], lineNumber: int) -> None:
        """
        Process the content of a periodic activity file.
        :param args: the content of the line
        :param lineNumber: the line number, used for error handling
        """
        if len(args) != 6:
            raise InputFormatException(self.periodic_activities_file_name, len(args), 6)
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

        source_event = self.periodic_ean.getNode(source_eventId)
        if not source_event:
            raise GraphIncidentNodeNotFoundException(activity_id, source_eventId)
        target_event = self.periodic_ean.getNode(target_eventId)
        if not target_event:
            raise GraphIncidentNodeNotFoundException(activity_id, target_eventId)

        new_activity = PeriodicActivity(activity_id, activity_type, source_event, target_event, lower_bound,
                                        upper_bound, 0)
        could_add_activity = self.periodic_ean.addEdge(new_activity)

        if not could_add_activity:
            raise GraphEdgeIdMultiplyAssignedException(activity_id)

    @staticmethod
    def readTimPassLib(read_events: bool = True, read_activities: bool = True, read_timetable: bool = False,
                       event_file_name: str = "", activity_file_name: str = "", timetable_file_name: str = "",
                       periodic_ean: Graph[PeriodicEvent, PeriodicActivity] = None,
                       periodic_timetable: PeriodicTimetable = None,
                       time_units_per_minute: int = None, period_length: int = None,
                       config: Config = Config.getDefaultConfig()) \
        -> (Graph[PeriodicEvent, PeriodicActivity], PeriodicTimetable):
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
        :return: the ean with the added data.
        """
        if not periodic_ean:
            periodic_ean = SimpleDictGraph()
        if read_events and not event_file_name:
            event_file_name = config.getStringValue("filename_timpasslib_events")
        if read_activities and not activity_file_name:
            activity_file_name = config.getStringValue("filename_timpasslib_activities")
        if read_timetable and not timetable_file_name:
            timetable_file_name = config.getStringValue("filename_timpasslib_timetable")
        if read_timetable and not periodic_timetable:
            if not time_units_per_minute:
                time_units_per_minute = config.getIntegerValue("time_units_per_minute")
            if not period_length:
                period_length = config.getIntegerValue("period_length")
            periodic_timetable = PeriodicTimetable(time_units_per_minute, period_length)
        reader = PeriodicEANReaderTimPassLib(activity_file_name, event_file_name,
                                   timetable_file_name, periodic_ean, periodic_timetable)

        if read_events:
            CsvReader.readCsv(event_file_name, reader.processPeriodicEventTimPassLib)
        if read_activities:
            CsvReader.readCsv(activity_file_name, reader.processPeriodicActivityTimPassLib)
        if read_timetable:
            CsvReader.readCsv(timetable_file_name, reader.processPeriodicTimetable)
        return periodic_ean, periodic_timetable