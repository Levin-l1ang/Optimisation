from core.exceptions.input_exceptions import (InputFormatException,
                                              InputTypeInconsistencyException)
from core.exceptions.data_exceptions import DataIllegalActivityTypeException
from core.io.csv import CsvReader, CsvWriter
from core.model.vehicle_scheduling import (TripType, Trip, VehicleSchedule,
                                           VehicleTour, Circulation)

from core.util.config import Config


class VehicleScheduleReader:
    """
    Class implementing mehods to read a vehicle schedule in LinTim format. Call
    read(str) or read(str, VehicleSchedule) to read.
    """
    def __init__(self, vehicleScheduleFileName: str, vehicleSchedule: VehicleSchedule):
        """
        Initialise a new VehicleScheduleReader. Afterwards
        processVehicleScheduleLine(str, int) can be used whne calling
        CsvReader(str, BiConsumer). For an easy access to a vehicle schedule,
        just call read(str) or read(str, VehicleSchedule)
        :param vehicleScheduleFileName  the file name
        :param vehicleSchedule
        """
        self.vehicleScheduleFileName = vehicleScheduleFileName
        self.vehicleSchedule = vehicleSchedule

    @staticmethod
    def parse_trip_type(inputType: str, tripNumberOfVehicle: int) -> TripType:
        """
        Parse the given input as an trip type. Will raise, if it is not valid.
        :param inputType: the input to parse
        :param tripNumberOfVehicle: the activity id. Only used for error handling.
        :return: the parsed trip type
        """

        if inputType.lower() == "empty" or inputType.lower() == "\"empty\"":
            result = TripType.EMPTY
        elif inputType.lower() == "trip" or inputType.lower() == "\"trip\"":
            result = TripType.TRIP
        else:
            raise DataIllegalActivityTypeException(tripNumberOfVehicle,
                                                   inputType)
        return result

    def process_vehicle_schedule_line(self, args: [str], lineNumber: int) -> None:
        """
        Method to process a line from a vehicle scheduling file in LinTim
        format. The read trip will be appended to the vehicle schedule provided
        in VehicleScheduleReader(str, VehicleSchedule).
        :param args     the line to process
        :param lineNumber   the number of the line read, will be used for error
        handling
        :raise exceptions   if the line is incorrectly formatted, i.e., has the
                            wrong number of columns
                            if some entry is in the wrong format.
        """
        if len(args) != 13:
            raise InputFormatException(self.vehicleScheduleFileName, len(args),
                                       13)
        try:
            circulationId = int(args[0])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  1, lineNumber, "int",
                                                  args[0]))
        try:
            vehicleId = int(args[1])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  2, lineNumber, "int",
                                                  args[1]))
        try:
            tripNumberOfVehicle = int(args[2])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  3, lineNumber, "int",
                                                  args[2]))
        tripType = VehicleScheduleReader.parse_trip_type(args[3],
                                                         tripNumberOfVehicle)
        try:
            startAperiodicEventId = int(args[4])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  5, lineNumber, "int",
                                                  args[4]))
        try:
            startPeriodicEventId = int(args[5])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  6, lineNumber, "int",
                                                  args[5]))
        try:
            startStopId = int(args[6])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  7, lineNumber, "int",
                                                  args[6]))
        try:
            startTime = int(args[7])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  8, lineNumber, "int",
                                                  args[7]))
        try:
            endAperiodicEventId = int(args[8])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  9, lineNumber, "int",
                                                  args[8]))
        try:
            endPeriodicEventId = int(args[9])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  10, lineNumber, "int",
                                                  args[9]))
        try:
            endStopId = int(args[10])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  11, lineNumber, "int",
                                                  args[10]))
        try:
            endTime = int(args[11])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  12, lineNumber, "int",
                                                  args[11]))
        try:
            lineId = int(args[12])
        except ValueError:
            raise(InputTypeInconsistencyException(self.vehicleScheduleFileName,
                                                  13, lineNumber, "int",
                                                  args[12]))

        trip = Trip(startAperiodicEventId, startPeriodicEventId, startStopId,
                    startTime, endAperiodicEventId, endPeriodicEventId,
                    endStopId, endTime, lineId, tripType)

        circulation = self.vehicleSchedule.getCirculation(circulationId)
        if not circulation:
            circulation = Circulation(circulationId)
            self.vehicleSchedule.addCirculation(circulation)

        vehicleTour = circulation.getVehicleTour(vehicleId)
        if not vehicleTour:
            vehicleTour = VehicleTour(vehicleId)
            circulation.addVehicle(vehicleTour)

        vehicleTour.addTrip(tripNumberOfVehicle, trip)

    @staticmethod
    def read(file_name: str = "", vehicle_schedule: VehicleSchedule = None,
             config: Config = Config.getDefaultConfig()) -> VehicleSchedule:

        """
        Read the vehicle schedule defined by the given file name. If no
        vehicleSchedule is given, a new one will be created.
        :param config:
        :param file_name   the file name to read
        :param vehicle_schedule   the vehicle schedule to add the data to.
        :return  the vehicle schedule.
        """

        if not vehicle_schedule:
            vehicle_schedule = VehicleSchedule()
        if not file_name:
            file_name = config.getStringValue("default_vehicle_schedule_file")
        reader = VehicleScheduleReader(file_name,
                                       vehicle_schedule)
        CsvReader.readCsv(file_name,
                          reader.process_vehicle_schedule_line)
        return vehicle_schedule


class VehicleScheduleWriter:
    """
    Class implementing writing methods for a vehicle schedule. Output will be
    in LinTim compatible file format. Call
    writeVehicleSchedule(VehicleScheduleVehicleSchedule) or
    writeVehicleSchedule(VehicleSchedule, Config)
    """
    @staticmethod
    def write(vehicleSchedule: VehicleSchedule, config: Config = Config.getDefaultConfig(),
              file_name: str = "", header: str = "") -> None:
        """
        Write the given vehicle schedule to the file, given in the config.
        OutpuOutput will be in LinTim compatible format.
        :param vehicleSchedule     the vehicle schedule to write
        :param config   the config to read from if no other parameters are
        given
        :param file_name  the file name to write the schedule to
        :param header    the header to write in the vehicle
        schedule file.
        """
        if not file_name:
            file_name = config.getStringValue("default_vehicle_schedule_file")
        if not header:
            header = config.getStringValue("vehicle_schedule_header")

        writer = CsvWriter(file_name, header)
        for circulation in vehicleSchedule.getCirculations():
            for vehicleTour in circulation.getVehicleTourList():
                trip_list = sorted(vehicleTour.getTripsWithIds(), key=lambda entry: entry[0])
                for tripEntry in trip_list:
                    writer.writeLine(vehicleSchedule.toCsvStrings(
                        circulation.getCirculationId(),
                        vehicleTour.getVehicleId(),
                        tripEntry[0]))
        writer.close()
