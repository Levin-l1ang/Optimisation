import logging
from typing import Dict, Tuple, List

from core.exceptions.exceptions import LinTimException
from core.model.activityType import ActivityType
from core.model.graph import Graph
from core.model.impl.dict_graph import DictGraph
from core.model.lines import Line
from core.model.periodic_ean import PeriodicEvent, PeriodicActivity, LineDirection, EventType
from core.model.ptn import Stop
from common.model.net import Net, NetSection
from common.util import constants
from common.util.net_helper import transform_time_to_minutes, convert_time_to_time_units

logger = logging.getLogger(__name__)


def get_fixed_times(net: Net, whole_ean: Graph[PeriodicEvent, PeriodicActivity],
                    fixed_lines: Dict[Tuple[str, str, str], Line],
                    directed: bool, period_length: int, hour_to_consider: int,
                    time_units_per_minute: int) -> Graph[PeriodicEvent, PeriodicActivity]:
    """
    Get the fixed times for the given lines.
    :param net: the net file object
    :param whole_ean: the complete ean
    :param fixed_lines: the fixed lines to read the times for
    :param directed: whether the lines are directed
    :param period_length: the period length
    :return: an ean containing the fixed events
    """
    time_traversals = net.get_section(constants.LINE_TIME_TRAVERSAL_SECTION_HEADER)
    fixed_ean: Graph[PeriodicEvent, PeriodicActivity] = DictGraph()
    for line_name, line in fixed_lines.items():
        # check if we have different line routes/time profiles for the different directions
        line_route_name = line_name[1].split("_")[0]
        time_profile_name = line_name[2].split("_")[0]
        logger.debug("Searching for events for {}, {}".format(line_name, line.getId()))
        new_events = set_times_for_line(line, line_name[0], line_route_name, time_profile_name, LineDirection.FORWARDS, time_traversals,
                                        whole_ean, time_units_per_minute)
        logger.debug("Found events")
        # Now move every time by the correct start time
        start_minute = find_start_minute(net, line_name[0], line_route_name, time_profile_name, LineDirection.FORWARDS,
                                         hour_to_consider, line.getFrequency(),
                                         time_units_per_minute, period_length)
        for event in new_events:
            event.setTime(event.getTime() + start_minute)
        for new_event in new_events:
            logger.debug("Add event {}, time {}".format(new_event, new_event.getTime()))
            fixed_ean.addNode(new_event)
        if not directed:
            # check if we have different line routes/time profiles for the different directions
            line_route_name = line_name[1].split("_")[-1]
            time_profile_name = line_name[2].split("_")[-1]
            reverse_events = set_times_for_line(line, line_name[0], line_route_name, time_profile_name, LineDirection.BACKWARDS,
                                                time_traversals, whole_ean, time_units_per_minute)
            start_minute = find_start_minute(net, line_name[0], line_route_name, time_profile_name,  LineDirection.BACKWARDS,
                                             hour_to_consider, line.getFrequency(),
                                             time_units_per_minute, period_length)
            for event in reverse_events:
                event.setTime(event.getTime() + start_minute)
            for new_event in reverse_events:
                logger.debug("Add event {}, time {}".format(new_event, new_event.getTime()))
                fixed_ean.addNode(new_event)
            new_events.extend(reverse_events)
        logger.debug("Set times for line repetitions")
        set_time_for_line_repetitions(line, new_events, whole_ean, fixed_ean, period_length)
    return fixed_ean


