import csv
import logging
import math

from functools import partial
from core.algorithm.dijkstra import Dijkstra
from core.io.csv import CsvWriter
from core.io.lines import LineReader
from core.io.ptn import PTNReader
from core.model.graph import Graph
from core.model.lines import Line, LinePool
from core.model.multimodal_transfer import MTType, MTEdge, MTNode
from core.model.periodic_ean import PeriodicEvent, PeriodicActivity, EventType, LineDirection, ActivityType
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.util.config import Config
from core.util.mm_ean import EAN
from core.model.ptn import Stop, Link
from core.model.impl.mapOD import MapOD

logger = logging.getLogger(__name__)

class Counter:
    """
    A helper class for obtaining consecutive IDs for, e.g., events and activities
    """
    def __init__(self, first_value: int = 0):
        self.value = first_value

    def __call__(self) -> int:
        self.value += 1
        return self.value

def read_and_update_event_modalities(ean: EAN, filename: str):
    """
    Reads event modalities from a file and updates the events in the given EAN.
    The file should have two columns: event_id;modality
    :param ean: The EAN to update
    :param filename: The file to read from
    """
    event_id_map: dict[str, dict[int, int]] = {}
    line_id_map: dict[str, dict[int, int]] = {}
    modefile = open(file=filename, mode='r')
    reader = csv.reader(modefile, delimiter=';')
    next(reader)  # Skip header
    for row in reader:
        event_id = int(row[0])
        mode = row[1]
        event = ean.getNode(event_id)
        event.modality = mode
        if mode not in event_id_map:
            event_id_map[mode] = {}
            line_id_map[mode] = {}
        event_id_map[mode][event_id] = int(row[2])
        line_id_map[mode][event_id] = int(row[3])
    modefile.close()
    return event_id_map, line_id_map


def copy_ean(ean: EAN):
    """
    Creates a deep copy of the given EAN.
    :param ean: The EAN to copy
    :return: A deep copy of the given EAN
    """
    copy: EAN = SimpleDictGraph[PeriodicEvent, PeriodicActivity]()

    for event in ean.getNodes():
        new_event = PeriodicEvent(event_id=event.getId(),
                                  stop_id=event.getStopId(),
                                  event_type=event.getType(),
                                  line_id=event.getLineId(),
                                  time=event.getTime(),
                                  number_of_passengers=event.getNumberOfPassengers(),
                                  direction=event.getDirection(),
                                  line_frequency_repetition=event.getLineFrequencyRepetition(),
                                  modality=event.modality)
        if not copy.addNode(new_event):
            raise RuntimeError('Could not add event')
    for activity in ean.getEdges():
        leftnode_id = activity.getLeftNode().getId()
        leftnode = copy.getNode(leftnode_id)
        rightnode_id = activity.getRightNode().getId()
        rightnode = copy.getNode(rightnode_id)
        new_activity = PeriodicActivity(activityId=activity.getId(),
                                        activityType=activity.getType(),
                                        sourceEvent=leftnode,
                                        targetEvent=rightnode,
                                        lowerBound=activity.getLowerBound(),
                                        upperBound=activity.getUpperBound(),
                                        numberOfPassengers=activity.getNumberOfPassengers())
        if not copy.addEdge(new_activity):
            raise RuntimeError('Could not add activity')

    return copy

class EANFinder:
    """
    A helper class to quickly find events in an EAN based on stop ID, type, and modality.
    """
    def __init__(self, ean: EAN):
        self.stopid_time_mod_dict: dict[tuple[int, int, str], list[PeriodicEvent]] = {}
        self.stopid_time_type_dict: dict[tuple[int, int, EventType], list[PeriodicEvent]] = {}
        self.stopid_type_dict: dict[tuple[int, EventType], list[PeriodicEvent]] = {}
        for event in ean.getNodes():
            self.stopid_time_mod_dict.setdefault((event.getStopId(), event.getTime(), event.modality), []).append(event)
            self.stopid_time_type_dict.setdefault((event.getStopId(), event.getTime(), event.getType()), []).append(event)
            self.stopid_type_dict.setdefault((event.getStopId(), event.getType()), []).append(event)

    def getDepartures(self, stopId: int):
        return self.stopid_type_dict.get((stopId, EventType.DEPARTURE), [])
