import logging
from typing import Tuple, Union

import ean_data
from core.solver.generic_solver_interface import Model, Variable, VariableType, OptimizationSense, ConstraintSense, \
    Status, DoubleAttribute, DoubleParam
from line_data import *
from parameters import ODActivatorParameters, PeriodicEanParameters, TimetableObjectiveParameters, SolverParameters
from ptn_data import *
from od_data import *
import networkx as nx

logger = logging.getLogger(__name__)


class PtnPreprocessor:
    ptn_graph = nx.DiGraph()
    revers_graph = nx.DiGraph()
    activities_drive = set()
    activities_trans_wait = set()
    activities_sync = set()
    transfer_stations = []
    unused_pnt_edges = None

    unused_events = None
    unused_activities = None
    length_sp_upper_bound = -1

    def __init__(self, ptn: Ptn, line_pool, ean, tt_parameters: TimetableObjectiveParameters,
                 ean_parameters: PeriodicEanParameters, solver_parameters: SolverParameters):
        self.activities_drive = set(ean.get_activities(["drive"]))
        self.activities_trans_wait = set(ean.get_activities(["wait", "trans"]))
        # initialize transfer stations
        transfer_stations = PtnPreprocessor.potential_transfer_stations(solver_parameters, line_pool, ptn)

        for node in ptn.get_nodes():
            self.ptn_graph.add_node(node.getId())
            self.ptn_graph.add_node(-node.getId())
            lower_bound = min((tt_parameters.factor_travel_time + tt_parameters.factor_wait_time) *
                              ean_parameters.min_wait_time,
                              (tt_parameters.factor_travel_time + tt_parameters.factor_transfer_time) *
                              ean_parameters.min_trans_time + tt_parameters.transfer_penalty)
            if node in transfer_stations:
                upper_bound = max((tt_parameters.factor_travel_time + tt_parameters.factor_wait_time) *
                                  ean_parameters.max_wait_time,
                                  (tt_parameters.factor_travel_time + tt_parameters.factor_transfer_time) *
                                  ean_parameters.max_trans_time + tt_parameters.transfer_penalty)
            else:
                upper_bound = (tt_parameters.factor_travel_time + tt_parameters.factor_wait_time) * \
                              ean_parameters.max_wait_time
            # add split edge
            self.ptn_graph.add_edge(node.getId(), -node.getId(), is_split=True, lower_bound=lower_bound,
                                    upper_bound=upper_bound, node_id=node.getId())

        for edge in ptn.get_edges():
            left_node_id = edge.getLeftNode().getId()
            right_node_id = edge.getRightNode().getId()
            self.ptn_graph.add_edge(-left_node_id, right_node_id, is_split=False, edge_id=edge.getId(),
                                    lower_bound=(tt_parameters.factor_travel_time + tt_parameters.factor_drive_time) *
                                                edge.getLowerBound(),
                                    upper_bound=(tt_parameters.factor_travel_time + tt_parameters.factor_drive_time) *
                                                edge.getUpperBound())

        self.reverse_graph = self.ptn_graph.reverse()

    def compute_unused_ptn_edges(self, ptn: Ptn, source_node: Stop, target_node: Stop):
        unused_edges = set()
        # compute shortest s-t-path with upper_bounds
        source_node_id = -source_node.getId()
        target_node_id = target_node.getId()
        self.length_sp_upper_bound = nx.dijkstra_path_length(self.ptn_graph, source_node_id, target_node_id,
                                                        weight='upper_bound')

        # compute shortest s-i /i-t paths for all nodes

        source_to_intermediate = nx.single_source_dijkstra_path_length(self.ptn_graph, source_node_id,
                                                                       weight='lower_bound')
        intermediate_to_target = nx.single_source_dijkstra_path_length(self.reverse_graph, target_node_id,
                                                                       weight='lower_bound')
        for edge in ptn.get_edges():
            left_node_out_id = -edge.getLeftNode().getId()
            right_node_in_id = edge.getRightNode().getId()
            if source_to_intermediate[left_node_out_id] \
                    + self.ptn_graph[left_node_out_id][right_node_in_id]['lower_bound'] \
                    + intermediate_to_target[right_node_in_id] > self.length_sp_upper_bound:
                unused_edges.add(edge)
        return unused_edges

    def compute_potentially_used_events_and_activities(self, ean, ptn, od_pair):
        source_station = od_pair.get_origin()
        target_station = od_pair.get_destination()

        self.unused_ptn_edges = self.compute_unused_ptn_edges(ptn, source_station, target_station)

        self.unused_activities = set()
        self.unused_events = set()

        # Add algorithm from Preprocessing
        # Drive activities are not used, if the corresponding ptn edges are not used
        for activity in self.activities_drive:
            left_event = activity.get_left_event()
            right_event = activity.get_right_event()
            left_ptn_node = left_event.get_ptn_node()
            right_ptn_node = right_event.get_ptn_node()
            ptn_edge = ptn.get_edge_between(left_ptn_node, right_ptn_node)
            if ptn_edge in self.unused_ptn_edges:
                self.unused_activities.add(activity)
                self.unused_events.add(left_event)
                self.unused_events.add(right_event)

        # Wait and transfer edges
        for activity in self.activities_trans_wait:
            left_event = activity.get_left_event()
            right_event = activity.get_right_event()
            if left_event in self.unused_events or right_event in self.unused_events:
                self.unused_activities.add(activity)

        # Sync activities are not used in passenger paths

        used_activities = (self.activities_trans_wait | self.activities_drive) - self.unused_activities
        used_events = set(ean.get_all_events()) - self.unused_events
        return [used_events, used_activities]

    # Computes the weights on PTN edges and transfers for passengers who are not routed during the optimization
    def compute_weights_unrouted_passengers(self, ptn: Ptn, od: OD) -> Tuple[Dict[Link, float], Dict[Stop, float],
                                                                             Dict[Link, Dict[Link, float]]]:
        ptn_edge_weights = {}
        ptn_transfer_weights = {}
        ptn_wait_weights = {}
        paths = dict(nx.all_pairs_dijkstra_path(self.ptn_graph, weight='lower_bound'))
        for od_pair in od.get_all_od_pairs():
            if od_pair.is_active() or od_pair.get_n_passengers(1) == 0:
                continue
            source_station_id = -od_pair.get_origin().getId()
            target_station_id = od_pair.get_destination().getId()
            shortest_path = paths[source_station_id][target_station_id]
            for index in range(0, shortest_path.__len__()-1):
            #for node_id in shortest_path[0:-2]:
                node_id = shortest_path[index]
                node_id_next = shortest_path[index + 1]
                edge = self.ptn_graph[node_id][node_id_next]
                if not edge['is_split']:
                    ptn_edge = ptn.get_edge(edge['edge_id'])
                    if not ptn_edge_weights.__contains__(ptn_edge):
                        ptn_edge_weights[ptn_edge] = 0
                    ptn_edge_weights[ptn_edge] += od_pair.get_n_passengers(1)
                else:
                    ptn_node = ptn.get_node(edge['node_id'])
                    # Transfer stations
                    if ptn_node in self.transfer_stations:
                        # find edge before transfer
                        if index == 0:
                            raise Exception("Path cannot begin with transfer!")
                        node_id_before = shortest_path[index - 1]
                        ptn_edge_before_transfer = ptn.get_edge(self.ptn_graph[node_id_before][node_id].edge_id)
                        # find edge after transfer
                        if index >= shortest_path.length - 2:
                            raise Exception("Path cannot end with a transfer!")
                        node_id_next_next = shortest_path[index + 2]
                        ptn_edge_after_transfer = ptn.get_edge(self.ptn_graph[node_id_next][node_id_next_next].edge_id)
                        # add to ptn_transfer_weights
                        if not ptn_transfer_weights.__contains__(ptn_edge_before_transfer):
                            ptn_transfer_weights[ptn_edge_before_transfer] = {}
                        if not ptn_transfer_weights[ptn_edge_before_transfer].__contains__(ptn_edge_after_transfer):
                            ptn_transfer_weights[ptn_edge_before_transfer][ptn_edge_after_transfer] = 0
                        ptn_transfer_weights[ptn_edge_before_transfer][
                            ptn_edge_after_transfer] += od_pair.get_n_passengers(1)
                    # Waiting stations
                    else:
                        if not ptn_wait_weights.__contains__(ptn_node):
                            ptn_wait_weights[ptn_node] = 0
                        ptn_wait_weights[ptn_node] += od_pair.get_n_passengers(1)
        return ptn_edge_weights, ptn_wait_weights, ptn_transfer_weights

    # Implementation of IP model to compute the smallest number of stations which suffice as transferring
    # stations between any two lines with at least one common station.
    @staticmethod
    def potential_transfer_stations(parameters: SolverParameters, line_pool: LinePool, ptn: Ptn):
        model = PotentialTransferStationIP(ptn, line_pool, parameters)
        model.create_model()
        model.solve()
        return model.get_solution()


