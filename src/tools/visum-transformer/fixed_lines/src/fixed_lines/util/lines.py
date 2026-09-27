from typing import Dict, List, Tuple
import logging

from core.model.graph import Graph
from core.model.lines import LinePool, Line
from core.model.periodic_ean import LineDirection
from core.model.ptn import Link, Stop
from core.util.config import Config
from common.model.net import Net, NetSection
from common.util import constants
from common.util.constants import VEHICLE_UNITS_SHORT_NAME_HEADER, \
    VEHICLE_UNITS_CAPACITY_HEADER
from common.util.net_helper import transform_time_to_minutes, transform_time_to_hour, convert_time_to_time_units

logger = logging.getLogger(__name__)


def get_fixed_lines_from_visum(net: Net, veh_units_net: Net, ptn: Graph[Stop, Link], hour_to_consider: int,
                               period_length: int, time_units_per_minute: int) -> \
    (LinePool, Dict[Line, int], Dict[Line, Tuple[str, str, str]]):
    """
    Get a linepool containing the fixed lines from the net file object
    :param net: the net file object to read the lines from
    :param ptn: the underlying ptn
    :return: a line pool containing only the lines to fix
    """
    store_stops_in_translator(ptn)
    store_links_in_translator(ptn, net)
    name_combination_dict = find_fixed_line_names_and_unit_combinations(net)
    fixed_lines, line_to_name_dict = read_fixed_lines(ptn, net, list(name_combination_dict.keys()))
    line_to_full_name_dict = compute_frequencies(fixed_lines, line_to_name_dict, net, hour_to_consider, period_length, time_units_per_minute)
    capacities = find_line_capacities(veh_units_net, line_to_name_dict, name_combination_dict)
    return fixed_lines, capacities, line_to_full_name_dict


def find_line_capacities(net: Net, line_to_name_dict: Dict[Line, Tuple[str, str]], name_combination_dict: Dict[Tuple[str, str], str]) \
        -> Dict[Line, int]:
    capacity_lookup_section = net.get_section(constants.VEHICLE_UNITS_SECTION_HEADER)
    result = {}
    capacities = {}
    for row in capacity_lookup_section.get_rows():
        sys_code = capacity_lookup_section.get_entry_from_row(row, VEHICLE_UNITS_SHORT_NAME_HEADER)
        capacity = capacity_lookup_section.get_entry_from_row(row, VEHICLE_UNITS_CAPACITY_HEADER)
        capacities[sys_code] = int(capacity)
    for line, name in line_to_name_dict.items():
        line_name, line_route_name = name
        sys_code = name_combination_dict[line_name, line_route_name]
        capacity = capacities[sys_code]
        result[line] = capacity
    return result


def store_stops_in_translator(ptn: Graph[Stop, Link]) -> None:
    """
    Store all the stops in a translator, i.e., by their short name
    :param ptn: the ptn to store
    """
    for stop in ptn.getNodes():
        StopTranslator.add_stop(stop, stop.getShortName())


def store_links_in_translator(ptn: Graph[Stop, Link], net: Net) -> None:
    """
    Store all links in a translator, i.e., by their name in the net file object
    :param ptn: the ptn to store
    :param net: the net file object to find the names
    """
    edge_section = net.get_section(constants.LINK_SECTION_HEADER)
    for visum_link_line in edge_section.get_rows():
        name = edge_section.get_entry_from_row(visum_link_line, constants.LINK_NUMBER_HEADER)
        from_name = edge_section.get_entry_from_row(visum_link_line, constants.LINK_FROM_NODE_HEADER)
        to_name = edge_section.get_entry_from_row(visum_link_line, constants.LINK_TO_NODE_HEADER)
        try:
            from_stop = StopTranslator.get_stop(from_name)
        except KeyError:
            logger.warning(f"Could not find stop with short name {from_stop}")
            LinkTranslator.add_link(None, name)
            return
        try:
            to_stop = StopTranslator.get_stop(to_name)
        except KeyError:
            logger.warning(f"Could not find stop with short name {to_name}")
            LinkTranslator.add_link(None, name)
            return
        corresponding_link = get_link(ptn, from_stop, to_stop)
        if not corresponding_link:
            logger.warning("Could not find link for visum link {}, going from {} to {}".format(name, from_stop, to_stop))
            #raise ValueError("Could not find link for visum link {}".format(name))
        LinkTranslator.add_link(corresponding_link, name)