#        return [event for event in self.ean.getNodes() if event.getStopId() == stopId and event.getType() == EventType.DEPARTURE]

    def getArrivals(self, stopId: int):
        return self.stopid_type_dict.get((stopId, EventType.ARRIVAL), [])
        #return [event for event in self.ean.getNodes() if event.getStopId() == stopId and event.getType() == EventType.ARRIVAL]

    def getEventsWithType(self, stopId: int, time: int, type: EventType):
        return self.stopid_time_type_dict.get((stopId, time, type), [])
        #return [event for event in self.ean.getNodes() if event.getStopId() == stopId and event.getType() == type]

    def getEvent(self, stopId: int, time: int, modality: str) -> PeriodicEvent:
        event_list = self.stopid_time_mod_dict.get((stopId, time, modality), [])
        if len(event_list) >= 2:
            raise RuntimeError('Multiple occurances found')
        elif len(event_list) == 1:
            return event_list[0]
        elif len(event_list) == 0:
            return RuntimeError('No event found')

    def newEvent(self, event: PeriodicEvent):
        self.stopid_time_mod_dict.setdefault((event.getStopId(), event.getTime(), event.modality), []).append(event)
        self.stopid_time_type_dict.setdefault((event.getStopId(), event.getTime(), event.getType()), []).append(event)
        self.stopid_type_dict.setdefault((event.getStopId(), event.getType()), []).append(event)


def join_nonscheduled_mode(ean: EAN, joint_graph: Graph[MTNode, MTEdge], od_nodes: MapOD, config: Config):
    """
    Adds nonscheduled activities to the EAN based on the given joint graph and OD matrix.
    :param ean: The EAN to modify
    :param joint_graph: The multimodal transfer graph to use for finding nonscheduled shortest paths
    :param od_nodes: The overall OD matrix
    :param config: The configuration
    :return: A dictionary mapping activity IDs to their transfer penalties, and a list of node IDs for the added OD helper nodes
    """
    period_length = config.getIntegerValue("period_length")
    max_walking_time = config.getDoubleValue("ean_max_nonscheduled_transfer_length");
    activity_counter = Counter(max([activity.getId() for activity in ean.getEdges()]))
    event_counter = Counter(max([event.getId() for event in ean.getNodes()]))
    penalty_per_activity = {}
    helper_nodes = []
    added_targets = []
    # Add OD helper nodes, for passengers taking a nonscheduled mode from an origin or to a destination
    for origin, targets in od_nodes.od.items():
        event_id = event_counter()
        new_arrival = PeriodicEvent(event_id, origin, EventType.ARRIVAL,
                 0, 0, 0, LineDirection.FORWARDS, 0, "OD helper")
        if not ean.addNode(new_arrival):
            logger.error(f"Failed to add nonscheduled arrival event at node {origin}")
        helper_nodes.append(event_id)
        for target, _ in targets.items():
            if origin == target:
                continue
            if not (target in added_targets):
                event_id = event_counter()
                new_departure = PeriodicEvent(event_id, target, EventType.DEPARTURE,
                        0, 0, 0, LineDirection.FORWARDS, 0, "OD helper")
                if not ean.addNode(new_departure):
                    logger.error(f"Failed to add nonscheduled arrival event at node {target}")
                helper_nodes.append(event_id)
                added_targets.append(target)
    finder = EANFinder(ean)
    stop_ids = []
    for node in joint_graph.getNodes():
        if node.getStopId() in stop_ids:
            continue
        else:
            stop_ids.append(node.getStopId())
    # Find shortest paths between all pairs of stops in the nonscheduled graph
    # that are within max_walking_time of each other, and add nonscheduled activities
    # between all arrivals at the origin and all departures at the target
    for origin in stop_ids:
        start_nodes = [node for node in joint_graph.getNodes() if node.getStopId() == origin and node.modality != ""]
        dijkstra = Dijkstra(joint_graph, start_nodes, MTEdge.getLowerBound)
        dijkstra.computeShortestPaths()
        for target in stop_ids:
            if origin == target:
                continue
            target_nodes = [node for node in joint_graph.getNodes() if node.getStopId() == target and node.modality != ""]
            distance = dijkstra.getDistance(target_nodes)
            if distance <= max_walking_time:
                path = dijkstra.getPath(target_nodes)
                edges = path.getEdges()
                # Calculate transfer penalty along the nonscheduled path (used later for calculating the actual time of the nonscheduled activity)
                transfer_penalty = 0
                for edge in edges:
                    if edge.getType == MTType.TRANSFER:
                        transfer_penalty += edge.getLowerBound
                for arrival in finder.getArrivals(origin):
                    for departure in finder.getDepartures(target):
                        activity_id = activity_counter()
                        new_activity = PeriodicActivity(activity_id, ActivityType.NONSCHEDULED,
                                                        sourceEvent=arrival, targetEvent=departure,
                                                        lowerBound=distance, upperBound=period_length+distance-1,
                                                        numberOfPassengers=0)
                        if not ean.addEdge(new_activity):
                            logger.error(f"Failed to add nonscheduled change activity from node {origin} to {target}")
                        penalty_per_activity[activity_id] = transfer_penalty
    return penalty_per_activity, helper_nodes


