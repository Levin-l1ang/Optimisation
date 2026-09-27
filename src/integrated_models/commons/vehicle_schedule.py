import logging
from typing import List
from typing import Dict
import line_data
import ean_data

from core.model.ptn import Stop
from core.util.constants import SECONDS_PER_MINUTE
from parameters import VSParameters

logger = logging.getLogger(__name__)


class VehicleSchedule:
    def __init__(self, line_pool: line_data.LinePool) -> None:
        self.max_vehicle_id = 0
        self.vehicles = []
        self.connections = []
        self.drivings_per_line = {}
        for line in line_pool.get_lines():
            self.drivings_per_line[line.get_directed_line_id()] = 0

    def add_connection(self, connection: "Connection") -> None:
        self.connections.append(connection)

    def add_vehicle(self, vehicle: "Vehicle") -> None:
        self.vehicles.append(vehicle)
        self.max_vehicle_id += 1

    def get_connections(self) -> List["Connection"]:
        return self.connections

    def get_vehicles(self) -> List["Vehicle"]:
        return self.vehicles

    def add_driving(self, line: line_data.Line) -> None:
        self.drivings_per_line[line.get_directed_line_id()] += 1

    def get_drivings(self, line: line_data.Line) -> int:
        return self.drivings_per_line[line.get_directed_line_id()]

    def get_max_vehicle_id(self) -> int:
        return self.max_vehicle_id

    def add_connections_from_ip_model(self,
                                      vehicle_connect: Dict[int, Dict[line_data.Line,
                                                                      Dict[int, Dict[line_data.Line, int]]]],
                                      p_max: int, line_pool: line_data.LinePool,
                                      vs_allow_empty_trips) -> None:
        for p_1 in range(1, p_max + 1):
            for l_1 in line_pool.get_lines():
                for p_2 in range(1, p_max + 1):
                    for l_2 in line_pool.get_lines():
                        if vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            if vehicle_connect[p_1][l_1][p_2][l_2] == 1:
                                self.add_connection(Connection(l_1, p_1, l_2, p_2))
        for line in line_pool.get_lines():
            self.drivings_per_line[line.get_directed_line_id()] = p_max


class Connection:
    def __init__(self, line_1: line_data.Line, period_1: int, line_2: line_data.Line, period_2: int):
        self.line_1 = line_1
        self.line_2 = line_2
        self.period_1 = period_1
        self.period_2 = period_2

    def get_line_1(self) -> line_data.Line:
        return self.line_1

    def get_line_2(self) -> line_data.Line:
        return self.line_2

    def get_period_1(self) -> int:
        return self.period_1

    def get_period_2(self) -> int:
        return self.period_2

    def to_string(self) -> str:
        return "(%d,%s)(%d,%s)" % (self.period_1, self.line_1.to_string(), self.period_2, self.line_2.to_string())


class Vehicle:
    def __init__(self, vehicle_schedule: VehicleSchedule, start_period: int, start_line: line_data.Line) -> None:
        self.vehicle_id = vehicle_schedule.get_max_vehicle_id() + 1
        self.connections = []
        self.start_period = start_period
        self.start_line = start_line
        self.last_period = start_period
        self.last_line = start_line

    def add_connection(self, trip: "Connection") -> None:
        self.connections.append(trip)

    def get_vehicle_id(self) -> int:
        return self.vehicle_id

    def get_connections(self) -> List[Connection]:
        return self.connections

    def get_start_line(self) -> line_data.Line:
        return self.start_line

    def get_start_period(self) -> int:
        return self.start_period

    def get_last_period(self) -> int:
        return self.last_period

    def get_last_line(self) -> line_data.Line:
        return self.last_line

    def find_all_connections(self, connections: List[Connection]) -> None:
        add_connection = True
        while add_connection:
            add_connection = False
            for connection in connections:
                if connection.get_period_1() == self.last_period and connection.get_line_1() == self.last_line:
                    self.add_connection(connection)
                    self.last_period = connection.get_period_2()
                    self.last_line = connection.get_line_2()
                    add_connection = True
                    break