def find_fixed_line_names_and_unit_combinations(net: Net) -> Dict[Tuple[str, str], str]:
    """
    Find all fixed line names and the corresponding sys code. The returned dict will contain the line name as
    key and the sys code as value
    :param net: the net to search in
    :return: a dict containing the line names and their corresponding sys codes
    """
    line_section = net.get_section(constants.LINE_SECTION_HEADER)
    name_to_syscode_dict = {}
    for row in line_section.get_rows():
        syscode = line_section.get_entry_from_row(row, constants.LINE_VSYSCODE_HEADER)
        if syscode == constants.NON_FIXED_VSYSCODE:
            continue
        name = line_section.get_entry_from_row(row, constants.LINE_NAME_HEADER)
        name_to_syscode_dict[name] = syscode
        logger.debug(f"Found line {name} with syscode {syscode}")
    result = {}
    line_route_section = net.get_section(constants.LINE_ROUTE_SECTION_HEADER)
    for row in line_route_section.get_rows():
        line_name = line_route_section.get_entry_from_row(row, constants.LINE_ROUTE_LINE_NAME_HEADER)
        if line_name not in name_to_syscode_dict:
            continue
        route_name = line_route_section.get_entry_from_row(row, constants.LINE_ROUTE_ROUTE_NAME_HEADER)
        result[line_name, route_name] = name_to_syscode_dict[line_name]
    return result