def distribute_passengers_penalized(ean: EAN, od: MapOD, config: Config, useTimetable: bool):
    """
    Distributes passengers in the EAN based on the given OD matrix, using a penalized shortest path approach.
    :param ean: The EAN to modify
    :param od: The overall OD matrix
    :param config: The configuration
    :param useTimetable: Whether to use the timetable for calculating activity durations
    """
    # Reset passenger counts
    for edge in ean.getEdges():
        edge.setNumberOfPassengers(0)
    for node in ean.getNodes():
        node.number_of_passengers = 0

    joint_modality_name = config.getStringValue('modality_joint_name')
    joint_ptn = PTNReader.read(config=config, modality=joint_modality_name)
    linepool_joint = LineReader.read(ptn=joint_ptn, read_frequencies=True, modality=joint_modality_name)

    for origin_stop_id, destinations in od.od.items():
        origin_nodes = [node for node in ean.getNodes() if node.getStopId() == origin_stop_id]
        if sum(destinations.values()) <= 0:
            continue
        distance_function = partial(getWeight, config=config, useTimetable=useTimetable, joint_ptn=joint_ptn, linepool_joint=linepool_joint)
        dijkstra = Dijkstra(graph=ean, start_node=origin_nodes,
                            distance_function=distance_function)
        dijkstra.computeShortestPaths()
        for destination_stop_id, demand in destinations.items():
            if origin_stop_id == destination_stop_id or demand <= 0:
                continue
            destination_nodes = [node for node in ean.getNodes()
                                 if node.getStopId() == destination_stop_id]
            shortest_path = dijkstra.getPath(destination_nodes)
            if shortest_path is None:
                raise RuntimeError(f'OD pair {origin_stop_id}-{destination_stop_id} is not connected!')
            for copy_edge in shortest_path.getEdges():
                real_edge = ean.getEdge(copy_edge.getId())
                if real_edge is None:
                    raise RuntimeError('Edge is not in graph')
                real_edge.numberOfPassengers += demand

