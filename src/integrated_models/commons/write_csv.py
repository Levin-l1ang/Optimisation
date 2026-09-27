import logging
import os
from typing import Dict

from ean_data import Ean, EanActivity, EanEvent, AperiodicEan

from line_data import LinePool, Line
from od_data import OD, ODPair
from parameters import LineFileNameParameters, PeriodicEanFileParameters, SolverParameters, VSParameters
from vehicle_schedule import Trip, VehicleSchedule

logger = logging.getLogger(__name__)


def write_line_concept(parameters: LineFileNameParameters, line_pool: LinePool, frequencies: Dict[Line, int]) -> None:
    logger.debug("Print line concept")
    create_output_folder_if_necessary(parameters.line_concept_file_name)
    line_concept_file = open(parameters.line_concept_file_name, 'w')
    line_concept_file.write("#" + parameters.line_concept_header + "\n")
    max_index = line_pool.get_max_id()
    line_range = range(1, int(max_index) + 1)
    for line_id in line_range:
        edge_index = 1
        lines = line_pool.get_lines_by_directed_id(line_id)
        frequency = 0
        for line in lines.values():
            frequency += frequencies[line]
        for edge in line_pool.get_line_by_directed_id(line_id).get_edges():
            line_concept_file.write(f"{line_id}; {edge_index}; {abs(edge.getId())}; {frequency}\n")
            edge_index += 1
    line_concept_file.close()


def write_periodic_events(parameters: PeriodicEanFileParameters, ean: Ean, od: OD,
                          arc_used: Dict[EanActivity, Dict[ODPair, Dict[int, int]]],
                          frequencies: Dict[Line, int] = None) -> None:
    logger.debug("Print events")
    create_output_folder_if_necessary(parameters.periodic_event_file_name)
    events_file = open(parameters.periodic_event_file_name, 'w')
    events_file.write("#" + parameters.periodic_event_header + "\n")
    event_index = 1
    for event in ean.get_events_network():
        if not frequencies or frequencies[event.get_line()] == 1:
            passengers = event.get_n_passengers()
            for activity in event.get_incident_activities():
                if not (activity.get_activity_type() in ['from']):
                    continue
                passengers += activity.get_n_passengers()
                for od_pair in od.get_active_od_pairs():
                    for t in range(1, od_pair.get_n_time_slices() + 1):
                        if arc_used[activity][od_pair][t] == 1:
                            passengers += od_pair.get_n_passengers(t)
            event.set_event_id(event_index)
            event.set_n_passengers(passengers)
            events_file.write(f"{event.to_events_periodic()}\n")
            event_index += 1
    events_file.close()


def write_periodic_timetable(parameters: PeriodicEanFileParameters, ean: Ean, pi: Dict[EanEvent, int],
                             frequencies: Dict[Line, int] = None) -> None:
    logger.debug("Print timetable")
    create_output_folder_if_necessary(parameters.periodic_timetable_filename)
    timetable_file = open(parameters.periodic_timetable_filename, 'w')
    timetable_file.write("#" + parameters.periodic_timetable_header + "\n")
    for event in ean.get_events_network():
        if not frequencies or frequencies[event.get_line()] == 1:
            timetable_file.write(f"{event.get_event_id()}; {pi[event]}\n")
    timetable_file.close()


def write_periodic_activities(parameters: PeriodicEanFileParameters, ean: Ean, od: OD,
                              arc_used: Dict[EanActivity, Dict[ODPair, Dict[int, int]]],
                              lines_established: Dict[EanActivity, int] = None) -> None:
    logger.debug("Print activities")
    create_output_folder_if_necessary(parameters.periodic_activity_file_name)
    activities_file = open(parameters.periodic_activity_file_name, 'w')
    activities_file.write("#" + parameters.periodic_activity_header + "\n")
    activity_index = 1
    for activity in ean.get_activities(['drive', 'wait', 'sync']):
        passengers = activity.get_n_passengers()
        if activity.get_activity_type() != 'sync':
            for od_pair in od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if arc_used[activity][od_pair][t] == 1:
                        passengers += od_pair.get_n_passengers(t)
        if not lines_established or lines_established[activity] == 1:
            activity.set_activity_id(activity_index)
            activity.set_n_passengers(passengers)
            activities_file.write("%s %d\n" % (activity.to_activities_periodic(), passengers))
            activity_index += 1
    for activity in ean.get_activities(['trans']):
        passengers = activity.get_n_passengers()
        for od_pair in od.get_active_od_pairs():
            for t in range(1, od_pair.get_n_time_slices() + 1):
                if arc_used[activity][od_pair][t] == 1:
                    passengers += od_pair.get_n_passengers(t)
        if not lines_established or lines_established[activity] == 1:
            activity.set_activity_id(activity_index)
            activity.set_n_passengers(passengers)
            activities_file.write("%s %d\n" % (activity.to_activities_periodic(), passengers))
            activity_index += 1
    activities_file.close()


def write_solver_statistic(parameters: SolverParameters, solver_time: float, gap: float, objective: float) -> None:
    create_output_folder_if_necessary(parameters.solver_statistic_file_name)
    a = open(parameters.solver_statistic_file_name, 'w')
    a.write(f'solver_time; {solver_time}\n')
    a.write(f'gap; {gap}\n')
    a.write(f'objective; {objective}\n')
    a.close()


def create_output_folder_if_necessary(filename: str):
    if os.path.dirname(filename) and not os.path.exists(os.path.dirname(filename)):
        os.makedirs(os.path.dirname(filename))