def find_start_minute(net: Net, line_name: str, line_route_name: str, time_profile: str, direction: LineDirection,
                      hour_to_consider: int, frequency: int, time_units_per_minute: int, period_length: int) -> int:
    journeys = net.get_section(constants.TIMETABLE_START_SECTION_HEADER)
    #if direction == LineDirection.FORWARDS:
    #    direction_time_profile = time_profile.split("_")[0]
    #else:
    #    direction_time_profile = time_profile.split("_")[1]
    departure_minutes = [int(journeys.get_entry_from_row(row, constants.TIMETABLE_START_DEPARTURE_HEADER).split(":")[1])
                         for row in journeys.get_rows()
                         if journeys.get_entry_from_row(row, constants.TIMETABLE_START_DIRECTION_HEADER) == direction.value
                         and journeys.get_entry_from_row(row, constants.TIMETABLE_START_LINE_NAME_HEADER) == line_name
                         and journeys.get_entry_from_row(row, constants.TIMETABLE_START_LINE_ROUTE_NAME_HEADER) == line_route_name
                         and int(journeys.get_entry_from_row(row, constants.TIMETABLE_START_DEPARTURE_HEADER).split(":")[0]) == hour_to_consider
                         and journeys.get_entry_from_row(row, constants.TIMETABLE_START_TIME_PROFILE_NAME_HEADER) == time_profile]
    logger.debug(f"Found departure times {departure_minutes}")
    if len(departure_minutes) == 0:
        logger.error(f"Did not find entries for {line_name}, {line_route_name}, {direction}, {time_profile}")
    departure_times = [departure_minute * time_units_per_minute for departure_minute in departure_minutes]
    departure_times.sort()
    first_departure_time = find_first_periodic_departure(departure_times, frequency, period_length)
    if first_departure_time == -1:
        raise LinTimException(f"Did not find periodic departure times for {line_name}, {line_route_name}, {time_profile}, {direction}")
    return first_departure_time


def find_first_periodic_departure(departures: List[int], frequency: int, period_length: int) -> int:
    # Iterate the departures, see if there are `frequency` periodic departures after it
    headway = period_length / frequency
    # TODO: Adapt here to be able to handle frequencies that do not divide the period length, i.e. allow buffer times
    for i, first_departure_time in enumerate(departures):
        if i + frequency > len(departures):
            # It is not possible to find `frequency` periodic departures starting here
            break
        j = 1
        found_all_departures = True
        for next_departure_time in departures[i + 1:]:
            if next_departure_time > first_departure_time + j * headway:
                # Did not find the next departure!
                found_all_departures = False
                break
            if next_departure_time == first_departure_time + j * headway:
                # Found departure, search for the next
                j += 1
        if j == frequency and found_all_departures:
            return first_departure_time
    return -1


def set_times_for_line(line: Line, line_name: str, line_route_name: str, time_profile: str, direction: LineDirection,
                       time_section: NetSection, whole_ean: Graph[PeriodicEvent, PeriodicActivity],
                       time_units_per_minute: int) -> [PeriodicEvent]:
    """
    Read the times for the given line.
    :param line: the line to read the times for
    :param line_name: the line name
    :param direction: the direction of the line
    :param time_section: the net section containing the times
    :param whole_ean: the complete ean
    :return: a list of the events with fixed times belonging to the given line and direction
    """
    stop_times = get_stop_times(line_name, line_route_name, time_profile, direction, time_section, time_units_per_minute)
    result = []
    stop_list = line.getLinePath().getNodes()
    if direction == LineDirection.BACKWARDS:
        stop_list.reverse()
    # Find all corresponding events
    arr_events, dep_events = find_events(line, direction, whole_ean)
    logger.debug(f"Found events for {line}, {direction}: {arr_events}, {dep_events}")
    if len(stop_times) != len(stop_list):
        print(f"Found {len(stop_times)} visum times for {len(stop_list)} stops")
        raise RuntimeError(f"Did not find enough time entries for line {line_name}, {line_route_name}, {time_profile}")
    for stop, times, arr_event, dep_event in zip(stop_list, stop_times, [None] + arr_events, dep_events + [None]):
        if arr_event:
            arr_event.setTime(times[0])
            result.append(arr_event)
        if dep_event:
            dep_event.setTime(times[1])
            result.append(dep_event)
    return result


