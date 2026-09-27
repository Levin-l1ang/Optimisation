"""Utilities for joining multiple EANs to one."""
import csv
from itertools import product

from core.model.graph import Graph
from core.model.impl.simple_dict_graph import SimpleDictGraph
from core.model.periodic_ean import (
    ActivityType, EventType, PeriodicActivity,
    PeriodicEvent, PeriodicTimetable,
)
from core.util.config import Config
from core.util.mm_utilities import add_modality_prefix

EAN = Graph[PeriodicEvent, PeriodicActivity]


def _get_node(node: PeriodicEvent, node_id: int, modality: str, line_id_map_mode: dict[int, int]):
    """
    Create a new node with given id and modality from the given node.
    :param node: The node to copy
    :param node_id: The id of the new node
    :param modality: The modality of the new node
    :param line_id_map_mode: The map from old line ids to joint ids
    :return: The new node
    """
    return PeriodicEvent(
        event_id=node_id,
        stop_id=node.getStopId(),
        event_type=node.getType(),
        line_id=line_id_map_mode[node.getLineId()],
        number_of_passengers=node.getNumberOfPassengers(),
        direction=node.getDirection(),
        line_frequency_repetition=node.getLineFrequencyRepetition(),
        time=node.getTime(),
        modality=modality,
    )


def _get_edge(edge: PeriodicActivity, edge_id: int, id_map: dict[int, PeriodicEvent]):
    """
    Create a new edge with given id from the given edge, using the given id map to
    map the source and target nodes.
    :param edge: The edge to copy
    :param edge_id: The id of the new edge
    :param id_map: The map from old node ids to new nodes
    :return: The new edge
    """
    return PeriodicActivity(
        activityId=edge_id,
        activityType=edge.getType(),
        sourceEvent=id_map[edge.getLeftNode().event_id],
        targetEvent=id_map[edge.getRightNode().event_id],
        lowerBound=edge.getLowerBound(),
        upperBound=edge.getUpperBound(),
        numberOfPassengers=edge.getNumberOfPassengers(),
    )


