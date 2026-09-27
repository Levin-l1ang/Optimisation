import logging
from collections import Counter
from typing import List

from core.exceptions.config_exceptions import ConfigTypeMismatchException
from core.exceptions.data_exceptions import DataIndexNotFoundException
from core.model.graph import Graph
from core.model.impl.dict_graph import DictGraph
from core.model.periodic_ean import PeriodicActivity, PeriodicEvent, ActivityType
from core.model.ptn import Stop, Link
from robust_activities.model.buffered_activity import BufferedPeriodicActivity

logger = logging.getLogger(__file__)


def parse_buffered_links_and_stops(input_line: str, buffered_stop_percentage: float,
                                   buffer_link_percentage: float, ptn: Graph[Stop, Link],
                                   ean: Graph[PeriodicEvent, PeriodicActivity]) -> (List[Link], List[Stop]):
    """
    Parse the config line containing the links to buffer
    :param input_line: the input line from the config
    :param buffered_stop_percentage: the percentage of stops to buffer. Will buffer all wait edges at the stop. Will
            choose the percentage amount of the stops with the most transfers
    :param buffer_link_percentage: the percentage of links to buffer. Will buffer all drive edges corresponding to the
            link. Will choose the percentage amount of the links with the most drive activities associated.
    :param ptn: the ptn to search in
    :param ean: the ean to search in
    :return: the links to buffer
    """
    link_ids = input_line.split(",")
    links = []
    for link_id in link_ids:
        if not link_id:
            continue
        try:
            integer_link_id = int(link_id)
        except ValueError:
            raise ConfigTypeMismatchException("rob_buffer_link_list", "List of ints", input_line)
        link = ptn.getEdge(integer_link_id)
        if not link:
            raise DataIndexNotFoundException("Link", integer_link_id)
        links.append(link)
    # Now look for the most used stops, i.e., the stops with the most transfers
    changes_per_stop = Counter()
    for activity in ean.getEdges():
        if activity.getType() != ActivityType.CHANGE:
            continue
        changes_per_stop[ptn.getNode(activity.getLeftNode().getStopId())] += activity.getNumberOfPassengers()
    number_of_stops_to_buffer = int(buffered_stop_percentage * len(ptn.getNodes()))
    logger.debug("Buffering the {} most used stops".format(number_of_stops_to_buffer))
    stops_to_buffer = [stop for (stop, weight) in changes_per_stop.most_common(number_of_stops_to_buffer)]
    if len(stops_to_buffer) > 0:
        logger.debug("Stops to buffer: ")
        for stop in stops_to_buffer:
            logger.debug("{}".format(stop))
    # Now look for the most-used links
    activities_per_link = Counter()
    for activity in ean.getEdges():
        if activity.getType() != ActivityType.DRIVE:
            continue
        # Search for the corresponding link
        potential_links = ptn.getOutgoingEdges(ptn.getNode(activity.getLeftNode().getStopId()))
        for link in potential_links:
            if (link.getLeftNode().getId() == activity.getLeftNode().getStopId()
                and link.getRightNode().getId() == activity.getRightNode().getStopId()) or \
                (link.getRightNode().getId() == activity.getLeftNode().getStopId()
                 and link.getLeftNode().getId() == activity.getRightNode().getStopId()):
                activities_per_link[link] += 1
                break
    number_of_links_to_buffer = int(buffer_link_percentage * len(ptn.getEdges()))
    logger.debug("Buffering the {} most used links".format(number_of_links_to_buffer))
    links_to_buffer = [link for (link, weight) in activities_per_link.most_common(number_of_links_to_buffer)]
    if len(links_to_buffer) > 0:
        logger.debug("Links to buffer: ")
        for link in links_to_buffer:
            logger.debug("{}".format(link))
    return list(set(links + links_to_buffer)), stops_to_buffer


def transform_to_buffered_ean(unbuffered_ean: Graph[PeriodicEvent, PeriodicActivity]) \
        -> Graph[PeriodicEvent, BufferedPeriodicActivity]:
    """
    Transform the given unbuffered ean to its buffered equivalent
    :param unbuffered_ean: the unbuffered ean
    :return: the respective buffered ean, containing no buffers
    """
    buffered_ean: Graph[PeriodicEvent, BufferedPeriodicActivity] = DictGraph()
    for event in unbuffered_ean.getNodes():
        buffered_ean.addNode(event)
    for activity in unbuffered_ean.getEdges():
        buffered_ean.addEdge(BufferedPeriodicActivity(activity))
    return buffered_ean


def apply_buffers(ean: Graph[PeriodicEvent, BufferedPeriodicActivity], ptn: Graph[Stop, Link],
                  links_to_buffer: List[Link], stops_to_buffer: List[Stop], wait_buffer_amount: int,
                  drive_buffer_amount: int, drive_buffer_ratio: float) -> int:
    """
    Apply the given buffer to the given links in the given ean
    :param ean: the ean containing the activities to buffer
    :param ptn: the underlying ptn
    :param links_to_buffer: the links to buffer
    :param stops_to_buffer: the stops to buffer
    :param wait_buffer_amount: the amount to buffer the wait activities by
    :param drive_buffer_amount: the amount to buffer the drive activities by. Will try to buffer a drive activity up to
    drive_buffer_amount + drive_buffer_ratio*lower_bound, if the upper bound is high enough
    :param drive_buffer_ratio: the ratio to buffer the drive activities by. Will try to buffer a drive activity up to
    drive_buffer_amount + drive_buffer_ratio*lower_bound, if the upper bound is high enough
    :return: the ean with applied buffers
    """
    count_buffered_activities = 0
    for activity in ean.getEdges():
        # Check if we need to buffer the activity
        if activity.getType() == ActivityType.DRIVE:
            outgoing_edges = ptn.getOutgoingEdges(ptn.getNode(activity.getLeftNode().getStopId()))
            incoming_edges = ptn.getIncomingEdges(ptn.getNode(activity.getRightNode().getStopId()))
            link = list(set(outgoing_edges) & set(incoming_edges))[0]
            if link in links_to_buffer:
                buffer_value = min(int(round(drive_buffer_amount + drive_buffer_ratio * activity.getLowerBound())),
                                   activity.getUpperBound() - activity.getLowerBound())
                activity.set_buffer(buffer_value)
                count_buffered_activities += 1
        if activity.getType() == ActivityType.WAIT:
            if ptn.getNode(activity.getLeftNode().getStopId()) in stops_to_buffer:
                activity.set_buffer(min(float(wait_buffer_amount), activity.getUpperBound() - activity.getLowerBound()))
                count_buffered_activities += 1
    for activity in ean.getEdges():
        if activity.get_buffer() != 0:
            logger.debug("Buffering activity {}".format(activity))
            activity.set_buffer_weight(1/float(count_buffered_activities))
    return count_buffered_activities