def get_stop_times(line_name: str, line_route_name: str, time_profile: str, direction: LineDirection, time_section: NetSection,
                   time_units_per_minute: int) -> [(int, int)]:
    """
    Get the stop times for the given line in the given direction
    :param line_name: the name of the line to search for
    :param direction: the direction of the line to search for
    :param time_section: the net section containing the times
    :return: a list of the stop times for the given line in the given direction
    """
    #if direction == LineDirection.FORWARDS:
    #    direction_time_profile = time_profile.split("_")[0]
    #else:
    #    direction_time_profile = time_profile.split("_")[1]
    time_rows = [row for row in time_section.get_rows() if
                 time_section.get_entry_from_row(row, constants.LINE_TIME_TRAVERSAL_LINE_NAME_HEADER) == line_name
                 and time_section.get_entry_from_row(row, constants.LINE_TIME_TRAVERSAL_LINE_ROUTE_NAME_HEADER) == line_route_name
                 and time_section.get_entry_from_row(row, constants.LINE_TIME_TRAVERSAL_TIME_PROFILE_NAME_HEADER) == time_profile
                 and time_section.get_entry_from_row(row,
                                                     constants.LINE_TIME_TRAVERSAL_DIRECTION_HEADER) == direction.value]
    return [(convert_time_to_time_units(time_section.get_entry_from_row(row,
                                                                       constants.LINE_TIME_TRAVERSAL_ARRIVAL_HEADER),
                                        time_units_per_minute),
             convert_time_to_time_units(time_section.get_entry_from_row(row,
                                                                       constants.LINE_TIME_TRAVERSAL_DEPARTURE_HEADER),
                                        time_units_per_minute))
            for row in time_rows]


def find_events(line: Line, direction: LineDirection, ean: Graph[PeriodicEvent, PeriodicActivity]) -> \
        (List[PeriodicEvent], List[PeriodicEvent]):
    """
    Find the events belonging to the given line in the given direction.
    :param line: the line to search for
    :param direction: the direction to search for
    :param stop: the stop to search for
    :param ean: the ean to search the events in
    :return: the two corresponding events, first arrival, second departure
    """
    first_event = ean.get_node_by_function(lambda e: e.getLineId() == line.getId() and e.getDirection() == direction
                                                     and e.getLineFrequencyRepetition() == 1
                                                     and len([a for a in ean.getIncomingEdges(e)
                                                              if a.getType() == ActivityType.WAIT
                                                              or a.getType() == ActivityType.DRIVE]) == 0,
                                           True)
    current_event = first_event
    departure_events = []
    arrival_events = []
    while True:
        if current_event.getType() == EventType.DEPARTURE:
            departure_events.append(current_event)
        else:
            arrival_events.append(current_event)
        next_activity = next(iter([a for a in ean.getOutgoingEdges(current_event)
                              if a.getType() == ActivityType.WAIT
                              or a.getType() == ActivityType.DRIVE]), None)
        if next_activity:
            current_event = next_activity.getRightNode()
        else:
            break
    return arrival_events, departure_events


def event_corresponds_to(event: PeriodicEvent, line_id: int, direction: LineDirection, stop_id: int,
                         event_type: EventType, frequency_repetiton: int) -> bool:
    """
    whether the given events corresponds to the given information
    :param event: the event to check
    :param line_id: the line id
    :param direction: the direction
    :param stop_id: the stop id
    :param event_type: the event type
    :param frequency_repetiton: the frequency repetition
    :return: whether the given event contains the given information
    """
    return event.getLineId() == line_id and event.getStopId() == stop_id and event.getType() == event_type \
           and event.getDirection() == direction and event.getLineFrequencyRepetition() == frequency_repetiton


def set_time_for_line_repetitions(line: Line, line_events: [PeriodicEvent], ean: Graph[PeriodicEvent, PeriodicActivity],
                                  fixed_ean: Graph[PeriodicEvent, PeriodicActivity], period_length: int) -> None:
    """
    Set the event times for the line repetitions.
    :param line: the line to set the times for
    :param line_events: the events for the first repetition of the line, containing the fixed times
    :param ean: the ean to search in
    :param fixed_ean: the fixed ean, the fixed events will be added here
    :param period_length: the period length
    """
    # First, get all line events
    all_line_events = [e for e in ean.getNodes() if e.getLineId() == line.getId()]
    for event in line_events:
        for frequency in range(2, line.getFrequency() + 1):
            corresponding_event = next(iter([e for e in all_line_events if e.getStopId() == event.getStopId()
                                             and e.getLineFrequencyRepetition() == frequency
                                             and e.getType() == event.getType()
                                             and e.getDirection() == event.getDirection()])
                                       , None)
            corresponding_time = (event.getTime() + (frequency-1) * (period_length/line.getFrequency())) % period_length
            corresponding_event.setTime(corresponding_time)
            fixed_ean.addNode(corresponding_event)