class EanPreprocessor:
    ean_graph = nx.DiGraph()
    revers_graph = nx.DiGraph()
    activities_drive_wait_trans = []
    unused_trans_activities = []
    unused_events = None
    unused_activities = None
    length_sp_upper_bound = -1

    def __init__(self, ean: "ean_data.Ean", parameters: ODActivatorParameters, unused_transfer_activities=None) -> None:

        if unused_transfer_activities is None:
            unused_transfer_activities = []
        factor_travel_time = parameters.factor_travel_time
        factor_transfer_time = parameters.factor_transfer_time
        factor_wait_time = parameters.factor_wait_time
        factor_drive_time = parameters.factor_drive_time
        penalty_transfer = parameters.transfer_penalty
        self.activities_drive_wait_trans = ean.get_activities(['drive', 'wait', 'trans'])

        max_event_id = 0
        max_activity_id = 0
        EanPreprocessor.unused_trans_activities = unused_transfer_activities

        for event in ean.get_all_events():
            max_event_id += 1
            event.set_event_id(max_event_id)
            self.ean_graph.add_node(event.get_event_id())

        for activity in ean.get_all_activities():
            max_activity_id += 1
            activity.set_activity_id(max_activity_id)
            if activity in unused_transfer_activities:
                continue
            if activity.get_activity_type() in ['sync', 'headway', 'time']:
                continue
            elif activity.get_activity_type() == 'drive':
                lower_bound = (factor_travel_time + factor_drive_time) * activity.get_lower_bound()
                upper_bound = (factor_travel_time + factor_drive_time) * activity.get_upper_bound()
            elif activity.get_activity_type() == 'wait':
                lower_bound = (factor_travel_time + factor_wait_time) * activity.get_lower_bound()
                upper_bound = (factor_travel_time + factor_wait_time) * activity.get_upper_bound()
            elif activity.get_activity_type() == 'trans':
                lower_bound = (factor_travel_time + factor_transfer_time) * activity.get_lower_bound() \
                              + penalty_transfer
                upper_bound = (factor_travel_time + factor_transfer_time) * activity.get_upper_bound() \
                              + penalty_transfer
            elif activity.get_activity_type() in ['to', 'from']:
                lower_bound = 0
                upper_bound = 0
            self.ean_graph.add_edge(activity.get_left_event().get_event_id(), activity.get_right_event().get_event_id(),
                                    lower_bound=lower_bound, upper_bound=upper_bound)

        self.reverse_graph = self.ean_graph.reverse()

    def compute_fixed_passenger_paths(self, ean: "ean_data.Ean", od: "od_data.OD", use_lower_bounds: bool = True) -> float:
        if use_lower_bounds:
            edge_weight = 'lower_bound'
        else:
            edge_weight = 'upper_bound'

        length_fix_passengers = 0

        for event in ean.get_events_network():
            event.set_n_passengers(0)

        for activity in self.activities_drive_wait_trans:
            activity.set_n_passengers(0)

        for od_pair in od.get_all_od_pairs():
            if od_pair.is_active() or od_pair.get_n_passengers(1) == 0:
                continue
            start_event = ean.get_event_od(od_pair.get_origin(), od_pair.get_destination(), 'source', 1, 1)
            end_event = ean.get_event_od(od_pair.get_origin(), od_pair.get_destination(), 'target', 1, None)
            length, shortest_path = nx.single_source_dijkstra(self.ean_graph, start_event.get_event_id(),
                                                              target=end_event.get_event_id(), weight=edge_weight)
            length_fix_passengers += length * od_pair.get_n_passengers(1)
            # add passenger weight to events and activities
            # first and last activities and events are excluded, as they belong to to and from arcs
            last_event_id = shortest_path[0]
            last_event = ean.get_event_by_event_id(last_event_id)
            for event_id in shortest_path[1: -1]:
                event = ean.get_event_by_event_id(event_id)
                if isinstance(event, ean_data.EanEventNetwork):
                    #event.add_passengers(od_pair.get_total_passengers())
                    # Don't check first activity
                    if not isinstance(last_event, ean_data.EanEventNetwork):
                        last_event = event
                        continue
                    for outgoing_activity in last_event.get_outgoing_activities():
                        if outgoing_activity.get_right_event().get_event_id() == event_id:
                            outgoing_activity.add_passengers(od_pair.get_total_passengers())
                            od_pair.diff_bounds_sp += outgoing_activity.get_upper_bound() - outgoing_activity.get_lower_bound()
                            if outgoing_activity.get_activity_type() == "trans":
                                od_pair.transfer_in_shortest_paths = True
                                outgoing_activity.get_left_event().add_passengers(od_pair.get_total_passengers())
                                outgoing_activity.get_right_event().add_passengers(od_pair.get_total_passengers())
                last_event = event
            alighting_event = ean.get_event_by_event_id(shortest_path[-2])
            alighting_event.add_passengers(od_pair.get_total_passengers())
        return length_fix_passengers

    def compute_potentially_used_events_and_activities(self, ean: "ean_data.Ean", od_pair: "od_data.ODPair") -> list:
        self.unused_events = set()
        self.unused_activities = set()
        # compute shortest s-t-path with upper_bounds
        start_event_id = ean.get_event_od(od_pair.get_origin(), od_pair.get_destination(), 'source', 1,
                                          1).get_event_id()
        end_event_id = ean.get_event_od(od_pair.get_origin(), od_pair.get_destination(), 'target', 1,
                                        None).get_event_id()
        self.length_sp_upper_bound = nx.dijkstra_path_length(self.ean_graph, start_event_id, end_event_id,
                                                        weight='upper_bound')

        # compute shortest s-i /i-t paths for all nodes
        source_to_intermediate = nx.single_source_dijkstra_path_length(self.ean_graph, start_event_id,
                                                                       weight='lower_bound')
        intermediate_to_target = nx.single_source_dijkstra_path_length(self.reverse_graph, end_event_id,
                                                                       weight='lower_bound')
        # Compute unused events
        for event in ean.get_events_network():
            event_id = event.get_event_id()
            if not nx.has_path(self.ean_graph, start_event_id, event_id) or not nx.has_path(self.reverse_graph,
                                                                                            end_event_id, event_id):
                self.unused_events.add(event)
                continue
            if source_to_intermediate[event_id] + intermediate_to_target[event_id] > self.length_sp_upper_bound:
                self.unused_events.add(event)

        # Compute unused activities
        for activity in self.activities_drive_wait_trans:
            if activity in EanPreprocessor.unused_trans_activities:
                self.unused_activities.add(activity)
                continue
            left_event_id = activity.get_left_event().get_event_id()
            right_event_id = activity.get_right_event().get_event_id()
            if not nx.has_path(self.ean_graph, start_event_id, left_event_id) or not nx.has_path(self.reverse_graph,
                                                                                                 end_event_id,
                                                                                                 right_event_id):
                self.unused_activities.add(activity)
                continue
            if source_to_intermediate[left_event_id] + self.ean_graph[left_event_id][right_event_id]['lower_bound'] \
                    + intermediate_to_target[right_event_id] > self.length_sp_upper_bound:
                self.unused_activities.add(activity)

        used_activities = set(ean.get_activities(['drive', 'wait', 'trans'])) - self.unused_activities
        used_events = set(ean.get_all_events()) - self.unused_events
        return [used_events, used_activities]


