import sys

from core.exceptions.exceptions import LinTimException
from core.io.lines import LineReader
from core.io.od import ODReader
from core.io.periodic_ean import PeriodicEANReader
from core.io.ptn import PTNReader
from core.io.vehicleSchedule import VehicleScheduleReader
from core.model.activityType import ActivityType
from core.model.eventType import EventType
from core.model.impl.mapOD import MapOD
from core.model.lines import LineDirection
from parameters import PTNParameters, PeriodicEanFileParameters
from ptn_data import *
from line_data import *
from od_data import *
from vehicle_schedule import *
from ean_data import *


def read_ptn(ptn: Ptn, parameters: PTNParameters) -> None:
    lintim_ptn = PTNReader.read(stop_file_name=parameters.stop_file_name, link_file_name=parameters.edge_file_name,
                                directed=parameters.directed,
                                conversion_factor_length=parameters.conversion_factor_length)
    for node in lintim_ptn.getNodes():
        ptn.add_node(node)
    for edge in lintim_ptn.getEdges():
        ptn.add_edge(edge)


def read_load(load_file_name: str, ptn: Ptn, directed: bool) -> None:
    PTNReader.read(read_stops=False, read_links=False, read_loads=True, load_file_name=load_file_name, ptn=ptn.graph)
    if not directed:
        for edge in ptn.graph.getEdges():
            ptn.get_backward_edge(edge).setLowerFrequencyBound(edge.getLowerFrequencyBound())
            ptn.get_backward_edge(edge).setUpperFrequencyBound(edge.getUpperFrequencyBound())


def read_line_pool(line_pool_file_name: str, line_pool: LinePool, ptn: Ptn, directed: bool,
                   system_frequency: int = 1, read_line_concept: bool = False,
                   restrict_to_frequency_1: bool = True, read_costs: bool = False,
                   line_cost_file_name: str = "") -> None:
    core_pool = LineReader.read(ptn.graph, read_lines=True, read_costs=read_costs, read_frequencies=read_line_concept,
                                line_file_name=line_pool_file_name, create_directed_lines=directed,
                                line_cost_file_name=line_cost_file_name)
    for core_line in core_pool.getLines():
        if read_line_concept and restrict_to_frequency_1 and core_line.getFrequency() > 1:
            raise Exception("Line concepts with frequencies > 1 are not supported!")
        if read_line_concept:
            desired_frequency = core_line.getFrequency()
        else:
            desired_frequency = system_frequency
        if not read_line_concept or core_line.getFrequency() >= 1:
            new_lines = []
            for repetition in range(1, desired_frequency + 1):
                new_line = Line(core_line.getId(), line_pool, frequency=desired_frequency, repetition=repetition)
                if read_costs:
                    new_line.set_cost(core_line.getCost())
                    new_line.set_length(core_line.getLength())
                new_lines.append(new_line)
            if not directed:
                new_backward_lines = []
                for repetition in range(1, desired_frequency + 1):
                    new_line = Line(-core_line.getId(), line_pool, undirected_line_id=core_line.getId(),
                                    frequency=desired_frequency, repetition=repetition)
                    if read_costs:
                        new_line.set_cost(core_line.getCost())
                        new_line.set_length(core_line.getLength())
                    new_backward_lines.append(new_line)
            for core_link in core_line.getLinePath().getEdges():
                ptn_link = ptn.get_edge(core_link.getId())
                for new_line in new_lines:
                    new_line.add_edge(ptn_link, ptn, directed)
                if not directed:
                    for new_line_backwards in new_backward_lines:
                        new_line_backwards.add_edge(ptn_link, ptn, directed, False)


def read_od_matrix(od_file_name: str, ptn: Ptn, od: OD, global_n_time_slices: int = 1, period_length: int = 60) -> None:
    core_od = ODReader.read(MapOD(), -1, od_file_name)
    for core_od_pair in core_od.getODPairs():
        origin = ptn.get_node(core_od_pair.getOrigin())
        destination = ptn.get_node(core_od_pair.getDestination())
        n_passengers = core_od_pair.getValue()
        n_passengers = n_passengers * period_length / 60
        od_pair = ODPair(od, origin, destination, global_n_time_slices, active=False)
        for t in range(1, od_pair.get_n_time_slices() + 1):
            od_pair.set_n_passengers(t, int(n_passengers / od_pair.get_n_time_slices()))


