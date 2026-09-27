from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.exceptions.exceptions import LinTimException
from core.solver.generic_solver_interface import Model, OptimizationSense, VariableType, IntAttribute, Variable, \
    LinearExpression, ConstraintSense, Constraint, Status, DoubleAttribute
from ean_data import *
from lin_tim_pass_helper import LinTimPassParameters
from preprocessing import *
from write_csv import write_line_concept, write_periodic_events, write_periodic_timetable, write_periodic_activities, write_solver_statistic

logger = logging.getLogger(__name__)


class LinTimPassGenericModel:

    def __init__(self, ptn: Ptn, line_pool: LinePool, od: OD, ean: Ean, parameters: LinTimPassParameters):
        # Given data
        self._ptn = ptn
        self._line_pool = line_pool
        self._od = od
        self._ean = ean
        self._parameters = parameters

        # Instance data
        self._ptn_drive_weights = {}  # type: Dict[Link, float]
        self._ptn_wait_weights = {}  # type: Dict[Stop, float]
        self._ptn_transfer_weights = {}  # type: Dict[Link, Dict[Link, float]]
        self._activities_drive_wait_trans = []  # type: List[EanActivity]
        self._activities_drive = []  # type: List[EanActivity]
        self._activities_wait = []  # type: List[EanActivity]
        self._activities_trans = []  # type: List[EanActivity]
        self._activities_trans_wait = []  # type: List[EanActivity]
        self._activities_drive_wait_trans_sync = []  # type: List[EanActivity]
        self._activities_time_to = []  # type: List[EanActivity]
        self._activities_from = []  # type: List[EanActivity]
        self._activities_no_sync = []  # type: List[EanActivity]
        self._active_od_pairs = []  # type: List[ODPair]
        self._preprocessor = None  # type: Union[None, PtnPreprocessor]

        # Model
        self._m = None  # type: Union[None, Model]

        # Model data
        self._m_1 = 0
        self._m_2 = 0
        self._m_4 = 0
        self._frequencies = {}  # type: Dict[Line, Variable]
        self._lines_established = {}  # type: Dict[EanActivity, Variable]
        self._pi = {}  # type: Dict[EanEvent, Variable]
        self._modulo_parameter = {}  # type: Dict[EanActivity, Variable]
        self._arc_used = {}  # type: Dict[EanActivity, Dict[ODPair, Dict[int, Variable]]]
        self._travel_time_linear = {}  # type: Dict[EanActivity, Dict[ODPair, Dict[int, Variable]]]
        self._time_est_drive = {}  # type: Dict[Link, Variable]
        self._time_est_wait = {}  # type: Dict[Stop, Variable]
        self._time_est_transfer = {}  # type: Dict[Link, Dict[Link, Variable]]
        self.is_feasible = False

        # Constraint lists for writing the lp model
        self._timetabling_constraints = []  # type: List[Constraint]
        self._timetabling_established_lines_constraints = []  # type: List[Constraint]
        self._undirected_lines_constraints = []  # type: List[Constraint]
        self._system_frequency_constraints = []  # type: List[Constraint]
        self._upper_lower_frequency_constraints = []  # type: List[Constraint]
        self._established_lines_const = []  # type: List[Constraint]
        self._passenger_flow_constraints = []  # type: List[Constraint]
        self._time_slice_constraints = []  # type: List[Constraint]
        self._linearization_travel_time_constraints = []  # type: List[Constraint]
        self._use_fixed_passenger_paths_constraints = []  # type: List[Constraint]

    def create_model(self):
        self._preprocess_ptn()
        self._initialize_big_m_constraints()
        self._initialize_loop_lists()
        self._m = self._parameters.initialize_generic_model()
        self._create_variables()
        self._create_objective_function()
        self._add_constraints()
        if self._parameters.write_lp_output:
            self._write_lp_output()

    def _preprocess_ptn(self):
        logger.debug("Compute fixed passenger paths...")
        self._preprocessor = PtnPreprocessor(self._ptn, self._line_pool, self._ean, self._parameters, self._parameters,
                                             self._parameters)
        if self._parameters.add_fix_passenger_paths:
            self._ptn_drive_weights, self._ptn_wait_weights, self._ptn_transfer_weights = \
                self._preprocessor.compute_weights_unrouted_passengers(self._ptn, self._od)
        logger.debug("done!")

    def _initialize_big_m_constraints(self):
        max_upper_bound = self._ean.compute_max_upper_bound()
        self._m_1 = self._parameters.period_length
        self._m_2 = self._parameters.period_length
        self._m_4 = max_upper_bound
        logger.debug(f"m_1={self._m_1}\nm_2={self._m_2}\nm_4={self._m_4}\n")

    def _initialize_loop_lists(self):
        self._activities_drive_wait_trans = self._ean.get_activities(['drive', 'wait', 'trans'])
        self._activities_drive = self._ean.get_activities(['drive'])
        self._activities_wait = self._ean.get_activities(['wait'])
        self._activities_trans = self._ean.get_activities(['trans'])
        self._activities_trans_wait = self._ean.get_activities(['wait', 'trans'])
        self._activities_drive_wait_trans_sync = self._ean.get_activities(['drive', 'wait', 'trans', 'sync'])
        self._activities_time_to = self._ean.get_activities(['time', 'to'])
        self._activities_from = self._ean.get_activities(['from'])
        self._activities_no_sync = self._ean.get_activities(['drive', 'wait', 'trans', 'time', 'to', 'from'])
        self._active_od_pairs = self._od.get_active_od_pairs()
        logger.debug(f"Number of drive, wait, transfer activities: {len(self._activities_drive_wait_trans)}")

    def _create_variables(self):
        logger.debug("Initialize variables")
        self._add_line_planning_variables()
        self._add_timetabling_variables()
        self._add_arc_used_variables()
        self._add_linearized_travel_time_variables()
        if self._parameters.add_fix_passenger_paths:
            self._add_fixed_passenger_variables()
        logger.debug(f"Number of variables: {self._m.getIntAttribute(IntAttribute.NUM_VARIABLES)}")

    def _add_line_planning_variables(self):
        logger.debug("\tfrequencies")
        for line in self._line_pool.get_lines():
            self._frequencies[line] = self._m.addVariable(var_type=VariableType.BINARY, name=f'f_{line}')
        logger.debug("\tlines established")
        for activity in self._activities_drive_wait_trans_sync:
            self._lines_established[activity] = self._m.addVariable(var_type=VariableType.BINARY,
                                                                    name=f'y_{activity.get_activity_id()}')

    def _add_timetabling_variables(self):
        logger.debug("\tpi")
        for event in self._ean.get_events_network():
            self._pi[event] = self._m.addVariable(0, self._parameters.period_length - 1, var_type=VariableType.INTEGER,
                                                  name=f'pi_{event.get_event_id()}')
        logger.debug("\tmodulo parameter")
        for activity in self._activities_drive_wait_trans_sync:
            self._modulo_parameter[activity] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                                   name=f'z_{activity.get_activity_id()}')

    def _add_arc_used_variables(self):
        logger.debug("\tarc used")
        for activity in self._activities_no_sync:
            self._arc_used[activity] = {}
            for od_pair in self._active_od_pairs:
                self._arc_used[activity][od_pair] = {}
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    self._arc_used[activity][od_pair][t] = self._m.addVariable(var_type=VariableType.BINARY,
                                                                               name=f'p_{activity.get_activity_id()}_{od_pair}_{t}')

    def _add_linearized_travel_time_variables(self):
        logger.debug("\ttravel time linear")
        for activity in self._activities_drive_wait_trans:
            self._travel_time_linear[activity] = {}
            for od_pair in self._active_od_pairs:
                self._travel_time_linear[activity][od_pair] = {}
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    self._travel_time_linear[activity][od_pair][t] = \
                        self._m.addVariable(var_type=VariableType.INTEGER,
                                            name=f'd_{activity.get_activity_id()}_{od_pair}_{t}')

    def _add_fixed_passenger_variables(self):
        logger.debug("\ttime estimation drive")
        for edge in self._ptn_drive_weights.keys():
            self._time_est_drive[edge] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                             name=f'time_est_drive_{edge.getId()}')
        logger.debug("\ttime estimation wait")
        for node in self._ptn_wait_weights.keys():
            self._time_est_wait[node] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                            name=f'time_est_wait_{node.getId()}')
        logger.debug("\ttime estimation transfer stations")
        for edge_in in self._ptn_transfer_weights.keys():
            self._time_est_transfer[edge_in] = {}
            for edge_out in self._ptn_transfer_weights[edge_in].keys():
                self._time_est_transfer[edge_in][edge_out] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                                                 name=f'time_est_transfer_station_'
                                                                                      f'{edge_in.getId()}_'
                                                                                      f'{edge_out.getId()}')

    def _create_objective_function(self):
        logger.debug("Initialize objective function")
        sum_line_length = self._m.createExpression()
        sum_line_cost = self._m.createExpression()
        sum_travel_time = self._m.createExpression()
        sum_drive_time = self._m.createExpression()
        sum_wait_time = self._m.createExpression()
        sum_transfer_time = self._m.createExpression()
        sum_penalty_changing_time_slices = self._m.createExpression()
        sum_transfers = self._m.createExpression()

        for line in self._line_pool.get_lines():
            sum_line_length.multiAdd(line.compute_length_from_ptn(), self._frequencies[line])
            sum_line_cost.multiAdd(line.get_cost(), self._frequencies[line])
        for od_pair in self._active_od_pairs:
            for t in range(1, od_pair.get_n_time_slices() + 1):
                for activity in self._activities_drive_wait_trans:
                    sum_travel_time.multiAdd(od_pair.get_n_passengers(t), self._travel_time_linear[activity][od_pair][t])
                    if activity.get_activity_type() == 'trans':
                        sum_transfers.multiAdd(od_pair.get_n_passengers(t), self._arc_used[activity][od_pair][t])
                        sum_transfer_time.multiAdd(od_pair.get_n_passengers(t),
                                                  self._travel_time_linear[activity][od_pair][t])
                    if activity.get_activity_type() == 'wait':
                        sum_wait_time.multiAdd(od_pair.get_n_passengers(t),
                                              self._travel_time_linear[activity][od_pair][t])
                    if activity.get_activity_type() == 'drive':
                        sum_drive_time.multiAdd(od_pair.get_n_passengers(t),
                                               self._travel_time_linear[activity][od_pair][t])
                for activity in self._ean.get_activities(['time']):
                    target_event = activity.get_right_event()
                    if not isinstance(target_event, ean_data.EanEventOD):
                        raise LinTimException("Expected OD event, but got none!")
                    time_2 = target_event.get_time_2()
                    factor = od_pair.get_penalty(t, time_2) * self._parameters.period_length * \
                             od_pair.get_n_passengers(t)
                    sum_penalty_changing_time_slices.multiAdd(factor, self._arc_used[activity][od_pair][t])

        # add passengers on fixed paths
        if self._parameters.add_fix_passenger_paths:
            for edge, passengers in self._ptn_drive_weights.items():
                sum_travel_time.multiAdd(passengers, self._time_est_drive[edge])
                sum_drive_time.multiAdd(passengers, self._time_est_drive[edge])
            for stop, passengers in self._ptn_wait_weights.items():
                sum_travel_time.multiAdd(passengers, self._time_est_wait[stop])
                sum_wait_time.multiAdd(passengers, self._time_est_wait[stop])
            for edge_in in self._ptn_transfer_weights.keys():
                for edge_out, passengers in self._ptn_transfer_weights[edge_in].items():
                    sum_transfers.addConstant(passengers)
                    sum_travel_time.multiAdd(passengers, self._time_est_transfer[edge_in][edge_out])
                    sum_transfer_time.multiAdd(passengers, self._time_est_transfer[edge_in][edge_out])

        objective = self._m.createExpression()
        objective.multiAdd(self._parameters.factor_line_length, sum_line_length)
        objective.multiAdd(self._parameters.factor_line_cost, sum_line_cost)
        objective.multiAdd(self._parameters.factor_travel_time, sum_travel_time)
        objective.multiAdd(self._parameters.factor_drive_time, sum_drive_time)
        objective.multiAdd(self._parameters.factor_transfer_time, sum_transfer_time)
        objective.multiAdd(self._parameters.factor_wait_time, sum_wait_time)
        objective.multiAdd(self._parameters.factor_penalty_time_slice, sum_penalty_changing_time_slices)
        objective.multiAdd(self._parameters.transfer_penalty, sum_transfers)

        self._m.setObjective(objective, OptimizationSense.MINIMIZE)

    def get_duration(self, activity: EanActivity) -> LinearExpression:
        dur = self._m.createExpression()
        dur.multiAdd(1, self._pi[activity.get_right_event()])
        dur.multiAdd(-1, self._pi[activity.get_left_event()])
        dur.multiAdd(self._parameters.period_length, self._modulo_parameter[activity])
        return dur

    def _add_constraints(self):
        logger.debug("Add constraints:")
        self._add_timetabling_constraints()
        if not self._parameters.directed:
            self._add_undirected_line_constraints()
        self._add_system_frequency_constraints()
        if self._parameters.check_lower_frequencies or self._parameters.check_upper_frequencies:
            self._add_lower_and_upper_frequency_constraints()
        self._add_established_lines_constraints()
        self._add_passenger_flow_constraints()
        self._add_time_slice_constraints()
        self._add_linearized_travel_time_constraints()
        if self._parameters.add_fix_passenger_paths:
            self._add_fixed_passenger_path_constraints()

    def _add_timetabling_constraints(self):
        logger.debug("\ttimetabling")
        lhs = self._m.createExpression()
        for activity in self._activities_drive_wait_trans_sync:
            dur = self.get_duration(activity)
            lhs.clear()
            lhs.add(dur)
            lhs.multiAdd(-activity.get_lower_bound(), self._lines_established[activity])
            const_1 = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, 0,
                                           f"timetabling_1_{activity.get_activity_id()}")
            lhs.clear()
            lhs.add(dur)
            lhs.multiAdd(self._m_1, self._lines_established[activity])
            const_2 = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, activity.get_upper_bound() + self._m_1,
                                           f"timetabling_2_{activity.get_activity_id()}")
            source_event = activity.get_left_event()
            target_event = activity.get_right_event()
            if not isinstance(source_event, EanEventNetwork) or not isinstance(target_event, EanEventNetwork):
                raise LinTimException("Illegal state, unknown event type")

            lhs.clear()
            lhs.multiAdd(1, self._lines_established[activity])
            lhs.multiAdd(-1, self._frequencies[source_event.get_line()])
            const_3 = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, 0,
                                           f"timetabling_est_lines_1_{activity.get_activity_id()}")
            lhs.clear()
            lhs.multiAdd(1, self._lines_established[activity])
            lhs.multiAdd(-1, self._frequencies[target_event.get_line()])
            const_4 = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, 0,
                                       f"timetabling_est_lines_2_{activity.get_activity_id()}")
            lhs.clear()
            lhs.multiAdd(1, self._lines_established[activity])
            lhs.multiAdd(-1, self._frequencies[source_event.get_line()])
            lhs.multiAdd(-1, self._frequencies[target_event.get_line()])
            const_5 = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, -1,
                                           f"timetabling_est_lines_3_{activity.get_activity_id()}")
            if self._parameters.write_lp_output:
                self._timetabling_constraints.append(const_1)
                self._timetabling_constraints.append(const_2)
                self._timetabling_established_lines_constraints.append(const_3)
                self._timetabling_established_lines_constraints.append(const_4)
                self._timetabling_established_lines_constraints.append(const_5)

    def _add_undirected_line_constraints(self):
        # In undirected case forward and backward direction of a line have to be used together
        logger.debug("\tundirected lines")
        lhs = self._m.createExpression()
        for line in self._line_pool.get_lines():
            if line.get_directed_line_id() < 0:
                backwards_line = self._line_pool.get_line_by_directed_id_and_repetition(line.get_undirected_line_id(),
                                                                                        line.get_repetition())
                lhs.clear()
                lhs.multiAdd(1, self._frequencies[line])
                lhs.multiAdd(-1, self._frequencies[backwards_line])
                const_1 = self._m.addConstraint(lhs, ConstraintSense.EQUAL, 0,
                                               f"undirected_lines_{line.get_undirected_line_id()}")
                if self._parameters.write_lp_output:
                    self._undirected_lines_constraints.append(const_1)

    def _add_system_frequency_constraints(self):
        # Lines have to be used with frequency 0, 1 or the system frequency.
        logger.debug("\tLine frequency")
        lhs = self._m.createExpression()
        for line_id in range(1, self._line_pool.get_max_id() + 1):
            lines = self._line_pool.get_lines_by_directed_id(line_id)
            for repetition in range(1, lines[1].get_frequency()):
                lhs.clear()
                lhs.multiAdd(1, self._frequencies[lines[repetition]])
                lhs.multiAdd(-1, self._frequencies[lines[repetition + 1]])
                # if only frequency 1 is used, it has to be the line with repetition 1
                if repetition == 1 and not self._parameters.use_system_frequency:
                    const = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, 0,
                                                 f"system_frequency_{line_id}_{repetition}")
                # if the system frequency is used, all repetitions have to be used
                else:
                    const = self._m.addConstr(lhs, ConstraintSense.EQUAL, 0,
                                             f"system_frequency_{line_id}_{repetition}")
                if self._parameters.write_lp_output:
                    self._system_frequency_constraints.append(const)

    def _add_lower_and_upper_frequency_constraints(self):
        # Line frequencies have to be according to minimal/maximal frequencies for each edge
        logger.debug('\tlower and upper frequencies')
        lhs = self._m.createExpression()
        lines_by_edges = {}
        for edge in self._ptn.get_edges():
            lines_by_edges[edge] = []
        for line in self._line_pool.get_lines():
            for edge in line.get_edges():
                lines_by_edges[edge].append(line)
        for edge in self._ptn.get_edges():
            if not lines_by_edges[edge]:
                if self._parameters.check_lower_frequencies and edge.getLowerFrequencyBound() > 0:
                    logger.debug(f"Edge {edge.getId()} is not covered by any line, lower frequency bound "
                                  f"{edge.getLowerFrequencyBound()}!")
                    logger.debug("No feasible solution was found!")
                    return
                continue
            lhs.clear()
            for line in lines_by_edges[edge]:
                lhs.multiAdd(1, self._frequencies[line])
            if self._parameters.check_lower_frequencies:
                const_1 = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL,
                                                edge.getLowerFrequencyBound(), f"lower_freq_{edge.getId()}")
            if self._parameters.check_upper_frequencies:
                const_2 = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL,
                                                edge.getUpperFrequencyBound(), f"upper_freq_{edge.getId()}")
            if self._parameters.write_lp_output:
                if self._parameters.check_lower_frequencies:
                    self._upper_lower_frequency_constraints.append(const_1)
                if self._parameters.check_upper_frequencies:
                    self._upper_lower_frequency_constraints.append(const_2)

    def _add_established_lines_constraints(self):
        # Only arcs belonging to established lines can be used
        logger.debug("\testablished lines")
        lhs = self._m.createExpression()
        counter = 1
        nb_acts = len(self._activities_no_sync)
        for activity in self._activities_no_sync:
            logger.debug(f"\t\tactivity: {counter} of {nb_acts}")
            counter += 1
            i = activity.get_left_event()
            j = activity.get_right_event()
            lines = set()
            if isinstance(i, EanEventNetwork):
                lines.add(i.get_line())
            if isinstance(j, EanEventNetwork):
                lines.add((j.get_line()))
            for line in lines:
                for od_pair in self._od.get_active_od_pairs():
                    for t in range(1, od_pair.get_n_time_slices() + 1):
                        lhs.clear()
                        lhs.multiAdd(1, self._arc_used[activity][od_pair][t])
                        lhs.multiAdd(-1, self._frequencies[line])
                        const_1 = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, 0,
                                                       f"established_lines_{activity.get_activity_id()}_{line.get_directed_line_id()}_{od_pair}_{t}")
                        if self._parameters.write_lp_output:
                            self._established_lines_const.append(const_1)

    def _add_passenger_flow_constraints(self):
        logger.debug("\tpassenger flow")
        lhs = self._m.createExpression()
        counter = 1
        for od_pair in self._active_od_pairs:
            logger.debug(f"\t\tOD-pair: {counter}")
            counter += 1

            if self._parameters.use_preprocessing:
                used_tuple = self._preprocessor.compute_potentially_used_events_and_activities(self._ean, self._ptn,
                                                                                               od_pair)
                used_events = used_tuple[0]
                used_activities_drive_wait_trans = used_tuple[1]
            else:
                used_events = self._ean.get_all_events()
                used_activities_drive_wait_trans = self._activities_drive_wait_trans

            for t in range(1, od_pair.get_n_time_slices() + 1):
                # for event in ean.get_all_events():
                for event in used_events:
                    lhs.clear()
                    rhs = 0
                    empty = True
                    activities_to_check = []
                    # for activity in activities_drive_wait_trans:
                    for activity in used_activities_drive_wait_trans:
                        activities_to_check.append(activity)
                    for activity in self._activities_time_to:
                        event_to_check_od = activity.get_left_event()
                        event_to_check_used = activity.get_right_event()
                        if not self._parameters.use_preprocessing or event_to_check_used in used_events:
                            if not isinstance(event_to_check_od, ean_data.EanEventOD):
                                raise LinTimException("Expected OD event, got none!")
                            if event_to_check_od.check_attributes_od(od_pair.get_origin(), od_pair.get_destination(),
                                                                     t):
                                activities_to_check.append(activity)
                    for activity in self._activities_from:
                        event_to_check_od = activity.get_right_event()
                        event_to_check_used = activity.get_left_event()
                        if not self._parameters.use_preprocessing or event_to_check_used in used_events:
                            if not isinstance(event_to_check_od, ean_data.EanEventOD):
                                raise LinTimException("Expected OD event, got none!")
                            if event_to_check_od.check_attributes_od(od_pair.get_origin(), od_pair.get_destination(),
                                                                     t):
                                activities_to_check.append(activity)
                    for activity in activities_to_check:
                        if activity.get_left_event() == event:
                            lhs.multiAdd(1, self._arc_used[activity][od_pair][t])
                            empty = False
                        if activity.get_right_event() == event:
                            lhs.multiAdd(-1, self._arc_used[activity][od_pair][t])
                            empty = False
                    if isinstance(event, EanEventOD):
                        if event.check_attributes(od_pair.get_origin(), od_pair.get_destination(), 'source', t, t):
                            rhs = 1
                        elif event.check_attributes(od_pair.get_origin(), od_pair.get_destination(), 'target', t, None):
                            rhs = -1
                    if not empty:
                        const_1 = self._m.addConstraint(lhs, ConstraintSense.EQUAL, rhs,
                                                       f"pass_routing_{od_pair}_{t}_{event.get_event_id()}")
                        if self._parameters.write_lp_output:
                            self._passenger_flow_constraints.append(const_1)

    def _add_time_slice_constraints(self):
        # First network-event in path in right time-slice
        logger.debug("\ttime slice")
        lhs = self._m.createExpression()
        for activity in self._ean.get_activities(['to']):
            left_event = activity.get_left_event()
            right_event = activity.get_right_event()
            if not isinstance(left_event, ean_data.EanEventOD):
                raise LinTimException("Expected OD event, but got none!")
            origin = left_event.get_start()
            destination = left_event.get_end()
            time_1 = left_event.get_time_1()
            time_2 = left_event.get_time_2()
            od_pair = self._od.get_od_pair(origin, destination)
            if od_pair.is_active():
                lhs.multiAdd(1, self._pi[right_event])
                lhs.multiAdd((1 - time_2) * (self._parameters.period_length / od_pair.get_n_time_slices()),
                            self._arc_used[activity][od_pair][time_1])
                const_1 = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, 0,
                                               f"timeslice_lb_{activity.get_activity_id()}")
                lhs.clear()
                lhs.multiAdd(1, self._pi[right_event])
                lhs.multiAdd(self._m_2, self._arc_used[activity][od_pair][time_1])
                const_2 = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, self._m_2 +
                                                (time_2 * (self._parameters.period_length /
                                                           od_pair.get_n_time_slices()) - 1),
                                                f"timeslice_ub_{activity.get_activity_id()}")
                if self._parameters.write_lp_output:
                    self._time_slice_constraints.append(const_1)
                    self._time_slice_constraints.append(const_2)

    def _add_linearized_travel_time_constraints(self):
        logger.debug("\tlinearization travel time")
        lhs = self._m.createExpression()
        for activity in self._activities_drive_wait_trans:
            for od_pair in self._od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if activity.get_lower_bound() != activity.get_upper_bound():
                        lhs.clear()
                        lhs.multiAdd(1, self._travel_time_linear[activity][od_pair][t])
                        const_1 = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, 0,
                                                   f"linearization_1_{od_pair}_{t}_{activity}")
                        lhs.multiAdd(-1, self.get_duration(activity))
                        lhs.multiAdd(-self._m_4, self._arc_used[activity][od_pair][t])
                        const_2 = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, -self._m_4,
                                                       f"linearization_2_{od_pair}_{t}_{activity}")
                        if self._parameters.write_lp_output:
                            self._linearization_travel_time_constraints.append(const_1)
                            self._linearization_travel_time_constraints.append(const_2)
                    else:
                        lhs.clear()
                        lhs.multiAdd(1, self._travel_time_linear[activity][od_pair][t])
                        lhs.multiAdd(-activity.get_lower_bound(), self._arc_used[activity][od_pair][t])
                        const = self._m.addConstraint(lhs, ConstraintSense.EQUAL, 0,
                                                      f"linearization_3_{od_pair}_{t}_{activity}")
                        if self._parameters.write_lp_output:
                            self._linearization_travel_time_constraints.append(const)

    def _add_fixed_passenger_path_constraints(self):
        logger.debug("\tfixed passenger paths")
        lhs = self._m.createExpression()
        for edge, passengers in self._ptn_drive_weights.items():
            for activity in self._activities_drive:
                if activity.belongs_to_edge_drive(edge):
                    lhs.clear()
                    lhs.add(self.get_duration(activity))
                    lhs.multiAdd(-1, self._time_est_drive[edge])
                    lhs.multiAdd(self._m_4, self._lines_established[activity])
                    const = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, self._m_4,
                                                 f"fixed_pass_paths_drive_{edge.getId()}_{activity}")
                    if self._parameters.write_lp_output:
                        self._use_fixed_passenger_paths_constraints.append(const)
        for node, passengers in self._ptn_wait_weights.items():
            for activity in self._activities_wait:
                if activity.belongs_to_node_wait(node):
                    lhs.clear()
                    lhs.add(self.get_duration(activity))
                    lhs.multiAdd(-1, self._time_est_wait[node])
                    lhs.multiAdd(self._m_4, self._lines_established[activity])
                    const = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, self._m_4,
                                                 f"fixed_pass_paths_wait_{node.getId()}_{activity}")
                    if self._parameters.write_lp_output:
                        self._use_fixed_passenger_paths_constraints.append(const)
        for edge_in in self._ptn_transfer_weights.keys():
            for edge_out, passengers in self._ptn_transfer_weights[edge_in].items():
                for activity in self._activities_trans_wait:
                    if activity.belongs_to_transfer_node(edge_in, edge_out):
                        lhs.clear()
                        lhs.add(self.get_duration(activity))
                        lhs.multiAdd(-1, self._time_est_transfer[edge_in][edge_out])
                        lhs.multiAdd(self._m_4, self._lines_established[activity])
                        const = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, self._m_4,
                                                     f"fixed_pass_paths_transfer_{edge_in.getId()}_{edge_out.getId()}_"
                                                     f"{activity}")
                        if self._parameters.write_lp_output:
                            self._use_fixed_passenger_paths_constraints.append(const)

    def _write_lp_output(self):
        self._m.write("LinTimPass.lp")
        dec_file = open('LinTimPass.dec', 'w')
        dec_file.write("PRESOLVED\n0\n")
        dec_file.write("NBLOCKS\n4\n")
        # Block 1: Line planning
        dec_file.write("BLOCK 1\n")
        for const in self._undirected_lines_constraints:
            dec_file.write(const.getName() + "\n")
        for const in self._system_frequency_constraints:
            dec_file.write(const.getName() + "\n")
        for const in self._upper_lower_frequency_constraints:
            dec_file.write(const.getName() + "\n")
        # Block 2: Timetabling
        dec_file.write("BLOCK 2\n")
        for const in self._timetabling_constraints:
            dec_file.write(const.getName() + "\n")
        for const in self._use_fixed_passenger_paths_constraints:
            dec_file.write(const.getName() + "\n")
        # Block 3: Passenger flow
        dec_file.write("BLOCK 3\n")
        for const in self._passenger_flow_constraints:
            dec_file.write(const.getName() + "\n")
        # Master-constraints: Coupling
        dec_file.write("MASTERCONSS\n")
        # Line planning + timetabling
        for const in self._timetabling_established_lines_constraints:
            dec_file.write(const.getName() + "\n")
        # Line planing + routing
        for const in self._established_lines_const:
            dec_file.write(const.getName() + "\n")
        # Timetabling + routing
        for const in self._time_slice_constraints:
            dec_file.write(const.getName() + "\n")
        for const in self._linearization_travel_time_constraints:
            dec_file.write(const.getName() + "\n")
        dec_file.close()

    def solve(self):
        logger.debug("Start optimization")
        self._m.solve()
        self.is_feasible = self._m.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0
        if not self.is_feasible:
            logger.debug("No feasible solution found")
            if self._m.getStatus() == Status.INFEASIBLE:
                self._m.computeIIS("LinTimPass.ilp")
            raise AlgorithmStoppingCriterionException("Lin Tim Pass")
        if self._m.getStatus() == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        logger.debug("End optimization")

    def write_output(self):
        frequencies_solution = {line: int(round(self._m.getValue(self._frequencies[line])))
                                for line in self._frequencies.keys()}
        arc_used_solution = {activity:
                                 {od_pair:
                                      {t: int(round(self._m.getValue(self._arc_used[activity][od_pair][t])))
                                       for t in self._arc_used[activity][od_pair].keys()}
                                  for od_pair in self._arc_used[activity].keys()}
                             for activity in self._arc_used.keys()}
        pi_solution = {event: int(round(self._m.getValue(self._pi[event]))) for event in self._pi.keys()}
        lines_established_solution = {activity: int(round(self._m.getValue(self._lines_established[activity])))
                                      for activity in self._lines_established.keys()}
        write_solver_statistic(self._parameters, self._m.getDoubleAttribute(DoubleAttribute.RUNTIME),
                               self._m.getDoubleAttribute(DoubleAttribute.MIP_GAP),
                               self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL))
        write_line_concept(self._parameters, self._line_pool, frequencies_solution)
        write_periodic_events(self._parameters, self._ean, self._od, arc_used_solution, frequencies_solution)
        write_periodic_timetable(self._parameters, self._ean, pi_solution, frequencies_solution)
        write_periodic_activities(self._parameters, self._ean, self._od, arc_used_solution, lines_established_solution)
        self._write_objective()

    def _write_objective(self):
        # Output objective
        logger.debug("Computing all parts of the objective")
        sum_drive_time = 0
        sum_line_length = 0
        sum_line_cost = 0
        sum_travel_time = 0
        sum_wait_time = 0
        sum_transfer_time = 0
        sum_penalty_changing_time_slices = 0
        sum_transfers = 0

        for line in self._line_pool.get_lines():
            sum_line_length += line.compute_length_from_ptn() * round(self._m.getValue(self._frequencies[line]))
            sum_line_cost += line.get_cost() * round(self._m.getValue(self._frequencies[line]))
        for od_pair in self._active_od_pairs:
            for t in range(1, od_pair.get_n_time_slices() + 1):
                sum_travel_time_od_pair = 0
                for activity in self._activities_drive_wait_trans:
                    sum_travel_time += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) * \
                                       od_pair.get_n_passengers(t)
                    sum_travel_time_od_pair += \
                        round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) * \
                        od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'trans':
                        sum_transfers += round(self._m.getValue(self._arc_used[activity][od_pair][t])) * \
                                         od_pair.get_n_passengers(t)
                        sum_transfer_time += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) * \
                                             od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'wait':
                        sum_wait_time += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) * \
                                         od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'drive':
                        sum_drive_time += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) * \
                                          od_pair.get_n_passengers(t)
                if sum_travel_time_od_pair == 0:
                    logger.debug(f"No path for od pair {od_pair}")
                for activity in self._ean.get_activities(['time']):
                    target_event = activity.get_right_event()
                    if not isinstance(target_event, ean_data.EanEventOD):
                        raise LinTimException("Expected OD event, but got none!")
                    time_2 = target_event.get_time_2()
                    sum_penalty_changing_time_slices += \
                        od_pair.get_penalty(t, time_2) * self._parameters.period_length * \
                        round(self._m.getValue(self._arc_used[activity][od_pair][t])) * od_pair.get_n_passengers(t)

        objective_file = open(self._parameters.objectives_file_name, 'w')
        objective_file.write(f"sum drive distance vehicles full; {sum_line_length}\n")
        objective_file.write(f"sum line cost; {sum_line_cost}\n")
        objective_file.write(f"sum passenger travel time; {sum_travel_time}\n")
        objective_file.write(f"sum passenger drive time; {sum_drive_time}\n")
        objective_file.write(f"sum passenger wait time; {sum_wait_time}\n")
        objective_file.write(f"sum passenger transfer time; {sum_transfer_time}\n")
        objective_file.write(f"sum penalty changing time slices; {sum_penalty_changing_time_slices}\n")
        objective_file.write(f"number of transfers; {sum_transfers}\n")
        perceived_travel_time = self._parameters.factor_drive_time * sum_drive_time + \
                                self._parameters.factor_wait_time * sum_wait_time + \
                                self._parameters.factor_transfer_time * sum_transfer_time + \
                                self._parameters.transfer_penalty * sum_transfers
        objective_file.write(f"percieved travel time; {perceived_travel_time}\n")
        objective_file.write(f"Total value: {self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL)}")
        objective_file.close()