class Trip:

    def __init__(self) -> None:
        self.circ_id = -1
        self.vehicle_id = -1
        self.trip_id = -1
        self.trip_type = ""
        self.start_event = None
        self.periodic_start_event = None
        self.start_station: Stop = None
        self.start_time = -1
        self.end_event = None
        self.periodic_end_event = None
        self.end_station: Stop = None
        self.end_time = -1
        self.line_id = ""

    # trip from line, period
    def trip(self, circ_id: int, trip_id: int, vehicle_id: int, line: line_data.Line, period: int, period_length: int,
             periodic_ean: "ean_data.Ean", aperiodic_ean: "ean_data.AperiodicEan",
             duration: Dict[line_data.Line, int]) -> None:
        self.circ_id = circ_id
        self.vehicle_id = vehicle_id
        self.trip_id = trip_id
        self.trip_type = "TRIP"
        self.start_event = aperiodic_ean.get_aperiodic_starting_event(periodic_ean, line, period, period_length)
        self.periodic_start_event = periodic_ean.get_first_event_in_line(line)
        self.start_station = line.get_first_stop()
        self.start_time = self.start_event.get_aperiodic_time_with_offset()
        self.end_event = aperiodic_ean.get_aperiodic_ending_event(periodic_ean, line, period, duration, period_length)
        self.periodic_end_event = periodic_ean.get_last_event_in_line(line)
        self.end_station = line.get_last_stop()
        self.end_time = self.end_event.get_aperiodic_time_with_offset()
        self.line_id = line.get_undirected_line_id()

    # empty trip from connection
    def empty_trip(self, circ_id: int, trip_id: int, vehicle_id: int, connection: Connection, period_length: int,
                   periodic_ean: "ean_data.Ean", aperiodic_ean: "ean_data.AperiodicEan",
                   duration: Dict[line_data.Line, int]) -> None:
        self.circ_id = circ_id
        self.vehicle_id = vehicle_id
        self.trip_id = trip_id
        self.trip_type = "EMPTY"
        self.start_event = aperiodic_ean.get_aperiodic_ending_event(periodic_ean, connection.get_line_1(),
                                                                    connection.get_period_1(), duration, period_length)
        self.periodic_start_event = periodic_ean.get_last_event_in_line(connection.get_line_1())
        self.start_station = connection.get_line_1().get_last_stop()
        self.start_time = self.start_event.get_aperiodic_time_with_offset()
        self.end_event = aperiodic_ean.get_aperiodic_starting_event(periodic_ean, connection.get_line_2(),
                                                                    connection.get_period_2(), period_length)
        self.periodic_end_event = periodic_ean.get_last_event_in_line(connection.get_line_2())
        self.end_station = connection.get_line_2().get_first_stop()
        self.end_time = self.end_event.get_aperiodic_time_with_offset()
        self.line_id = -1

    def set_end(self, circ_id: int, line: line_data.Line, period: int, period_length: int, periodic_ean: "ean_data.Ean",
                aperiodic_ean: "ean_data.AperiodicEan") -> None:
        self.circ_id = circ_id
        self.trip_type = "EMPTY"
        self.end_event = aperiodic_ean.get_aperiodic_starting_event(periodic_ean, line, period, period_length)
        self.periodic_end_event = periodic_ean.get_first_event_in_line(line)
        self.end_station = line.get_first_stop()
        self.end_time = self.end_event.get_aperiodic_time_with_offset()
        self.line_id = -1

    def set_start(self, trip_id: int, vehicle_id: int, line: line_data.Line, period: int, period_length: int,
                  periodic_ean: "ean_data.Ean", aperiodic_ean: "ean_data.AperiodicEan",
                  duration: Dict[line_data.Line, int]) -> None:
        self.vehicle_id = vehicle_id
        self.trip_id = trip_id
        self.start_event = aperiodic_ean.get_aperiodic_ending_event(periodic_ean, line, period, duration, period_length)
        self.periodic_start_event = periodic_ean.get_last_event_in_line(line)
        self.start_station = line.get_last_stop()
        self.start_time = self.start_event.get_aperiodic_time_with_offset()

    # to csv
    def to_csv(self) -> str:
        return '%d; %d; %d; %s; %d; %d; %d; %d; %d; %d; %d; %d; %s' % (
            self.circ_id, self.vehicle_id, self.trip_id, self.trip_type, self.start_event.get_event_id(),
            self.periodic_start_event.get_event_id(), self.start_station.getId(), self.start_time * SECONDS_PER_MINUTE,
            self.end_event.get_event_id(), self.periodic_end_event.get_event_id(), self.end_station.getId(),
            self.end_time * SECONDS_PER_MINUTE, self.line_id)

    def to_csv_trip(self) -> str:
        return '%d; %d; %d; %d; %d; %d; %d; %d; %s' % (
            self.start_event.get_event_id(),
            self.periodic_start_event.get_event_id(), self.start_station.getId(), self.start_time * SECONDS_PER_MINUTE,
            self.end_event.get_event_id(), self.periodic_end_event.get_event_id(), self.end_station.getId(),
            self.end_time * SECONDS_PER_MINUTE, self.line_id)

    def to_csv_end_events(self) -> str:
        return '%d' % self.end_event.get_event_id()


def construct_vehicle_schedule_from_ip(line_pool: line_data.LinePool,
                                       vehicle_connect: Dict[int, Dict[line_data.Line,
                                                                       Dict[int, Dict[line_data.Line, int]]]],
                                       parameters: VSParameters,
                                       vehicle_from_depot: Dict[int, Dict[line_data.Line, int]],
                                       allow_empty_trips: bool) -> VehicleSchedule:
    logger.debug("Construct vehicle schedule")
    vehicle_schedule = VehicleSchedule(line_pool)
    vehicle_schedule.add_connections_from_ip_model(vehicle_connect, parameters.p_max, line_pool,
                                                   allow_empty_trips)
    logger.debug("Add vehicles:")
    for p_1 in range(1, parameters.p_max + 1):
        for l_1 in line_pool.get_lines():
            if vehicle_from_depot[p_1][l_1] == 1:
                vehicle_schedule.add_vehicle(Vehicle(vehicle_schedule, p_1, l_1))
    for vehicle in vehicle_schedule.get_vehicles():
        vehicle.find_all_connections(vehicle_schedule.get_connections())
    return vehicle_schedule