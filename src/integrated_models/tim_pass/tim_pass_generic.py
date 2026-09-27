import logging
from typing import List, Dict, Union

import ean_data
import preprocessing
import ptn_data
import line_data
import od_data
from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.exceptions.exceptions import LinTimException
from core.solver.generic_solver_interface import Model, OptimizationSense, VariableType, Variable, Constraint, \
    LinearExpression, ConstraintSense, DoubleAttribute, IntAttribute, Status
from ean_data import EanActivity, EanEvent
from od_data import ODPair
from preprocessing import EanPreprocessor
from tim_pass_helper import TimPassParameters
from write_csv import write_solver_statistic, write_periodic_timetable, write_periodic_events, write_periodic_activities

logger = logging.getLogger(__name__)


class TimPassGenericModel:

    def __init__(self, ean: ean_data.Ean, ptn: ptn_data.Ptn, line_pool: line_data.LinePool, od: od_data.OD,
                 parameters: TimPassParameters):
        self._ean = ean
        self._ptn = ptn
        self._line_pool = line_pool
        self._od = od
        self._parameters = parameters

        # Objects used by the model
        self._ean_preprocessor = None  # type: Union[None, EanPreprocessor]

        self._m = None  # type: Union[None, Model]

        # Initialize model attributes
        self._unused_transfer_activities = []  # type: List[EanActivity]
        self._length_fix_passenger_lower_bound = 0
        self._max_upper_bound = 0
        self._m_2 = 0
        self._m_4 = 0
        self.is_feasible = False

        # Dictionaries for storing variables
        self._pi = {}  # type: Dict[EanEvent, Variable]
        self._modulo_parameter = {}  # type: Dict[EanActivity, Variable]
        self._arc_used = {}  # type: Dict[EanActivity, Dict[ODPair, Dict[int, Variable]]]
        self._travel_time_linear = {}  # type: Dict[EanActivity, Dict[ODPair, Dict[int, Variable]]]

        # List of constraints, used for writing the lp output if necessary
        self._timetabling_constraints = []  # type: List[Constraint]
        self._passenger_flow_constraints = []  # type: List[Constraint]
        self._passenger_flow_constraints_by_od_pair = {}  # type: Dict[ODPair, List[Constraint]]
        self._time_slice_constraints = []  # type: List[Constraint]
        self._linearization_travel_time_constraints = []  # type: List[Constraint]

        # Lists for loops
        self._activities_drive_wait_trans = []  # type: List[EanActivity]
        self._activities_drive_wait_trans_restricted = []  # type: List[EanActivity]
        self._activities_drive_wait_trans_sync = []  # type: List[EanActivity]
        self._activities_drive_wait_trans_sync_restricted = []  # type: List[EanActivity]
        self._activities_time_to = []  # type: List[EanActivity]
        self._activities_to = []  # type: List[EanActivity]
        self._activities_from = []  # type: List[EanActivity]
        self._activities_no_sync = []  # type: List[EanActivity]
        self._active_od_pairs = []  # type: List[ODPair]

    def create_model(self):
        self._preprocess_ptn()
        self._preprocess_ean()
        self._initialize_big_m_constraints()
        self._initialize_loop_lists()
        self._m = self._parameters.initialize_generic_model()
        self._add_variables()
        self._create_objective_function()
        self._create_constraints()
        if self._parameters.write_lp_output:
            self._write_lp_output()

    def _preprocess_ptn(self):
        # Restrict transfer stations
        if self._parameters.restrict_transfer_stations:
            transfer_stations = preprocessing.PtnPreprocessor.potential_transfer_stations(self._parameters,
                                                                                          self._line_pool,
                                                                                          self._ptn)
            for activity in self._ean.get_activities(['trans']):
                source_event = activity.get_left_event()
                if not isinstance(source_event, ean_data.EanEventNetwork):
                    raise LinTimException("Expected network event, but got none!")
                if source_event.get_ptn_node() not in transfer_stations:
                    self._unused_transfer_activities.append(activity)

    def _preprocess_ean(self):
        logger.debug("Compute fixed passenger paths...")
        self._ean_preprocessor = preprocessing.EanPreprocessor(self._ean, self._parameters,
                                                               self._unused_transfer_activities)
        if self._parameters.tim_pass_fix_passengers:
            self._length_fix_passenger_lower_bound = self._ean_preprocessor.compute_fixed_passenger_paths(self._ean,
                                                                                                          self._od,
                                                                                                          True)
        logger.debug("done!")

    def _initialize_big_m_constraints(self):
        self._max_upper_bound = self._ean.compute_max_upper_bound()
        self._m_2 = self._parameters.period_length
        self._m_4 = self._max_upper_bound
        logger.debug(f"m_2={self._m_2}\nm_4={self._m_4}\n")

    def _initialize_loop_lists(self):
        self._activities_drive_wait_trans = self._ean.get_activities(['drive', 'wait', 'trans'])
        self._activities_drive_wait_trans_restricted = [activity for activity in self._activities_drive_wait_trans if
                                                        activity not in self._unused_transfer_activities]
        self._activities_drive_wait_trans_sync = self._ean.get_activities(['drive', 'wait', 'trans', 'sync'])
        self._activities_drive_wait_trans_sync_restricted = [activity for activity in
                                                             self._activities_drive_wait_trans_sync if
                                                             activity not in self._unused_transfer_activities]
        self._activities_time_to = self._ean.get_activities(['time', 'to'])
        self._activities_to = self._ean.get_activities(['to'])
        self._activities_from = self._ean.get_activities(['from'])
        self._activities_no_sync = self._ean.get_activities(['drive', 'wait', 'trans', 'time', 'to', 'from'])
        self._active_od_pairs = self._od.get_active_od_pairs()
        logger.debug(f"Number of drive, wait, transfer activities: {len(self._activities_drive_wait_trans)}")
        logger.debug(f"Number of drive, wait, transfer activities without unused transfer activities: "
                     f"{len(self._activities_drive_wait_trans) - len(self._unused_transfer_activities)}")

    def _add_variables(self):
        logger.debug("Initialize variables")
        self._create_timetable_variables()
        self._create_modulo_variables()
        self._create_arc_variables()
        self._create_travel_time_variables()
        logger.debug(f"Number of variables: {self._m.getIntAttribute(IntAttribute.NUM_VARIABLES)}")

    def _create_timetable_variables(self):
        logger.debug("\tpi")
        for event in self._ean.get_events_network():
            self._pi[event] = self._m.addVariable(0, self._parameters.period_length - 1, VariableType.INTEGER, 0,
                                                f'pi_{event.get_event_id()}')

    def _create_modulo_variables(self):
        logger.debug("\tmodulo parameter")
        for activity in self._activities_drive_wait_trans_sync:
            self._modulo_parameter[activity] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                                   name=f'z_{activity.get_activity_id()}')

    def _create_arc_variables(self):
        logger.debug("\tarc used")
        for activity in self._activities_no_sync:
            self._arc_used[activity] = {}
            for od_pair in self._active_od_pairs:
                self._arc_used[activity][od_pair] = {}
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    self._arc_used[activity][od_pair][t] = self._m.addVariable(var_type=VariableType.BINARY,
                                                                               name=f'p_{activity.get_activity_id()}_'
                                                                                    f'{od_pair}_{t}')

    def _create_travel_time_variables(self):
        logger.debug("\ttravel time linear")
        for activity in self._activities_drive_wait_trans:
            self._travel_time_linear[activity] = {}
            for od_pair in self._active_od_pairs:
                self._travel_time_linear[activity][od_pair] = {}
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    self._travel_time_linear[activity][od_pair][t] = \
                        self._m.addVariable(var_type=VariableType.INTEGER,
                                            name=f'd_{activity.get_activity_id()}_{od_pair}_{t}')

    def _create_objective_function(self):
        logger.debug("Initialize objective function")
        sum_travel_time = self._m.createExpression()
        sum_drive_time = self._m.createExpression()
        sum_wait_time = self._m.createExpression()
        sum_transfer_time = self._m.createExpression()
        sum_penalty_changing_time_slices = self._m.createExpression()
        sum_transfers = self._m.createExpression()

        for od_pair in self._active_od_pairs:
            for t in range(1, od_pair.get_n_time_slices() + 1):
                for activity in self._activities_drive_wait_trans_restricted:
                    sum_travel_time.multiAdd(od_pair.get_n_passengers(t),
                                            self._travel_time_linear[activity][od_pair][t])
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
                    factor = od_pair.get_penalty(t, time_2) * self._parameters.period_length \
                             * od_pair.get_n_passengers(t)
                    sum_penalty_changing_time_slices.multiAdd(factor, self._arc_used[activity][od_pair][t])

        # add fix passenger paths
        for activity in self._activities_drive_wait_trans:
            if activity.get_n_passengers() == 0:
                continue
            duration_activity = self._get_duration(activity)
            sum_travel_time.multiAdd(activity.get_n_passengers(), duration_activity)
            if activity.get_activity_type() == 'trans':
                sum_transfers.addConstant(activity.get_n_passengers())
                sum_transfer_time.multiAdd(activity.get_n_passengers(), duration_activity)
            if activity.get_activity_type() == 'wait':
                sum_wait_time.multiAdd(activity.get_n_passengers(), duration_activity)
            if activity.get_activity_type() == 'drive':
                sum_drive_time.multiAdd(activity.get_n_passengers(), duration_activity)

        objective = self._m.createExpression()
        objective.multiAdd(self._parameters.factor_travel_time, sum_travel_time)
        objective.multiAdd(self._parameters.factor_drive_time, sum_drive_time)
        objective.multiAdd(self._parameters.factor_transfer_time, sum_transfer_time)
        objective.multiAdd(self._parameters.factor_wait_time, sum_wait_time)
        objective.multiAdd(self._parameters.factor_penalty_time_slice, sum_penalty_changing_time_slices)
        objective.multiAdd(self._parameters.transfer_penalty, sum_transfers)

        self._m.setObjective(objective, OptimizationSense.MINIMIZE)

    def _get_duration(self, activity) -> LinearExpression:
        result = self._m.createExpression()
        result.add(self._pi[activity.get_right_event()])
        result.multiAdd(-1, self._pi[activity.get_left_event()])
        result.multiAdd(self._parameters.period_length, self._modulo_parameter[activity])
        return result

    def _create_constraints(self):
        logger.debug("Add constraints:")
        self._create_timetabling_constraints()
        self._create_passenger_flow_constraints()
        self._create_time_slice_constraints()
        self._create_linearization_constraints()

    def _create_timetabling_constraints(self):
        logger.debug("\ttimetabling")
        for activity in self._activities_drive_wait_trans_sync_restricted:
            const_1 = self._m.addConstraint(self._get_duration(activity), ConstraintSense.GREATER_EQUAL,
                                            activity.get_lower_bound(), f"timetabling_lb_{activity.get_activity_id()}")
            const_2 = self._m.addConstraint(self._get_duration(activity), ConstraintSense.LESS_EQUAL,
                                            activity.get_upper_bound(), f"timetabling_ub_{activity.get_activity_id()}")
            if self._parameters.write_lp_output:
                self._timetabling_constraints.append(const_1)
                self._timetabling_constraints.append(const_2)

    def _create_passenger_flow_constraints(self):
        logger.debug("\tpassenger flow")
        counter = 1
        lhs = self._m.createExpression()
        for od_pair in self._active_od_pairs:
            logger.debug(f"\t\tOD-pair: {counter}")
            counter += 1
            self._passenger_flow_constraints_by_od_pair[od_pair] = []

            if self._parameters.use_preprocessing:
                used_tuple = self._ean_preprocessor.compute_potentially_used_events_and_activities(self._ean, od_pair)
                used_events = used_tuple[0]
                used_activities_drive_wait_trans = used_tuple[1]
            else:
                used_events = self._ean.get_all_events()
                used_activities_drive_wait_trans = self._activities_drive_wait_trans

            used_activities_drive_wait_trans = set(used_activities_drive_wait_trans) - \
                                               set(self._unused_transfer_activities)
            for t in range(1, od_pair.get_n_time_slices() + 1):
                for event in used_events:
                    lhs.clear()
                    rhs = 0
                    empty = True
                    activities_to_check = []
                    for activity in used_activities_drive_wait_trans:
                        activities_to_check.append(activity)
                    for activity in self._activities_time_to:
                        event_to_check_od = activity.get_left_event()
                        event_to_check_used = activity.get_right_event()
                        if not self._parameters.use_preprocessing or event_to_check_used in used_events:
                            if not isinstance(event_to_check_od, ean_data.EanEventOD):
                                raise LinTimException("Expected OD event, got none!")
                            if event_to_check_od.check_attributes_od(od_pair.get_origin(),
                                                                     od_pair.get_destination(), t):
                                activities_to_check.append(activity)

                    for activity in self._activities_from:
                        event_to_check_od = activity.get_right_event()
                        event_to_check_used = activity.get_left_event()
                        if not self._parameters.use_preprocessing or event_to_check_used in used_events:
                            if not isinstance(event_to_check_od, ean_data.EanEventOD):
                                raise LinTimException("Expected OD event, got none!")
                            if event_to_check_od.check_attributes_od(od_pair.get_origin(),
                                                                     od_pair.get_destination(), t):
                                activities_to_check.append(activity)
                    for activity in activities_to_check:
                        if activity.get_left_event() == event:
                            lhs.add(self._arc_used[activity][od_pair][t])
                            empty = False
                        if activity.get_right_event() == event:
                            lhs.multiAdd(-1, self._arc_used[activity][od_pair][t])
                            empty = False
                    if isinstance(event, ean_data.EanEventOD):
                        if event.check_attributes(od_pair.get_origin(), od_pair.get_destination(), 'source', t, t):
                            rhs = 1
                        elif event.check_attributes(od_pair.get_origin(), od_pair.get_destination(), 'target', t, None):
                            rhs = -1
                    if not empty:
                        const_1 = self._m.addConstraint(lhs, ConstraintSense.EQUAL, rhs,
                                                   f"pass_routing_{od_pair}_{t}_{event.get_event_id()}")
                        if self._parameters.write_lp_output:
                            self._passenger_flow_constraints.append(const_1)
                            self._passenger_flow_constraints_by_od_pair[od_pair].append(const_1)

    def _create_time_slice_constraints(self):
        logger.debug("\ttime slice")
        lhs = self._m.createExpression()
        for activity in self._activities_to:
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
                lhs.clear()
                lhs.add(self._pi[right_event])
                lhs.multiAdd((1 - time_2) * (self._parameters.period_length / od_pair.get_n_time_slices()),
                            self._arc_used[activity][od_pair][time_1])
                const_1 = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, 0,
                                           f"timeslice_lb_{activity.get_activity_id()}")
                lhs.clear()
                lhs.add(self._pi[right_event])
                lhs.multiAdd(self._m_2, self._arc_used[activity][od_pair][time_1])
                const_2 = self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, self._m_2 +
                                                (time_2 * (self._parameters.period_length /
                                                           od_pair.get_n_time_slices()) - 1),
                                           f"timeslice_ub_{activity.get_activity_id()}")
                if self._parameters.write_lp_output:
                    self._time_slice_constraints.append(const_1)
                    self._time_slice_constraints.append(const_2)

    def _create_linearization_constraints(self):
        logger.debug("\tlinearization travel time")
        lhs = self._m.createExpression()
        for activity in self._activities_drive_wait_trans_restricted:
            for od_pair in self._od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if activity.get_lower_bound() != activity.get_upper_bound():
                        lhs.clear()
                        lhs.add(self._travel_time_linear[activity][od_pair][t])
                        const_1 = self._m.addConstraint(lhs,
                                                        ConstraintSense.GREATER_EQUAL, 0,
                                                       f"linearization_1_{od_pair}_"
                                                       f"{t}_{activity}")
                        lhs.multiAdd(-1, self._get_duration(activity))
                        lhs.multiAdd(-self._m_4, self._arc_used[activity][od_pair][t])
                        const_2 = self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, -self._m_4,
                                                        name=f"linearization_2_{od_pair}_"
                                                             f"{t}_{activity}")
                        if self._parameters.write_lp_output:
                            self._linearization_travel_time_constraints.append(const_1)
                            self._linearization_travel_time_constraints.append(const_2)
                    else:
                        lhs.clear()
                        lhs.add(self._travel_time_linear[activity][od_pair][t])
                        lhs.multiAdd(-activity.get_lower_bound(), self._arc_used[activity][od_pair][t])
                        const = self._m.addConstraint(lhs, ConstraintSense.EQUAL, 0,
                                                 f"linearization_3_{od_pair}_{t}_{activity}")
                        if self._parameters.write_lp_output:
                            self._linearization_travel_time_constraints.append(const)

    def _write_lp_output(self):
        self._m.write("TimPass.lp")
        dec_file = open('TimPass.dec', 'w')
        dec_file.write("PRESOLVED\n0\n")
        dec_file.write("NBLOCKS\n3\n")
        # Block 1: Timetabling
        dec_file.write("BLOCK 1\n")
        for const in self._timetabling_constraints:
            dec_file.write(const.getName() + "\n")
        # Block 2: Passenger flow
        dec_file.write("BLOCK 2\n")
        for const in self._passenger_flow_constraints:
            dec_file.write(const.getName() + "\n")
        # Master-constraints: Coupling
        dec_file.write("MASTERCONSS\n")
        # Timetabling + routing
        for const in self._time_slice_constraints:
            dec_file.write(const.getName() + "\n")
        for const in self._linearization_travel_time_constraints:
            dec_file.write(const.getName() + "\n")
        dec_file.close()
        dec_file = open('TimPassByODPair.dec', 'w')
        n_blocks = 2 + self._passenger_flow_constraints_by_od_pair.__len__()
        dec_file.write("PRESOLVED\n0\n")
        dec_file.write("NBLOCKS\n%d\n" % n_blocks)
        # Block 1: Timetabling
        dec_file.write("BLOCK 1\n")
        for const in self._timetabling_constraints:
            dec_file.write(const.getName() + "\n")
        # Block 2: Passenger flow
        block_number = 2
        for od_pair in self._active_od_pairs:
            dec_file.write("BLOCK %d\n" % block_number)
            block_number += 1
            for const in self._passenger_flow_constraints_by_od_pair[od_pair]:
                dec_file.write(const.getName() + "\n")
        # Master-constraints: Coupling
        dec_file.write("MASTERCONSS\n")
        # Timetabling + routing
        for const in self._time_slice_constraints:
            dec_file.write(const.getName() + "\n")
        for const in self._linearization_travel_time_constraints:
            dec_file.write(const.getName() + "\n")
        dec_file.close()

    def solve(self):
        logger.debug("Start optimization")
        self._m.solve()
        logger.debug("End optimization")
        self.is_feasible = self._m.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0
        if not self.is_feasible:
            logger.debug("No feasible solution found")
            if self._m.getStatus() == Status.INFEASIBLE:
                self._m.computeIIS("TimPass.ilp")
            raise AlgorithmStoppingCriterionException("Tim Pass")
        if self._m.getStatus() == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        logger.debug("Feasible solution found")
        logger.debug("End optimization")

    def write_output(self):
        arc_used_solution = {activity:
                                 {od_pair:
                                      {t: int(round(self._m.getValue(var)))
                                       for t, var in temp_2.items()}
                                  for od_pair, temp_2 in temp.items()}
                             for activity, temp in self._arc_used.items()}
        write_solver_statistic(self._parameters, self._m.getDoubleAttribute(DoubleAttribute.RUNTIME),
                               self._m.getDoubleAttribute(DoubleAttribute.MIP_GAP),
                               self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL))
        write_periodic_events(self._parameters, self._ean, self._od, arc_used_solution)
        self._write_timetable()
        write_periodic_activities(self._parameters, self._ean, self._od, arc_used_solution)
        self._write_objective()

    def _write_timetable(self):
        pi_solution = {event: int(round(self._m.getValue(var))) for event, var in self._pi.items()}
        write_periodic_timetable(self._parameters, self._ean, pi_solution)

    def _write_objective(self):
        logger.debug("Computing all parts of the objective")
        sum_drive_time = 0
        sum_travel_time = 0
        sum_wait_time = 0
        sum_transfer_time = 0
        sum_penalty_time_slice = 0
        sum_transfers = 0
        for od_pair in self._active_od_pairs:
            for t in range(1, od_pair.get_n_time_slices() + 1):
                sum_travel_time_od_pair = 0
                for activity in self._activities_drive_wait_trans:
                    sum_travel_time += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) \
                                       * od_pair.get_n_passengers(t)
                    sum_travel_time_od_pair += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) \
                                               * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'trans':
                        sum_transfers += round(self._m.getValue(self._arc_used[activity][od_pair][t])) \
                                         * od_pair.get_n_passengers(t)
                        sum_transfer_time += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) \
                                             * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'wait':
                        sum_wait_time += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) \
                                         * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'drive':
                        sum_drive_time += round(self._m.getValue(self._travel_time_linear[activity][od_pair][t])) \
                                          * od_pair.get_n_passengers(t)
                if od_pair.get_n_passengers(t) != 0 and sum_travel_time_od_pair == 0:
                    logger.warning(f"No path for OD pair {od_pair}")
                for activity in self._ean.get_activities(['time']):
                    target_event = activity.get_right_event()
                    if not isinstance(target_event, ean_data.EanEventOD):
                        raise LinTimException("Expected OD event, but got none!")
                    time_2 = target_event.get_time_2()
                    sum_penalty_time_slice += od_pair.get_penalty(t, time_2) * self._parameters.period_length \
                                              * round(self._m.getValue(self._arc_used[activity][od_pair][t])) \
                                              * od_pair.get_n_passengers(t)
        travel_time_routed_od_pairs = self._parameters.factor_travel_time * sum_travel_time \
                                      + self._parameters.factor_drive_time * sum_drive_time \
                                      + self._parameters.factor_transfer_time * sum_transfer_time \
                                      + self._parameters.factor_wait_time * sum_wait_time \
                                      + self._parameters.transfer_penalty * sum_transfers
        n_passengers = 0
        for od_pair in self._od.get_all_od_pairs():
            n_passengers += od_pair.get_n_passengers(1)
        objective_str = 'objective; ' + str(self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL))
        objective_per_pass_str = 'objective_per_passenger; ' + str(self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL) /
                                                                   n_passengers)
        length_fix_passenger_lower_bound_str = f'length_fix_passengers_lower_bound; ' \
                                               f'{self._length_fix_passenger_lower_bound}'
        travel_time_routed_od_pairs_str = f'travel_time_routed_OD_pairs; {travel_time_routed_od_pairs}'
        objective_file = open(self._parameters.objectives_file_name, 'w')
        objective_file.write(objective_str + '\n')
        objective_file.write(objective_per_pass_str + '\n')
        objective_file.write(length_fix_passenger_lower_bound_str + '\n')
        objective_file.write(travel_time_routed_od_pairs_str + '\n')
        objective_file.close()


