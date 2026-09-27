import logging
from typing import Tuple, Dict

from common.model.net import Net
from common.util.constants import TIMETABLE_HOUR_TO_CONSIDER, TIMETABLE_SECTION_HEADER, TIMETABLE_INDEX_HEADER, \
    TIMETABLE_DEPARTURE_HEADER, TIMETABLE_JOURNEY_NUMBER_HEADER, TIMETABLE_LINE_NAME_HEADER, \
    TIMETABLE_LINE_DIRECTION_HEADER, TIMETABLE_NODE_NUMBER_HEADER, TIMETABLE_ARRIVAL_HEADER
from common.util.net_helper import convert_time_to_time_units
from core.model.eventType import EventType
from core.model.graph import Graph
from core.model.lines import Line, LineDirection
from core.model.periodic_ean import PeriodicActivity, PeriodicEvent
from core.model.ptn import Link, Stop

logger = logging.getLogger(__name__)


def reconstruct_timetable(line_dict: Dict[str, Tuple[Line, str]], timetable_net: Net,
                          ean: Graph[PeriodicEvent, PeriodicActivity],
                          ptn: Graph[Stop, Link], period_length: int,
                          time_units_per_minute: int) -> None:
    timetable_section = timetable_net.get_section(TIMETABLE_SECTION_HEADER)
    # First run through to find all vehicle journeys to read
    start_time_to_read = convert_time_to_time_units(f"{TIMETABLE_HOUR_TO_CONSIDER}:0:0", time_units_per_minute)
    end_time_to_read = start_time_to_read + period_length
    journeys_to_read = set()
    journey_to_line_repetition = {}
    for row in timetable_section.get_rows():
        # Check whether the current row is a first departure of a line.
        # Collect all vehicle journey numbers to read later
        index = int(timetable_section.get_entry_from_row(row, TIMETABLE_INDEX_HEADER))
        if index != 1:
            continue
        # This is the start of a line. Is the start in the correct time window?
        departure_time = timetable_section.get_entry_from_row(row, TIMETABLE_DEPARTURE_HEADER)
        departure_time_in_units = convert_time_to_time_units(departure_time, time_units_per_minute)
        if departure_time_in_units < start_time_to_read or departure_time_in_units >= end_time_to_read:
            continue
        # Collect for later reading and determine line repetition
        # Convert minutes and seconds in time_units
        line_name = timetable_section.get_entry_from_row(row, TIMETABLE_LINE_NAME_HEADER)
        line_frequency = line_dict[line_name][0].getFrequency()
        line_repetition = int((departure_time_in_units % period_length) / (period_length / line_frequency)) + 1
        journey_number = timetable_section.get_entry_from_row(row, TIMETABLE_JOURNEY_NUMBER_HEADER)
        journey_to_line_repetition[journey_number] = line_repetition
        journeys_to_read.add(journey_number)
    # For safety, collect all events where we assign times to later on check whether we missed one
    read_events = set()
    # Now iterate the section again and read all events
    for row in timetable_section.get_rows():
        journey_number = timetable_section.get_entry_from_row(row, TIMETABLE_JOURNEY_NUMBER_HEADER)
        if journey_number not in journeys_to_read:
            continue
        # Collect all data and find the correct event
        line_name = timetable_section.get_entry_from_row(row, TIMETABLE_LINE_NAME_HEADER)
        net_direction = timetable_section.get_entry_from_row(row, TIMETABLE_LINE_DIRECTION_HEADER)
        node_nr = timetable_section.get_entry_from_row(row, TIMETABLE_NODE_NUMBER_HEADER)
        stop = ptn.get_node_by_function(Stop.getShortName, node_nr)
        arrival = timetable_section.get_entry_from_row(row, TIMETABLE_ARRIVAL_HEADER)
        departure = timetable_section.get_entry_from_row(row, TIMETABLE_DEPARTURE_HEADER)
        line_tuple = line_dict[line_name]
        # Check if we need to reverse the direction
        if line_tuple[1] == ">":
            if net_direction == ">":
                line_direction = LineDirection.FORWARDS
            else:
                line_direction = LineDirection.BACKWARDS
        else:
            if net_direction == ">":
                line_direction = LineDirection.BACKWARDS
            else:
                line_direction = LineDirection.FORWARDS
        # Find the correct event
        if arrival:
            arrival_event = ean.get_node_by_function(
                lambda e: identify_event(e, stop, line_tuple[0], line_direction, EventType.ARRIVAL,
                                         journey_to_line_repetition[journey_number]), True)
            if not arrival_event:
                logger.error(
                    f"Could not find arrival event for line {line_tuple[0].getId()} at stop {stop} in direction "
                    f"{line_direction} and repetition {journey_to_line_repetition[journey_number]}")
            # Determine the correct time to set
            arrival_time_in_units = convert_time_to_time_units(arrival, time_units_per_minute)
            periodic_arrival_time = arrival_time_in_units % period_length
            arrival_event.setTime(periodic_arrival_time)
            read_events.add(arrival_event)
        if departure:
            departure_event = ean.get_node_by_function(
                lambda e: identify_event(e, stop, line_tuple[0], line_direction, EventType.DEPARTURE,
                                         journey_to_line_repetition[journey_number]), True
            )
            if not departure_event:
                logger.error(
                    f"Could not find departure event for line {line_tuple[0].getId()} at stop {stop} in direction "
                    f"{line_direction} and repetition {journey_to_line_repetition[journey_number]}")
            departure_time_in_units = convert_time_to_time_units(departure, time_units_per_minute)
            periodic_departure_time = departure_time_in_units % period_length
            departure_event.setTime(periodic_departure_time)
            read_events.add(departure_event)
    # Check if we found all events
    if len(ean.getNodes()) != len(read_events):
        for event in ean.getNodes():
            if event not in read_events:
                logger.warning(f"Did not read time for event {event}")


def identify_event(event: PeriodicEvent, stop: Stop, line: Line, line_direction: LineDirection,
                   event_type: EventType, line_repetition: int) -> bool:
    return event.getStopId() == stop.getId() and event.getLineId() == line.getId() and \
           event.getDirection() == line_direction and event.getType() == event_type and \
           event.getLineFrequencyRepetition() == line_repetition