def getWeight(activity: PeriodicActivity, config: Config, useTimetable: bool, joint_ptn=None, linepool_joint=None) -> float:
    """Calculate edge weight in an EAN.

    Logic stolen from src/essentials/javatools/src/net/lintim/generator/PeriodicPassengerDistributionGenerator.java (getWeight).
    :param activity: Activity for which we will calculate the duration.
    :param config: The configuration
    :param useTimetable: Should timetable be used, i.e., should activity lengths be determined by their durations given a timetable
    :return: Time it takes to travel the given edge.
    """
    model_initial_duration_assumption = config.getStringValue("ean_initial_duration_assumption_model")
    if model_initial_duration_assumption.upper() == "SEMI_AUTOMATIC":
        logger.warning("Initial duration assumption not implemented for multimodal EANs, proceeding with automatic setting")

    change_penalty = config.getDoubleValue('ean_change_penalty')
    intermodal_change_penalty = config.getDoubleValue('ean_intermodal_change_penalty')
    nonscheduled_penalty = config.getDoubleValue('ean_nonscheduled_penalty')
    period_length = config.getIntegerValue('period_length')
    model_drive=config.getStringValue("ean_model_weight_drive")
    model_wait=config.getStringValue("ean_model_weight_wait")
    model_change=config.getStringValue("ean_model_weight_change")
    if useTimetable:
        retval = activity.getDuration(period_length)
        if activity.getType() == ActivityType.CHANGE:
            if activity.getLeftNode().modality != activity.getRightNode().modality:
                retval += intermodal_change_penalty
            else:
                retval += change_penalty
        return retval
    else:
        joint_modality_name = config.getStringValue('modality_joint_name')
        if joint_ptn is None:
            joint_ptn = PTNReader.read(config=config, modality=joint_modality_name)

        retval = 0
        if activity.getType() == ActivityType.DRIVE:
            if model_drive.upper() == "MINIMAL_DRIVING_TIME":
                return activity.getLowerBound()
            if model_drive.upper() == "AVERAGE_DRIVING_TIME":
                return (activity.getLowerBound() + activity.getUpperBound()) / 2
            if model_drive.upper() == "MAXIMAL_DRIVING_TIME":
                return activity.getUpperBound()
            if model_drive.upper() == "EDGE_LENGTH":
                return joint_ptn.get_edge_by_nodes(activity.getLeftNode().getStopId(), activity.getRightNode().getStopId()).getLength()
        elif activity.getType() == ActivityType.WAIT:
            if model_wait.upper() == "MINIMAL_WAITING_TIME":
                return activity.getLowerBound()
            if model_wait.upper() == "AVERAGE_WAITING_TIME":
                return (activity.getLowerBound() + activity.getUpperBound()) / 2
            if model_wait.upper() == "MAXIMAL_WAITING_TIME":
                return activity.getUpperBound()
            if model_wait.upper() == "ZERO_COST":
                return 0
        elif activity.getType() == ActivityType.CHANGE:
            if linepool_joint is None:
                linepool_joint = LineReader.read(ptn=joint_ptn, read_frequencies=True, modality=joint_modality_name)
            m_1 = activity.getLeftNode().modality
            m_2 = activity.getRightNode().modality
            l_1 = linepool_joint.getLine(activity.getLeftNode().getLineId())
            l_2 = linepool_joint.getLine(activity.getRightNode().getLineId())
            f_1 = l_1.getFrequency()
            f_2 = l_2.getFrequency()
            T = period_length
            penalty = 0
            if m_1 != m_2:
                penalty = intermodal_change_penalty
            else:
                penalty = change_penalty
            if model_change.upper() == "FORMULA_1":
                return T / f_1 + T / f_2 + penalty
            if model_change.upper() == "FORMULA_2":
                return T / (2 * f_1 * f_2) + penalty
            if model_change.upper() == "FORMULA_3":
                return T / (2 * f_2) + penalty
            if model_change.upper() == "MINIMAL_CHANGING_TIME":
                return activity.getLowerBound() + penalty
        elif activity.getType() == ActivityType.DEMAND:
            return 0
        elif activity.getType() == ActivityType.NONSCHEDULED:
            return activity.getLowerBound() + nonscheduled_penalty
        else:
            # activity type is not usable by passengers, e.g. sync
            return math.inf