def read_fixed_lines(ptn: Graph[Stop, Link], net: Net, fixed_line_names: List[Tuple[str, str]]) -> (LinePool, Dict[Line, Tuple[str, str]]):
    """
    Read all the fixed lines from the net file object and return them, including a map pointing to their net file names
    :param ptn: the underlying ptn
    :param net: the net file object to read from
    :param fixed_line_names: the names of the fixed lines
    :return: the line pool, containing the fixed lines, and a dict storing the line names by the lines
    """
    logger.debug("Reading fixed lines")
    fixed_costs = Config.getDoubleValueStatic("lpool_costs_fixed")
    costs_length = Config.getDoubleValueStatic("lpool_costs_length")
    costs_edges = Config.getDoubleValueStatic("lpool_costs_edges")
    line_routes = net.get_section(constants.LINE_ROUTE_ITEMS_SECTION_HEADER)
    current_line_id = 1
    pool = LinePool()
    names: Dict[Line, Tuple[str, str]] = {}
    considered_line_names = set()
    for line_name, line_route_name in fixed_line_names:
        current_line_route = [line for line in line_routes.get_rows()
                              if line_routes.get_entry_from_row(line, constants.LINE_ROUTE_ITEMS_LINE_NAME_HEADER) == line_name and line_routes.get_entry_from_row(line, constants.LINE_ROUTE_ITEMS_ROUTE_NAME_HEADER) == line_route_name]
        node_dict: Dict[int, Stop] = {}
        logger.debug(f"Processing line {line_name}, {line_route_name}")
        if not current_line_route:
            logger.debug("Skip")
            continue
        found_all_stops = True
        for line_route_element in current_line_route:
            if line_routes.get_entry_from_row(line_route_element, constants.LINE_ROUTE_ITEMS_DIRECTION_HEADER) == LineDirection.BACKWARDS.value:
                # We only need to consider one direction for undirected networks, for directed networks there is no backwards direction
                continue
            try:
                node_dict[int(line_routes.get_entry_from_row(line_route_element, constants.LINE_ROUTE_ITEMS_INDEX_HEADER))] = StopTranslator.get_stop(line_routes.get_entry_from_row(line_route_element,
                                                                                                                                                                                 constants.LINE_ROUTE_ITEMS_NODE_HEADER))
            except KeyError:
                logger.warning(f"Could not find stop {line_routes.get_entry_from_row(line_route_element, constants.LINE_ROUTE_ITEMS_NODE_HEADER)}, will skip line {line_name}")
                found_all_stops = False
                break
        if not found_all_stops:
            logger.debug("Skipping line")
            continue
        node_list = [node_dict[index+1] for index in range(len(node_dict))]
        logger.debug([stop.getShortName() for stop in node_list])
        link_stop_list = list(zip(node_list, node_list[1:]))
        link_list = [get_link(ptn, source, target) for (source, target) in link_stop_list]
        if len(link_list) == 0:
            logger.debug(f"Line {line_name}, {line_route_name} with nodes {node_dict} is empty")
            continue
        new_line = Line(current_line_id, ptn.isDirected(), cost=fixed_costs)
        current_line_id += 1
        found_all_edges = True
        for index, link in enumerate(link_list):
            if not link:
                logger.debug(f"Could not find link between nodes {link_stop_list[index][0]} and {link_stop_list[index][1]}, will skip line")
                found_all_edges = False
                break
            new_line.addLink(link, True, costs_length, costs_edges)
        if not found_all_edges:
            logger.debug("Skip line")
            continue
        pool.addLine(new_line)
        considered_line_names.add((line_name, line_route_name))
        names[new_line] = line_name, line_route_name
    # There may be lines in an undirected network that are only present in Visum in the backwards direction.
    # This is the case if there are multiple line_routes and not every line route is present in every direction.
    # We will stitch this together later in the frequency setting.
    if not ptn.isDirected():
        for line_name, line_route_name in fixed_line_names:
            if (line_name, line_route_name) in considered_line_names:
                continue
            logger.debug(f"Reconsidering {line_name}, {line_route_name}")
            current_line_route = [line for line in line_routes.get_rows()
                                  if line_routes.get_entry_from_row(line, constants.LINE_ROUTE_ITEMS_LINE_NAME_HEADER) == line_name and line_routes.get_entry_from_row(line, constants.LINE_ROUTE_ITEMS_ROUTE_NAME_HEADER) == line_route_name]
            node_dict: Dict[int, Stop] = {}
            if not current_line_route:
                logger.debug("Skip")
                continue
            found_all_stops = True
            for line_route_element in current_line_route:
                if line_routes.get_entry_from_row(line_route_element,
                                                  constants.LINE_ROUTE_ITEMS_DIRECTION_HEADER) == LineDirection.FORWARDS.value:
                    # Forward direction was considered earlier
                    continue
                try:
                    node_dict[int(line_routes.get_entry_from_row(line_route_element,
                                                                 constants.LINE_ROUTE_ITEMS_INDEX_HEADER))] = StopTranslator.get_stop(
                        line_routes.get_entry_from_row(line_route_element,
                                                       constants.LINE_ROUTE_ITEMS_NODE_HEADER))
                except KeyError:
                    logger.warning(
                        f"Could not find stop {line_routes.get_entry_from_row(line_route_element, constants.LINE_ROUTE_ITEMS_NODE_HEADER)}, will skip line {line_name}")
                    found_all_stops = False
                    break
            if not found_all_stops:
                continue
            node_list = [node_dict[index + 1] for index in range(len(node_dict))]
            logger.debug([stop.getShortName() for stop in node_list])
            link_stop_list = list(zip(node_list, node_list[1:]))
            link_list = [get_link(ptn, source, target) for (source, target) in link_stop_list]
            if len(link_list) == 0:
                logger.debug(f"Line {line_name}, {line_route_name} with nodes {node_dict} is empty")
                continue
            new_line = Line(current_line_id, ptn.isDirected(), cost=fixed_costs)
            current_line_id += 1
            links = []
            for index, link in enumerate(link_list):
                if not link:
                    logger.debug(
                        f"Could not find link between nodes {link_stop_list[index][0]} and {link_stop_list[index][1]}, will skip line")
                    continue
                links.append(link)
            # Now add the links in opposite order, since this is the backwards direction
            for link in links[::-1]:
                new_line.addLink(link, True, costs_length, costs_edges)
            pool.addLine(new_line)
            considered_line_names.add((line_name, line_route_name))
            names[new_line] = line_name, line_route_name

    logger.debug("Created {} lines".format(current_line_id-1))
    return pool, names