# Works only for frequency 1!
def read_vehicle_schedule(vehicle_schedules_file_name: str, line_pool: LinePool,
                          vehicle_schedule: VehicleSchedule, use_for_eigenmodel: bool = False,
                          vs_start_time: int = -1, vs_end_time: int = sys.maxsize) -> None:
    core_vs = VehicleScheduleReader.read(vehicle_schedules_file_name)
    drivings_of_lines = {}
    for l in line_pool.get_lines():
        drivings_of_lines[l] = []
        for core_circ in core_vs.getCirculations():
            for core_tour in core_circ.getVehicleTourList():
                for core_trip in core_tour.getTripList():
                    if (use_for_eigenmodel
                        and core_trip.getStartTime() >= vs_start_time
                        and core_trip.getEndTime() <= vs_end_time
                        and core_trip.getLineId() == l.get_undirected_line_id()
                        and core_trip.getStartStopId() == l.get_first_stop().getId()) \
                        or (not use_for_eigenmodel
                            and core_trip.getLineId() == l.get_undirected_line_id()
                            and core_trip.getStartStopId() == l.get_first_stop().getId()):
                        drivings_of_lines[l].append(core_trip.getStartTime())
                        vehicle_schedule.add_driving(l)
        drivings_of_lines[l].sort()
    current_vehicle_id = 0
    old_line = None
    old_line_period = 0
    for core_circ in core_vs.getCirculations():
        for core_tour in core_circ.getVehicleTourList():
            for core_trip in core_tour.getTripList():
                new_vehicle_id = core_tour.getVehicleId()
                # Ignore empty trips
                line_id = core_trip.getLineId()
                first_station_id = core_trip.getStartStopId()
                # Only lines which are completely in the window are considered!
                if (use_for_eigenmodel
                    and int(line_id) == -1
                    or core_trip.getStartTime() < vs_start_time
                    or core_trip.getEndTime() > vs_end_time) \
                    or (not use_for_eigenmodel
                        and line_id == -1):
                    continue
                current_line = line_pool.get_line_by_directed_id(line_id)
                if first_station_id == current_line.get_last_stop().getId():
                    current_line = line_pool.get_line_by_directed_id(-line_id)
                elif first_station_id != current_line.get_first_stop().getId():
                    raise RuntimeError(
                        "Line %d does neither start nor end with station %d!" % (line_id, first_station_id))
                current_line_period = drivings_of_lines[current_line].index(core_trip.getStartTime()) + 1
                if current_vehicle_id != new_vehicle_id:
                    vehicle_schedule.add_vehicle(Vehicle(vehicle_schedule, current_line_period, current_line))
                else:
                    vehicle_schedule.add_connection(
                        Connection(old_line, old_line_period, current_line, current_line_period))
                old_line = current_line
                old_line_period = current_line_period
                current_vehicle_id = new_vehicle_id


def read_ean(parameter: PeriodicEanFileParameters, ptn: Ptn, line_pool: LinePool, ean: Ean,
             read_timetable: bool = False) -> None:
    core_ean = PeriodicEANReader.read(event_file_name=parameter.periodic_event_file_name,
                                      activity_file_name=parameter.periodic_activity_file_name,
                                      read_timetable=read_timetable,
                                      timetable_file_name=parameter.periodic_timetable_filename)[0]
    # Read Events
    for core_event in core_ean.getNodes():
        line_id = core_event.getLineId()
        if core_event.getDirection() == LineDirection.BACKWARDS:
            line_id *= -1
        event_line = line_pool.get_line_by_directed_id_and_repetition(line_id,
                                                                      core_event.getLineFrequencyRepetition())
        stop = ptn.get_node(core_event.getStopId())
        if core_event.getType() == EventType.DEPARTURE:
            event_type = "dep"
        elif core_event.getType() == EventType.ARRIVAL:
            event_type = "arr"
        else:
            raise LinTimException(f"Unknown event type {core_event.getType()}")
        event = EanEventNetwork(ean, stop, event_line, event_type, core_event.getNumberOfPassengers(),
                                core_event.getId())
        if read_timetable:
            event.set_event_time(core_event.getTime())

    # Read Activities
    for core_activity in core_ean.getEdges():
        if core_activity.getType() == ActivityType.DRIVE:
            activity_type = 'drive'
        elif core_activity.getType() == ActivityType.WAIT:
            activity_type = 'wait'
        elif core_activity.getType() == ActivityType.CHANGE:
            activity_type = 'trans'
        elif core_activity.getType() == ActivityType.HEADWAY:
            activity_type = 'headway'
        else:
            activity_type = str(core_activity.getType())
        left_event = ean.get_event_by_event_id(core_activity.getLeftNode().getId())
        right_event = ean.get_event_by_event_id(core_activity.getRightNode().getId())
        EanActivity(left_event, right_event, activity_type, core_activity.getLowerBound(),
                    core_activity.getUpperBound(), core_activity.getNumberOfPassengers(), core_activity.getId())

    # Delete Lines that are not used
    lines_to_delete = []
    for line in line_pool.get_lines():
        line_used = False
        for event in ean.get_events_network():
            if event.get_line().get_directed_line_id() == line.get_directed_line_id():
                line_used = True
        if not line_used:
            lines_to_delete.append(line)

    for line in lines_to_delete:
        line_pool.delete_line(line)

    ean.compute_first_last_events_in_line(line_pool)