class EANJoiner:
    """Class for joining EANs of multiple modalities to a single EAN."""

    def __init__(
        self,
        eans: dict[str, EAN],
        timetables: dict[str, PeriodicTimetable | None],
        min_change_time: float,
        max_change_time: float,
        period: int,
        units_per_minute: int,
        line_id_map: dict[str, dict[int, int]],
        config: Config = Config.getDefaultConfig(),
    ) -> None:
        """Initialise the EANJoiner.

        :param eans: Dict of EANs, keyed by modality name.
        :param timetables: Dict of optional timetables, keyed by modality name.
        :param min_change_time: Minimum time for changing between modalities.
        :param max_change_time: Maximum time for changing between modalities.
        :param period: The period of the given timetables.
        :param units_per_minute: Units of time per minute.
        :param line_id_map: A dictionary of dictionaries mapping the line IDs in each modality line concept to those in the joint line concept 
        :param config: The config to read additional parameters from.
        """
        self.eans = eans
        self.min_change_time = min_change_time
        self.max_change_time = max_change_time
        self.period = period
        self.units_per_minute = units_per_minute
        self.timetables = timetables
        self.line_id_map = line_id_map
        self.config = config

        self.rolling_node_id = 0
        self.rolling_edge_id = 0
        self._joint_graph = SimpleDictGraph[PeriodicEvent, PeriodicActivity]()
        self._joint_timetable = PeriodicTimetable(
            period=period,
            timeUnitsPerMinute=units_per_minute,
        )
        self._join_ean_called = False
        self.stop_id_map: dict[int, list[PeriodicEvent]] = {}
        
        # self._origin_map: dict[int, int] = {}
        # self._destination_map: dict[int, int] = {}
        # self._add_od_called = False

    def _next_node_id(self) -> int:
        self.rolling_node_id += 1
        return self.rolling_node_id

    def _next_edge_id(self) -> int:
        self.rolling_edge_id += 1
        return self.rolling_edge_id

    @property
    def joint_ean(self) -> EAN:
        """Get the joined EAN.

        Raises RuntimeError if join_eans() is not called before calling this.
        """
        if self._join_ean_called:
            return self._joint_graph
        else:
            raise RuntimeError('EANJoiner.join_eans() must be called first!')

    @property
    def joint_timetable(self) -> PeriodicTimetable:
        """Get the joined timetable.

        Raises RuntimeError if join_eans() is not called before calling this.
        """
        if self._join_ean_called:
            return self._joint_timetable
        else:
            raise RuntimeError('EANJoiner.join_eans() must be called first!')

    def join_eans(self):
        """Join EANs and timetables."""
        self._add_graphs()
        self._add_intermodal_changes()
        self._join_ean_called = True

    def _add_graphs(self):
        # Add all nodes and edges of the individual EANs to the joint graph.
        # Note that change & headway activities must be added as the very last step ->
        # collect those activities along with the node id maps to process other
        # eans before adding them.
        joint_modality_name = self.config.getStringValue('modality_joint_name')
        event_modality_filename = self.config.getStringValue('filename_event_modalities')
        modefile = open(file=add_modality_prefix(joint_modality_name, event_modality_filename), mode='w')
        writer = csv.writer(modefile, delimiter=';')
        header = self.config.getStringValue("event_modalities_header").split(";")
        header[0] = "# " + header[0]
        writer.writerow(header)
        postponed_edges: list[tuple[PeriodicActivity, dict[int, PeriodicEvent]]] = []
        for modality, ean in self.eans.items():
            timetable = self.timetables[modality]
            node_id_map: dict[int, PeriodicEvent] = {}
            for node in ean.getNodes():
                new_node = _get_node(node, self._next_node_id(), modality, self.line_id_map[modality])
                if not self._joint_graph.addNode(new_node):
                    raise RuntimeError('Could not add the node!')
                node_id_map[node.event_id] = new_node
                self.stop_id_map.setdefault(node.getStopId(), []).append(new_node)
                if timetable is not None:
                    self._joint_timetable[new_node] = timetable[node]
                writer.writerow([str(new_node.getId()), modality, str(node.getId()), str(node.getLineId())])
            for edge in ean.getEdges():
                if edge.type in (ActivityType.CHANGE, ActivityType.HEADWAY):
                    postponed_edges.append((edge, node_id_map))
                    continue
                new_edge = _get_edge(edge, self._next_edge_id(), node_id_map)
                if not self._joint_graph.addEdge(new_edge):
                    raise RuntimeError('Could not add the edge!')
        for edge, node_id_map in postponed_edges:
            new_edge = _get_edge(edge, self._next_edge_id(), node_id_map)
            if not self._joint_graph.addEdge(new_edge):
                raise RuntimeError('Could not add the edge!')

    def _add_intermodal_changes(self):
        # Add change activities between modalities.
        # For each arrival node, check all departure nodes at the same stop.
        # If modalities do not match, add a change activity.
        arrival_nodes = [
            node for node in self._joint_graph.getNodes()
            if node.getType() == EventType.ARRIVAL
        ]
        departure_nodes = [
            node for node in self._joint_graph.getNodes()
            if node.getType() == EventType.DEPARTURE
        ]
        # generate change edges between modalities
        # NOTE: ignores change stop limitations that may be present
        for arrival, departure in product(arrival_nodes, departure_nodes):
            # Generate change activity only if modalities do not match and stop ids
            # are equal.
            if (
                arrival.modality == departure.modality
                or arrival.stop_id != departure.stop_id
            ):
                continue
            # Check if changing on previous stop is possible. In that case we will
            # not generate an change activity, as it will always be redundant.
            arrival_ingoing = [
                edge for edge in self._joint_graph.getIncomingEdges(arrival)
                if edge.getType() == ActivityType.DRIVE
            ]
            departure_outgoing = [
                edge for edge in self._joint_graph.getOutgoingEdges(departure)
                if edge.getType() == ActivityType.DRIVE
            ]
            if len(arrival_ingoing) != 1 or len(departure_outgoing) != 1:
                raise RuntimeError(
                    'EAN is invalid: node can have only one in/outgoing drive edge.',
                )

            if arrival_ingoing[0].getLeftNode() == departure_outgoing[0].getRightNode():
                # Changing on previous stop is equivalent / always better from
                # delay POV
                continue

            new_change = PeriodicActivity(
                activityId=self._next_edge_id(),
                activityType=ActivityType.CHANGE,
                sourceEvent=arrival,
                targetEvent=departure,
                lowerBound=self.min_change_time,
                upperBound=self.max_change_time,
                numberOfPassengers=0,  # Not defined yet as no routing is done.
            )
            if not self._joint_graph.addEdge(new_change):
                raise RuntimeError('Could not add the edge!')