def get_link(ptn: Graph[Stop, Link], source: Stop, target: Stop) -> Link:
    """
    Get the corresponding link to the two stops. Can return the undirected link, if there is no directed link and the
    ptn is undirected.
    :param ptn: the ptn to search in
    :param source: the source stop
    :param target: the target stop
    :return: the corresponding link to the two stops. May be undirected, if the ptn is undirected
    """
    link = ptn.get_edge_by_function(lambda l: l.getLeftNode() == source and l.getRightNode() == target, True)
    if not link and not ptn.isDirected():
        link = ptn.get_edge_by_function(lambda l: l.getRightNode() == source and l.getLeftNode() == target, True)
    return link


def compute_frequencies(pool: LinePool, line_names: Dict[Line, Tuple[str, str]], net: Net, hour_to_consider: int,
                        period_length: int, time_units_per_minute: int) -> Dict[Line, Tuple[str, str, str]]:
    """
    Find the frequencies to the given line pool from the net file object
    :param pool: the lines to search for
    :param line_names: the names of the lines in the net file object
    :param net: the net file object to read the frequencies from
    """
    result = {}
    line_traversals = net.get_section(constants.LINE_TRAVERSAL_SECTION_HEADER)
    # First, iterate all lines and store their frequencies. Afterwards we will find equivalent line routes and
    # compute the real frequency
    line_route_frequencies = {}
    for line in pool.getLines():
        line_name, route_name = line_names[line]
        logger.debug("Try to find frequency for line {}, {}, new id {}".format(line_name, route_name, line.getId()))
        frequency, time_profile = find_frequency_per_direction(line_traversals, line_name, route_name,
                                                               LineDirection.FORWARDS, hour_to_consider,
                                                               period_length, time_units_per_minute)
        if line.getLinePath().isDirected():
            # For directed lines, we are already done
            if frequency > 0:
                logger.debug("Found frequency")
            line.setFrequency(int(frequency))
            result[line] = (line_name, route_name, time_profile)
        else:
            # For undirected lines, store the results and check later if we can construct an undirected line
            reverse_frequency, reverse_time_profile = find_frequency_per_direction(line_traversals, line_name,
                                                                                   route_name,
                                                                                   LineDirection.BACKWARDS,
                                                                                   hour_to_consider,
                                                                                   period_length, time_units_per_minute)
            line_route_frequencies[line_name, route_name] = ((frequency, reverse_frequency),
                                                               (time_profile, reverse_time_profile))
    # Now lets see if we find frequencies for the undirected lines
    logger.debug("Check for undirected lines")
    used_line_routes = set()
    # First, fit all lines together, where the same line route works in both directions
    for line in pool.getLines():
        if line.getLinePath().isDirected():
            continue
        line_name, route_name = line_names[line]
        logger.debug("Try to find frequency for line {}, {}".format(line_name, route_name))
        (frequency, reverse_frequency), (time_profile, reverse_time_profile) = line_route_frequencies[line_name, route_name]
        if frequency == reverse_frequency:
            if frequency > 0:
                logger.debug(f"Found frequency + {frequency}")
            # Found the same frequency for this line route, can use it
            line.setFrequency(int(frequency))
            result[line] = (line_name, route_name, time_profile + "_" + reverse_time_profile)
            used_line_routes.add(line_name + "_" + route_name + ">")
            used_line_routes.add(line_name + "_" + route_name + "<")
    # Now, there are only undirected line left, where the forward and backwards direction of the same line route do
    # not have the same frequency. Try finding equivalent line routes (i.e. same line and same links) that have the
    # same frequency
    logger.debug("Search equivalent line routes")
    for line in pool.getLines():
        if line.getLinePath().isDirected():
            continue
        line_name, route_name = line_names[line]
        if line_name + "_" + route_name + ">" in used_line_routes:
            continue
        logger.debug("Try to find frequency for line {}, {}".format(line_name, route_name))
        logger.debug(f"Search for {line.getLinePath().getEdges()}")
        (frequency, _), (time_profile, _) = line_route_frequencies[line_name, route_name]
        for potential_reverse_line in pool.getLines():
            reverse_line_name, reverse_route_name = line_names[potential_reverse_line]
            if line_name != reverse_line_name or route_name == reverse_route_name or \
                    reverse_line_name + "_" + reverse_route_name + "<" in used_line_routes:
                continue
            logger.debug(f"Checking {reverse_line_name}, {reverse_route_name}")
            # Now we have the same line_name but a different route name (same route name was already checked above).
            # First, check frequencies
            (_, reverse_frequency), (_, reverse_time_profile) = line_route_frequencies[reverse_line_name, reverse_route_name]
            logger.debug(f"Frequency {reverse_frequency} for time profile {reverse_time_profile}")
            if frequency == reverse_frequency:
                # Are the links equivalen?
                if potential_reverse_line.getLinePath().getEdges() == line.getLinePath().getEdges():
                    logger.debug(f"Match!")
                    if frequency > 0:
                        logger.debug(f"Found frequency + {frequency}")
                    line.setFrequency(int(frequency))
                    result[line] = (line_name, route_name+"_"+reverse_route_name, time_profile + "_" + reverse_time_profile)
                    used_line_routes.add(line_name + "_" + route_name + ">")
                    used_line_routes.add(reverse_line_name + "_" + reverse_route_name + "<")
                    break
    return result