class PotentialTransferStationIP:
    def __init__(self, ptn: Ptn, line_pool: LinePool, parameters: SolverParameters) -> None:
        self._parameters = parameters
        self._ptn = ptn
        self._line_pool = line_pool
        self._m = None  # type: Union[None, Model]
        self._use_node = {}  # type: Dict[Stop, Variable]
        self._is_feasible = False

    def create_model(self) -> None:
        logger.debug("Initialize model")
        self._m = self._parameters.initialize_generic_model()
        self._m.setDoubleParam(DoubleParam.MIP_GAP, 0)
        self._m.setSense(OptimizationSense.MINIMIZE)
        self._create_variables()
        self._create_constraints()

    def _create_variables(self) -> None:
        logger.debug("Create variables")
        for node in self._ptn.get_nodes():
            self._use_node[node] = self._m.addVariable(var_type=VariableType.BINARY, objective=1,
                                                       name=f'x_{node.getId()}')

    def _create_constraints(self) -> None:
        lhs = self._m.createExpression()
        for line_1 in self._line_pool.get_lines():
            for line_2 in self._line_pool.get_lines():
                if line_1.get_directed_line_id() == line_2.get_undirected_line_id() \
                    or line_1.get_undirected_line_id() == line_2.get_directed_line_id():
                    continue
                nodes_1 = line_1.get_nodes()
                nodes_2 = line_2.get_nodes()
                common_nodes_list = PotentialTransferStationIP.get_common_sublists(nodes_1, nodes_2)
                for common_nodes in common_nodes_list:
                    lhs.clear()
                    for node in common_nodes:
                        lhs.multiAdd(1, self._use_node[node])
                    self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, 1,
                                          name=f"intersection_{line_1.get_directed_line_id()}"
                                               f"_{line_2.get_directed_line_id()}")

    def solve(self) -> None:
        logger.debug("Start optimization")
        self._m.solve()
        self._is_feasible = self._m.getStatus() == Status.FEASIBLE or self._m.getStatus() == Status.OPTIMAL
        if not self._is_feasible:
            logger.debug("No feasible solution found")
            if self._parameters.show_solver_output:
                self._m.computeIIS("TransferStations.ilp")
        else:
            logger.debug("Feasible solution found")
        logger.debug("End optimization")
        logger.debug(f"Total number of stations: {len(self._ptn.get_nodes())}")
        logger.debug(f"Number of necessary transfer stations: "
                     f"{int(self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL))}")

    def get_solution(self) -> List[Stop]:
        transfer_stations = []  # type: List[Stop]
        for node in self._ptn.get_nodes():
            if round(self._m.getValue(self._use_node[node])) == 1:
                transfer_stations.append(node)
        return transfer_stations

    # helper function
    @staticmethod
    def get_common_sublists(list_1, list_2):
        common_sublists = []
        reverse = set()
        len_list_1 = list_1.__len__()
        len_list_2 = list_2.__len__()

        # Check for antiparallel stretches
        next_index_to_check = -1

        for index_1 in range(0, len_list_1):
            if index_1 < next_index_to_check:
                continue
            for index_2 in range(len_list_2 - 1, -1, -1):
                if list_1[index_1] is list_2[index_2]:
                    if index_1 == 0:
                        line_1_begins = True
                    else:
                        line_1_begins = False
                    if index_2 == len_list_2 - 1:
                        line_2_ends = True
                    else:
                        line_2_ends = False
                    first_reverse_element = list_1[index_1]
                    reverse_list = []
                    next_index_to_check = index_1 + min(len_list_1 - index_1, index_2 + 1)
                    for index_3 in range(0, min(len_list_1 - index_1, index_2 + 1)):
                        if not list_1[index_1 + index_3] is list_2[index_2 - index_3]:
                            next_index_to_check = index_1 + index_3
                            break
                        else:
                            reverse_list.append(list_1[index_1 + index_3])
                    if reverse_list.__len__() > 1:
                        if not (line_1_begins or line_2_ends):
                            common_sublists.append([first_reverse_element])
                        for element in reverse_list:
                            reverse.add(element)
                    break

        # Check for parallel stretches
        next_index_to_check = -1

        for index_1 in range(0, len_list_1):
            sublist = []
            if list_1[index_1] in reverse:
                continue
            if index_1 < next_index_to_check:
                continue
            for index_2 in range(0, len_list_2):
                if list_1[index_1] is list_2[index_2]:
                    line_1_begins = index_1 == 0
                    line_2_begins = index_2 == 0
                    next_index_to_check = index_1 + min(len_list_1 - index_1, len_list_2 - index_2)
                    for index_3 in range(0, min(len_list_1 - index_1, len_list_2 - index_2)):
                        if not list_1[index_1 + index_3] is list_2[index_2 + index_3]:
                            next_index_to_check = index_1 + index_3
                            break
                        else:
                            sublist.append(list_1[index_1 + index_3])
                    line_1_ends = next_index_to_check == len_list_1
                    line_2_ends = next_index_to_check == index_1 + len_list_2 - index_2
                    if not ((line_1_begins and line_2_begins)
                            or (line_1_ends and line_2_ends)
                            or (line_1_begins and line_1_ends)
                            or (line_2_begins and line_2_ends)):
                        common_sublists.append(sublist)
                    break
        return common_sublists