def write_aperiodic_ean(parameters: VSParameters, ean: Ean, aperiodic_ean: AperiodicEan,
                        frequencies: Dict[Line, int] = None, lines_established: Dict[EanActivity, int] = None) -> None:
    create_output_folder_if_necessary(parameters.event_expanded_file_name)
    events_expanded_file = open(parameters.event_expanded_file_name, 'w')
    events_expanded_file.write("#" + parameters.aperiodic_event_header + "\n")
    events = ean.get_events_network()
    aperiodic_event_id = 1
    for event in events:
        if not frequencies or frequencies[event.get_line()] == 1:
            for aperiodic_event in aperiodic_ean.get_aperiodic_events_by_periodic_event(event):
                aperiodic_event.reset_event_id(aperiodic_event_id)
                aperiodic_event_id += 1
                events_expanded_file.write(aperiodic_event.to_events_expanded() + "\n")
    events_expanded_file.close()
    create_output_folder_if_necessary(parameters.timetable_expanded_file_name)
    timetable_expanded_file = open(parameters.timetable_expanded_file_name, 'w')
    timetable_expanded_file.write("#" + parameters.aperiodic_timetable_header + "\n")
    for event in events:
        if not frequencies or frequencies[event.get_line()] == 1:
            for aperiodic_event in aperiodic_ean.get_aperiodic_events_by_periodic_event(event):
                timetable_expanded_file.write(aperiodic_event.to_timetable_expanded() + "\n")
    timetable_expanded_file.close()
    create_output_folder_if_necessary(parameters.activities_expanded_file_name)
    activities_expanded_file = open(parameters.activities_expanded_file_name, 'w')
    activities_expanded_file.write("#" + parameters.aperiodic_activity_header + "\n")
    aperiodic_activity_id = 1
    for activity in ean.get_activities(['drive', 'wait', 'sync']):
        if not lines_established or lines_established[activity] == 1:
            for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
                aperiodic_activity.reset_activity_id(aperiodic_activity_id)
                aperiodic_activity_id += 1
                activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")
    for activity in ean.get_activities(['trans']):
        if not lines_established or lines_established[activity] == 1:
            for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
                aperiodic_activity.reset_activity_id(aperiodic_activity_id)
                aperiodic_activity_id += 1
                activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")
    for activity in ean.get_activities(['headway']):
        if not lines_established or lines_established[activity] == 1:
            for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
                aperiodic_activity.reset_activity_id(aperiodic_activity_id)
                aperiodic_activity_id += 1
                activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")
    activities_expanded_file.close()


def write_vehicle_schedule(parameters: VSParameters, ean: Ean, aperiodic_ean: AperiodicEan, vs: VehicleSchedule,
                           duration: Dict[Line, int], period_length: int) -> None:
    create_output_folder_if_necessary(parameters.vehicle_file_name)
    vehicle_file = open(parameters.vehicle_file_name, 'w')
    vehicle_file.write("#" + parameters.vehicle_schedule_header + "\n")
    create_output_folder_if_necessary(parameters.trip_file_name)
    trip_file = open(parameters.trip_file_name, 'w')
    trip_file.write("#" + parameters.trip_header + "\n")
    create_output_folder_if_necessary(parameters.end_events_file_name)
    end_events_file = open(parameters.end_events_file_name, 'w')
    end_events_file.write("# event-id\n")
    circ_id = 1
    # get first event to save for last empty trip
    last_trip = Trip()
    vehicle = vs.get_vehicles()[0]
    last_trip.set_end(circ_id, vehicle.get_start_line(), vehicle.get_start_period(), period_length,
                      ean, aperiodic_ean)
    trip = Trip()
    first = True
    vehicle_swap_trip = Trip()
    # Transform duration dict
    for vehicle in vs.get_vehicles():
        if not first:
            vehicle_swap_trip.set_end(circ_id, vehicle.get_start_line(), vehicle.get_start_period(),
                                      period_length, ean, aperiodic_ean)
        first = False
        trip_id = 1
        vehicle_id = vehicle.get_vehicle_id()
        # First Trip:
        # output trip
        trip.trip(circ_id, trip_id, vehicle_id, vehicle.get_start_line(), vehicle.get_start_period(),
                  period_length, ean, aperiodic_ean, duration)
        vehicle_file.write(trip.to_csv() + "\n")
        trip_file.write(trip.to_csv_trip() + "\n")
        end_events_file.write(trip.to_csv_end_events() + "\n")
        for connection in vehicle.get_connections():
            # output empty trip
            trip_id += 1
            trip.empty_trip(circ_id, trip_id, vehicle_id, connection, period_length, ean,
                            aperiodic_ean, duration)
            vehicle_file.write(trip.to_csv() + "\n")
            # output trip
            trip_id += 1
            trip.trip(circ_id, trip_id, vehicle_id, connection.get_line_2(), connection.get_period_2(),
                      period_length, ean, aperiodic_ean, duration)
            vehicle_file.write(trip.to_csv() + "\n")
            trip_file.write(trip.to_csv_trip() + "\n")
            end_events_file.write(trip.to_csv_end_events() + "\n")
        # set vehicle swap trip
        trip_id += 1
        vehicle_swap_trip.set_start(trip_id, vehicle.get_vehicle_id(), vehicle.get_last_line(),
                                    vehicle.get_last_period(), period_length, ean, aperiodic_ean,
                                    duration)
    # set end of circulation
    vehicle = vs.get_vehicles()[-1]
    last_trip.set_start(trip_id, vehicle.get_vehicle_id(), vehicle.get_last_line(), vehicle.get_last_period(),
                        period_length, ean, aperiodic_ean, duration)
    vehicle_file.close()
    trip_file.close()
    end_events_file.close()