def find_frequency_per_direction(line_traversals: NetSection, line_name: str, line_route_name: str,
                                 direction: LineDirection, hour_to_consider: int,
                                 period_length: int, time_units_per_minute: int) -> Tuple[int, str]:
    relevant_rows = {}
    for row in line_traversals.get_rows():
        if line_traversals.get_entry_from_row(row, constants.LINE_TRAVERSAL_LINE_NAME_HEADER) != line_name or \
            line_traversals.get_entry_from_row(row, constants.LINE_TRAVERSAL_ROUTE_NAME_HEADER) != line_route_name or \
            line_traversals.get_entry_from_row(row, constants.LINE_TRAVERSAL_DIRECTION_HEADER) != direction.value:
            continue
        hour = transform_time_to_hour(line_traversals.get_entry_from_row(row, constants.LINE_TRAVERSAL_TIME_HEADER))
        if hour != hour_to_consider:
            continue
        time_profile = line_traversals.get_entry_from_row(row, constants.LINE_TRAVERSAL_TIME_PROFILE_HEADER)
        if time_profile not in relevant_rows:
            relevant_rows[time_profile] = []
        relevant_rows[time_profile].append(line_traversals.get_entry_from_row(row, constants.LINE_TRAVERSAL_TIME_HEADER))
    if len(relevant_rows) == 0:
        logger.warning(f"Found no relevant rows for {line_name}, {line_route_name}, {direction}, skip")
        return 0, ""
    time_profile = max(relevant_rows.keys(), key=lambda p: len(relevant_rows[p]))
    logger.debug(f"Choose time profile {time_profile} for {line_name}, {line_route_name}, {direction}")
    first_hour_line_traversals = []
    for line_traversals_time in relevant_rows[time_profile]:
        hour = transform_time_to_hour(line_traversals_time)
        if hour != hour_to_consider:
            continue
        first_hour_line_traversals.append(convert_time_to_time_units(line_traversals_time, time_units_per_minute))
    max_frequency = len(first_hour_line_traversals)
    first_hour_line_traversals.sort()
    logger.debug(f"Determining frequency of line {line_name}, {line_route_name}, {direction}")
    logger.debug(f"All departures: {first_hour_line_traversals}")
    # Check if the departure in the first hour where periodic, otherwise find the biggest frequency that is periodic
    for real_frequency in range(max_frequency, 0, -1):
        if found_periodic_departures(real_frequency, first_hour_line_traversals, period_length):
            return real_frequency, time_profile
    return 0, ""