def makeDirected(undirected_graph: Graph[Stop, Link]) -> Graph[Stop, Link]:
    """
    Creates a copy of undirected graph with two directed edges for each undirected edge.
    Edges and Nodes are new instances.
    """
    directed_graph = SimpleDictGraph()
    running_edge_id = 1
    for node in undirected_graph.getNodes():
        newnode = Stop(node.getId(), node.getShortName(), node.getLongName(), node.getXCoordinate(), node.getYCoordinate(), node.getLongitude(), node.getLatitude(), node.getModality())
        directed_graph.addNode(newnode)
    for edge in undirected_graph.getEdges():
        leftdir = directed_graph.getNode(edge.getLeftNode().getId())
        rightdir = directed_graph.getNode(edge.getRightNode().getId())
        forward = Link(running_edge_id, leftdir, rightdir, edge.getLength(), edge.getLowerBound(), edge.getUpperBound(), True)
        running_edge_id += 1
        directed_graph.addEdge(forward)
        if edge.isDirected():
            continue
        backward = Link(running_edge_id, rightdir, leftdir, edge.getLength(), edge.getLowerBound(), edge.getUpperBound(), True)
        running_edge_id += 1
        directed_graph.addEdge(backward)
    return directed_graph

def isFeasible(ean: EAN, period: int) -> bool:
    """
    Checks whether all activities in the given EAN are feasible, i.e., whether their durations
    are within their lower and upper bounds.
    :param ean: The EAN to check
    :param period: The period length
    :return: True if all activities are feasible, False otherwise
    """
    feasible = True
    for activity in ean.getEdges():
        duration = activity.getDuration(period)
        lowerbound = activity.getLowerBound()
        upperbound = activity.getUpperBound()

        if duration < lowerbound or duration > upperbound:
            feasible = False
            print(f"Activity {activity.getId()} is not feasible. It has dur {duration}, lb {lowerbound}, ub {upperbound}.")
            print(f"Left modality {activity.getLeftNode().modality}, Right mode {activity.getRightNode().modality}")
            print(f"Type: {activity.getType()},")

    return feasible

def write_nonscheduled_transfers(ean_with_nonscheduled: EAN, penalty_per_activity: dict[int, float], artificial_nodes: list[int], config: Config):
    """
    Write nonscheduled transfers to file
    :param ean_with_nonscheduled: EAN with nonscheduled transfers
    :param penalty_per_activity: dictionary of penalties per activity
    :param artificial_nodes: list of OD helper nodes to ignore
    :param config: configuration
    """
    modefile = open(file=config.getStringValue('filename_nonscheduled_transfers'), mode='w')
    writer = csv.writer(modefile, delimiter=';')
    header = config.getStringValue("nonscheduled_transfers_header").split(";")
    header[0] = "# " + header[0]
    writer.writerow(header)
    for activity_id, penalty in penalty_per_activity.items():
        activity = ean_with_nonscheduled.getEdge(activity_id)
        if (activity.getLeftNode().getId() in artificial_nodes) or (activity.getRightNode().getId() in artificial_nodes):
            continue
        else:
            writer.writerow([str(activity_id),
                activity.getType().value.strip("\""),
                str(activity.getLeftNode().getId()),
                str(activity.getRightNode().getId()),
                CsvWriter.shortenDecimalValueForOutput(activity.getLowerBound()),
                CsvWriter.shortenDecimalValueForOutput(activity.getUpperBound()),
                CsvWriter.shortenDecimalValueForOutput(activity.getNumberOfPassengers()),
                penalty])

def add_nonscheduled_transfers(joint_ean: EAN, config: Config):
    """
    Add nonscheduled transfers from file to the joint EAN
    :param joint_ean: joint EAN to add nonscheduled transfers to
    """
    modefile = open(file=config.getStringValue('filename_nonscheduled_transfers'), mode='r')
    reader = csv.reader(modefile, delimiter=';')
    next(reader, None)  # skip the headers
    for line in reader:
        source = joint_ean.getNode(int(line[2]))
        target = joint_ean.getNode(int(line[3]))
        penalty = float(line[7]) # transfer penalties along nonscheduled transfer
        nonscheduled_transfer = PeriodicActivity(activityId=int(line[0]), activityType=ActivityType[line[1].upper()], sourceEvent=source,
                                                 targetEvent=target, lowerBound=float(line[4]) - penalty,
                                                 upperBound=float(line[5]) - penalty, numberOfPassengers=int(line[6]))
        joint_ean.addEdge(nonscheduled_transfer)
