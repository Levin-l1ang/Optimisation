from typing import List

from core.exceptions.input_exceptions import (InputFormatException,
                                              InputTypeInconsistencyException)
from core.io.csv import CsvReader, CsvWriter
from core.model.vehicle_scheduling import TripType, Trip
from core.util.config import Config, default_config


class TripReader:
    """
    Reader for the trip data format.
    """
    def __init__(self, file_name: str, trips: List[Trip]):
        """
        Initialise a new TripReader. Afterwards processTripLine(str, int) can
        be used when calling CsvReader.readCsv(). For an easy acces to the
        trips, just call read(str) or read(str, []).
        :param file_name     the file name to read
        :param trips    the list to append the read trips to
        """
        self.tripFileName = file_name
        self.trips = trips

    def process_trip(self, args: List[str], line_number: int) -> None:
        """
        Processs a line in the LinTim trip format and append the information to
        the list given ins TripReader.
        :param args     the line to process
        :param line_number   the number of the line read, will be used for error
        handling
        :raise exceptions   if the line is incorrectly formatted, i.e., has the
        wrong number of columns
                            if some entry is in the wrong format
        """
        if len(args) != 9:
            raise InputFormatException(self.tripFileName, len(args), 9)
        try:
            startAperiodicEventId = int(args[0])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 1,
                                                  line_number, "int", args[0]))
        try:
            startPeriodicEventId = int(args[1])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 2,
                                                  line_number, "int", args[1]))
        try:
            startStopId = int(args[2])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 3,
                                                  line_number, "int", args[2]))
        try:
            startTime = int(args[3])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 4,
                                                  line_number, "int", args[3]))
        try:
            endAperiodicEventId = int(args[4])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 5,
                                                  line_number, "int", args[4]))
        try:
            endPeriodicEventId = int(args[5])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 6,
                                                  line_number, "int", args[5]))
        try:
            endStopId = int(args[6])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 7,
                                                  line_number, "int", args[6]))
        try:
            endTime = int(args[7])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 8,
                                                  line_number, "int", args[7]))
        try:
            lineId = int(args[8])
        except ValueError:
            raise(InputTypeInconsistencyException(self.tripFileName, 9,
                                                  line_number, "int", args[8]))

        trip = Trip(startAperiodicEventId, startPeriodicEventId, startStopId,
                    startTime, endAperiodicEventId, endPeriodicEventId,
                    endStopId, endTime, lineId, TripType.TRIP)

        self.trips.append(trip)

    @staticmethod
    def read(file_name: str = "", trips: List[Trip] = None, config: Config = Config.getDefaultConfig()) -> List[Trip]:
        """
        Read a list of trips form the given file name and store all read trips
        in the given list.
        :param config:
        :param file_name     the file to read
        :param trips    the vehicle schedule to append to
        """
        if not trips:
            trips = []
        if not file_name:
            file_name = config.getStringValue("default_trips_file")
        reader = TripReader(file_name, trips)
        CsvReader.readCsv(file_name, reader.process_trip)
        return trips


class TripWriter:
    """
    Class implementing write methods for a list of trips.
    """

    @staticmethod
    def write(trips: List[Trip], config: Config = default_config, file_name: str = None, header: str = None) \
            -> None:
        """
        Write the given trips to the file given in the config. Output will be
        LinTim compatible format. If no file names or headers are given,
        default names will be used instead.
        :param trips    the trips to write.
        :param config   the config to read from if necessary values are not
        given
        :param file_name    the file name to write the timetable to
        :param header      the header to write in the trips file
        """
        if not file_name:
            file_name = config.getStringValue("default_trips_file")
        if not header:
            header = config.getStringValue("trip_header")

        CsvWriter.writeListStatic(file_name, trips, Trip.toCsvStrings, header=header)