def found_periodic_departures(frequency: int, departures: List[int], period_length: int) -> bool:
    # Iterate the departures, see if there are `frequency` periodic departures after it
    headway = period_length / frequency
    # TODO: Adapt here to be able to handle frequencies that do not divide the period length, i.e. allow buffer times
    for i, first_departure_time in enumerate(departures):
        if i + frequency > len(departures):
            # It is not possible to find `frequency` periodic departures starting here
            break
        j = 1
        found_all_departures = True
        for next_departure_time in departures[i+1:]:
            if next_departure_time > first_departure_time + j * headway:
                # Did not find the next departure!
                found_all_departures = False
                break
            if next_departure_time == first_departure_time + j * headway:
                # Found departure, search for the next
                j += 1
        if j == frequency and found_all_departures:
            return True
    return False


def merge_pools(read_pool: LinePool, fixed_lines: LinePool, directed: bool, capacities: Dict[Line, int],
                line_to_name_dict: Dict[Line, Tuple[str, str, str]]) -> Dict[int, Tuple[str, str, str]]:
    """
    Merge the two line pools into one
    :param capacities:
    :param read_pool: the base pool
    :param fixed_lines: the lines to add
    :param directed: whether the lines should be directed
    """
    if not read_pool.getLines():
        next_id = 1
    else:
        next_id = max([line.getId() for line in read_pool.getLines()]) + 1
    logger.debug("Add {} new lines to the pool of {} lines, max id in old pool was {}".format(len(fixed_lines.getLines()),len(read_pool.getLines()),next_id-1))
    fixed_lines_to_add = fixed_lines.getLines()
    # First remove all lines from the fixed line pool, since we may have line id conflicts
    for line in fixed_lines_to_add:
        fixed_lines.removeLine(line.getId())
    old_capacities = dict(capacities)
    capacities.clear()
    line_names = {}
    # Now the fixed lines are empty, iterate the copy from above and add all lines with the new consecutive id
    for line in fixed_lines_to_add:
        if line.getFrequency() == 0:
            # Skip fixed lines with frequency 0. These do not operate in the considered hour
            continue
        new_line = Line(next_id, directed, line.getLength(), line.getCost(), line.getFrequency(), line.getLinePath())
        next_id += 1
        added = read_pool.addLine(new_line)
        if not added:
            logger.debug("Unable to add line " + str(new_line))
        else:
            logger.debug(f"Added line with id {next_id-1}: {new_line}")
        # We can add and remove from fixed_lines since .getLines() gets a copy of the current list, this will not be
        # changed!

        capacity = old_capacities[line]
        fixed_lines.addLine(new_line)
        capacities[new_line] = capacity
        line_names[new_line.getId()] = line_to_name_dict[line]
    return line_names


class StopTranslator:
    """
    Utility class for storing stops by their name
    """
    stops: Dict[str, Stop] = {}

    @staticmethod
    def add_stop(stop: Stop, name: str) -> None:
        """
        Add the given stop to the translator
        :param stop: the stop to add
        :param name: the name of the stop
        """
        StopTranslator.stops[name] = stop

    @staticmethod
    def get_stop(name: str) -> Stop:
        """
        Get the stop for the given name
        :param name: the name to search for
        :return: the corresponding stop
        """
        return StopTranslator.stops[name]


class LinkTranslator:
    """
    Utility class for storing links by their name in the net file object
    """
    links: Dict[str, Link] = {}

    @staticmethod
    def add_link(link: Link, name: str) -> None:
        """
        Add the given link to the translator
        :param link: the link to add
        :param name: the name of the link
        """
        LinkTranslator.links[name] = link

    @staticmethod
    def get_link(name: str):
        """
        Get the link for the given name
        :param name: the name to search for
        :return: the corresponding link
        """
        return LinkTranslator.links[name]
