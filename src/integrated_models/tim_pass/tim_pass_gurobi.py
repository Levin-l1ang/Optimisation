import logging
from typing import List, Dict, Union

import gurobipy
from gurobipy.gurobipy import Var, Constr

import ean_data
import preprocessing
import ptn_data
import line_data
import od_data
from core.exceptions.exceptions import LinTimException
from ean_data import EanActivity, EanEvent
from gurobi_helper import initialize_gurobi_model
from od_data import ODPair
from preprocessing import EanPreprocessor
from tim_pass_helper import TimPassParameters

logger = logging.getLogger(__name__)


class TimPassGurobiModel:

    def __init__(self, ean: ean_data.Ean, ptn: ptn_data.Ptn, line_pool: line_data.LinePool, od: od_data.OD,
                 parameters: TimPassParameters):
        self.ean = ean
        self.ptn = ptn
        self.line_pool = line_pool
        self.od = od
        self.parameters = parameters

        # Objects used by the model
        self.ean_preprocessor = None  # type: Union[None, EanPreprocessor]

        self.m = None  # type: Union[None, Model]

        # Initialize model attributes
        self.unused_transfer_activities = []  # type: List[EanActivity]
        self.length_fix_passenger_lower_bound = 0
        self.max_upper_bound = 0
        self.m_2 = 0
        self.m_4 = 0
        self.is_feasible = False

        # Dictionaries for storing variables
        self.pi = {}  # type: Dict[EanEvent, Var]
        self.modulo_parameter = {}  # type: Dict[EanActivity, Var]
        self.arc_used = {}  # type: Dict[EanActivity, Dict[ODPair, Dict[int, Var]]]
        self.travel_time_linear = {}  # type: Dict[EanActivity, Dict[ODPair, Dict[int, Var]]]

        # List of constraints, used for writing the lp output if necessary
        self.timetabling_constraints = []  # type: List[Constr]
        self.passenger_flow_constraints = []  # type: List[Constr]
        self.passenger_flow_constraints_by_od_pair = {}  # type: Dict[ODPair, List[Constr]]
        self.time_slice_constraints = []  # type: List[Constr]
        self.linearization_travel_time_constraints = []  # type: List[Constr]

        # Lists for loops
        self.activities_drive_wait_trans = []  # type: List[EanActivity]
        self.activities_drive_wait_trans_restricted = []  # type: List[EanActivity]
        self.activities_drive_wait_trans_sync = []  # type: List[EanActivity]
        self.activities_drive_wait_trans_sync_restricted = []  # type: List[EanActivity]
        self.activities_time_to = []  # type: List[EanActivity]
        self.activities_to = []  # type: List[EanActivity]
        self.activities_from = []  # type: List[EanActivity]
        self.activities_no_sync = []  # type: List[EanActivity]
        self.active_od_pairs = []  # type: List[ODPair]

    def create_model(self):
        self.preprocess_ptn()
        self.preprocess_ean()
        self.set_big_m_constraints()
        self.initialize_loop_lists()
        self.m = initialize_gurobi_model(self.parameters, "TimPass")
        self.create_variables()
        self.create_objective_function()
        self.create_constraints()
        if self.parameters.write_lp_output:
            self.write_lp_output()

    def preprocess_ptn(self):
        # Restrict transfer stations
        if self.parameters.restrict_transfer_stations:
            transfer_stations = preprocessing.PtnPreprocessor.potential_transfer_stations(self.parameters,
                                                                                          self.line_pool, self.ptn)
            for activity in self.ean.get_activities(['trans']):
                source_event = activity.get_left_event()
                if not isinstance(source_event, ean_data.EanEventNetwork):
                    raise LinTimException("Expected network event, but got none!")
                if source_event.get_ptn_node() not in transfer_stations:
                    self.unused_transfer_activities.append(activity)

    def preprocess_ean(self):
        logger.debug("Compute fixed passenger paths...")
        self.ean_preprocessor = preprocessing.EanPreprocessor(self.ean, self.parameters,
                                                              self.unused_transfer_activities)
        if self.parameters.tim_pass_fix_passengers:
            self.length_fix_passenger_lower_bound = self.ean_preprocessor.compute_fixed_passenger_paths(self.ean,
                                                                                                        self.od,
                                                                                                        True)
        logger.debug("done!")

    def set_big_m_constraints(self):
        self.max_upper_bound = self.ean.compute_max_upper_bound()
        self.m_2 = self.parameters.period_length
        self.m_4 = self.max_upper_bound
        logger.debug(f"m_2={self.m_2}\nm_4={self.m_4}\n")

    def initialize_loop_lists(self):
        self.activities_drive_wait_trans = self.ean.get_activities(['drive', 'wait', 'trans'])
        self.activities_drive_wait_trans_restricted = [activity for activity in self.activities_drive_wait_trans if
                                                       activity not in self.unused_transfer_activities]
        self.activities_drive_wait_trans_sync = self.ean.get_activities(['drive', 'wait', 'trans', 'sync'])
        self.activities_drive_wait_trans_sync_restricted = [activity for activity in
                                                            self.activities_drive_wait_trans_sync if
                                                            activity not in self.unused_transfer_activities]
        self.activities_time_to = self.ean.get_activities(['time', 'to'])
        self.activities_to = self.ean.get_activities(['to'])
        self.activities_from = self.ean.get_activities(['from'])
        self.activities_no_sync = self.ean.get_activities(['drive', 'wait', 'trans', 'time', 'to', 'from'])
        self.active_od_pairs = self.od.get_active_od_pairs()
        logger.debug(f"Number of drive, wait, transfer activities: {len(self.activities_drive_wait_trans)}")
        logger.debug(f"Number of drive, wait, transfer activities without unused transfer activities: "
                     f"{len(self.activities_drive_wait_trans) - len(self.unused_transfer_activities)}")


    def create_variables(self):
        logger.debug("Initialize variables")
        self.create_timetable_variables()
        self.create_modulo_variables()
        self.create_arc_variables()
        self.create_travel_time_variables()
        self.m.update()
        logger.debug(f"Number of variables: {self.m.getAttr(gurobipy.GRB.Attr.NumVars)}")

    def create_timetable_variables(self):
        logger.debug("\tpi")
        for event in self.ean.get_events_network():
            self.pi[event] = self.m.addVar(0, self.parameters.period_length - 1, vtype=gurobipy.GRB.INTEGER,
                                           name=f'pi_{event.get_event_id()}')

    def create_modulo_variables(self):
        logger.debug("\tmodulo parameter")
        for activity in self.activities_drive_wait_trans_sync:
            self.modulo_parameter[activity] = self.m.addVar(vtype=gurobipy.GRB.INTEGER, name=f'z_{activity.get_activity_id()}')

    def create_arc_variables(self):
        logger.debug("\tarc used")
        for activity in self.activities_no_sync:
            self.arc_used[activity] = {}
            for od_pair in self.active_od_pairs:
                self.arc_used[activity][od_pair] = {}
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    self.arc_used[activity][od_pair][t] = self.m.addVar(vtype=gurobipy.GRB.BINARY,
                                                                        name=f'p_{activity.get_activity_id()}_{od_pair}_{t}')

    def create_travel_time_variables(self):
        logger.debug("\ttravel time linear")
        for activity in self.activities_drive_wait_trans:
            self.travel_time_linear[activity] = {}
            for od_pair in self.active_od_pairs:
                self.travel_time_linear[activity][od_pair] = {}
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    self.travel_time_linear[activity][od_pair][t] = self.m.addVar(vtype=gurobipy.GRB.INTEGER,
                                                                                  name=f'd_{activity.get_activity_id()}_{od_pair}_{t}')

    def create_objective_function(self):
        logger.debug("Initialize objective function")
        sum_travel_time = 0
        sum_drive_time = 0
        sum_wait_time = 0
        sum_transfer_time = 0
        sum_penalty_changing_time_slices = 0
        sum_transfers = 0

        for od_pair in self.active_od_pairs:
            for t in range(1, od_pair.get_n_time_slices() + 1):
                for activity in self.activities_drive_wait_trans_restricted:
                    sum_travel_time += self.travel_time_linear[activity][od_pair][t] * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'trans':
                        sum_transfers += self.arc_used[activity][od_pair][t] * od_pair.get_n_passengers(t)
                        sum_transfer_time += self.travel_time_linear[activity][od_pair][t] * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'wait':
                        sum_wait_time += self.travel_time_linear[activity][od_pair][t] * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'drive':
                        sum_drive_time += self.travel_time_linear[activity][od_pair][t] * od_pair.get_n_passengers(t)
                for activity in self.ean.get_activities(['time']):
                    target_event = activity.get_right_event()
                    if not isinstance(target_event, ean_data.EanEventOD):
                        raise LinTimException("Expected OD event, but got none!")
                    time_2 = target_event.get_time_2()
                    sum_penalty_changing_time_slices += od_pair.get_penalty(t, time_2) * self.parameters.period_length * \
                                                        self.arc_used[activity][od_pair][t] \
                                                        * od_pair.get_n_passengers(t)

        # add fix passenger paths
        for activity in self.activities_drive_wait_trans:
            if activity.get_n_passengers() == 0:
                continue
            duration_activity = self.get_duration(activity)
            sum_travel_time += duration_activity * activity.get_n_passengers()
            if activity.get_activity_type() == 'trans':
                sum_transfers += activity.get_n_passengers()
                sum_transfer_time += duration_activity * activity.get_n_passengers()
            if activity.get_activity_type() == 'wait':
                sum_wait_time += duration_activity * activity.get_n_passengers()
            if activity.get_activity_type() == 'drive':
                sum_drive_time += duration_activity * activity.get_n_passengers()

        self.m.setObjective(self.parameters.factor_travel_time * sum_travel_time +
                            self.parameters.factor_drive_time * sum_drive_time +
                            self.parameters.factor_transfer_time * sum_transfer_time +
                            self.parameters.factor_wait_time * sum_wait_time +
                            self.parameters.factor_penalty_time_slice * sum_penalty_changing_time_slices +
                            self.parameters.transfer_penalty * sum_transfers)

    def get_duration(self, activity):
        return self.pi[activity.get_right_event()] - self.pi[activity.get_left_event()] \
               + self.modulo_parameter[activity] * self.parameters.period_length

    def create_constraints(self):
        logger.debug("Add constraints:")
        self.create_timetabling_constraints()
        self.create_passenger_flow_constraints()
        self.create_time_slice_constraints()
        self.create_linearization_constraints()

    def create_timetabling_constraints(self):
        logger.debug("\ttimetabling")
        for activity in self.activities_drive_wait_trans_sync_restricted:
            i = activity.get_left_event()
            j = activity.get_right_event()
            const_1 = self.m.addConstr(self.pi[j] - self.pi[i] + self.modulo_parameter[activity]
                                       * self.parameters.period_length >= activity.get_lower_bound(),
                                       name=f"timetabling_lb_{activity.get_activity_id()}")
            const_2 = self.m.addConstr(self.pi[j] - self.pi[i] + self.modulo_parameter[activity] *
                                       self.parameters.period_length <= activity.get_upper_bound(),
                                       name=f"timetabling_ub_{activity.get_activity_id()}")
            if self.parameters.write_lp_output:
                self.timetabling_constraints.append(const_1)
                self.timetabling_constraints.append(const_2)

    def create_passenger_flow_constraints(self):
        logger.debug("\tpassenger flow")
        counter = 1
        for od_pair in self.active_od_pairs:
            logger.debug(f"\t\tOd-pair: {counter}")
            counter += 1
            self.passenger_flow_constraints_by_od_pair[od_pair] = []

            if self.parameters.use_preprocessing:
                used_tuple = self.ean_preprocessor.compute_potentially_used_events_and_activities(self.ean, od_pair)
                used_events = used_tuple[0]
                used_activities_drive_wait_trans = used_tuple[1]
            else:
                used_events = self.ean.get_all_events()
                used_activities_drive_wait_trans = self.activities_drive_wait_trans

            used_activities_drive_wait_trans = set(used_activities_drive_wait_trans) - \
                                               set(self.unused_transfer_activities)

            for t in range(1, od_pair.get_n_time_slices() + 1):
                for event in used_events:
                    sum_out = 0
                    sum_in = 0
                    right_hand_side = 0
                    empty = True
                    activities_to_check = []
                    for activity in used_activities_drive_wait_trans:
                        activities_to_check.append(activity)
                    for activity in self.activities_time_to:
                        event_to_check_od = activity.get_left_event()
                        event_to_check_used = activity.get_right_event()
                        if not self.parameters.use_preprocessing or event_to_check_used in used_events:
                            if not isinstance(event_to_check_od, ean_data.EanEventOD):
                                raise LinTimException("Expected OD event, got none!")
                            if event_to_check_od.check_attributes_od(od_pair.get_origin(),
                                                                     od_pair.get_destination(), t):
                                activities_to_check.append(activity)

                    for activity in self.activities_from:
                        event_to_check_od = activity.get_right_event()
                        event_to_check_used = activity.get_left_event()
                        if not self.parameters.use_preprocessing or event_to_check_used in used_events:
                            if not isinstance(event_to_check_od, ean_data.EanEventOD):
                                raise LinTimException("Expected OD event, got none!")
                            if event_to_check_od.check_attributes_od(od_pair.get_origin(),
                                                                     od_pair.get_destination(), t):
                                activities_to_check.append(activity)
                    for activity in activities_to_check:
                        if activity.get_left_event() == event:
                            sum_out += self.arc_used[activity][od_pair][t]
                            empty = False
                        if activity.get_right_event() == event:
                            sum_in += self.arc_used[activity][od_pair][t]
                            empty = False
                    if isinstance(event, ean_data.EanEventOD):
                        if event.check_attributes(od_pair.get_origin(), od_pair.get_destination(), 'source', t, t):
                            right_hand_side = 1
                        elif event.check_attributes(od_pair.get_origin(), od_pair.get_destination(), 'target', t, None):
                            right_hand_side = -1
                    if not empty:
                        const_1 = self.m.addConstr(sum_out - sum_in == right_hand_side,
                                                   name=f"pass_routing_{od_pair}_{t}_{event.get_event_id()}")
                        if self.parameters.write_lp_output:
                            self.passenger_flow_constraints.append(const_1)
                            self.passenger_flow_constraints_by_od_pair[od_pair].append(const_1)

    def create_time_slice_constraints(self):
        logger.debug("\ttime slice")
        for activity in self.activities_to:
            left_event = activity.get_left_event()
            if not isinstance(left_event, ean_data.EanEventOD):
                raise LinTimException("Expected OD event, but got none!")
            right_event = activity.get_right_event()
            origin = left_event.get_start()
            destination = left_event.get_end()
            time_1 = left_event.get_time_1()
            time_2 = left_event.get_time_2()
            od_pair = self.od.get_od_pair(origin, destination)
            if od_pair.is_active():
                const_1 = self.m.addConstr(self.pi[right_event] >=
                                           self.arc_used[activity][od_pair][time_1] * (time_2 - 1)
                                           * (self.parameters.period_length / od_pair.get_n_time_slices()),
                                           name=f"timeslice_lb_{activity.get_activity_id()}")
                const_2 = self.m.addConstr(self.pi[right_event] <=
                                           (time_2 * (self.parameters.period_length / od_pair.get_n_time_slices()) - 1)
                                           + self.m_2 * (1 - self.arc_used[activity][od_pair][time_1]),
                                           name=f"timeslice_ub_{activity.get_activity_id()}")
                if self.parameters.write_lp_output:
                    self.time_slice_constraints.append(const_1)
                    self.time_slice_constraints.append(const_2)

    def create_linearization_constraints(self):
        logger.debug("\tlinearization travel time")
        for activity in self.activities_drive_wait_trans_restricted:
            i = activity.get_left_event()
            j = activity.get_right_event()
            for od_pair in self.od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if activity.get_lower_bound() != activity.get_upper_bound():
                        const_1 = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] >= 0,
                                                   name=f"linearization_1_{od_pair}_{t}_{activity.get_activity_id()}")
                        const_2 = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] >=
                                                   self.pi[j] - self.pi[i] + self.modulo_parameter[activity] *
                                                   self.parameters.period_length - (1 - self.arc_used[activity][od_pair][t])
                                                   * self.m_4,
                                                   name=f"linearization_2_{od_pair}_{t}_{activity.get_activity_id()}")
                        if self.parameters.write_lp_output:
                            self.linearization_travel_time_constraints.append(const_1)
                            self.linearization_travel_time_constraints.append(const_2)
                    else:
                        const = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] ==
                                                 activity.get_lower_bound() * self.arc_used[activity][od_pair][t],
                                                 name=f"linearization_3_{od_pair}_{t}_{activity.get_activity_id()}")
                        if self.parameters.write_lp_output:
                            self.linearization_travel_time_constraints.append(const)

    def write_lp_output(self):
        self.m.update()
        self.m.write("TimPassGurobi.lp")
        dec_file = open('TimPass.dec', 'w')
        dec_file.write("PRESOLVED\n0\n")
        dec_file.write("NBLOCKS\n3\n")
        # Block 1: Timetabling
        dec_file.write("BLOCK 1\n")
        for const in self.timetabling_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Block 2: Passenger flow
        dec_file.write("BLOCK 2\n")
        for const in self.passenger_flow_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Master-constraints: Coupling
        dec_file.write("MASTERCONSS\n")
        # Timetabling + routing
        for const in self.time_slice_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in self.linearization_travel_time_constraints:
            dec_file.write(const.ConstrName + "\n")
        dec_file.close()
        dec_file = open('TimPassByODPair.dec', 'w')
        n_blocks = 2 + self.passenger_flow_constraints_by_od_pair.__len__()
        dec_file.write("PRESOLVED\n0\n")
        dec_file.write("NBLOCKS\n%d\n" % n_blocks)
        # Block 1: Timetabling
        dec_file.write("BLOCK 1\n")
        for const in self.timetabling_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Block 2: Passenger flow
        block_number = 2
        for od_pair in self.active_od_pairs:
            dec_file.write("BLOCK %d\n" % block_number)
            block_number += 1
            for const in self.passenger_flow_constraints_by_od_pair[od_pair]:
                dec_file.write(const.ConstrName + "\n")
        # Master-constraints: Coupling
        dec_file.write("MASTERCONSS\n")
        # Timetabling + routing
        for const in self.time_slice_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in self.linearization_travel_time_constraints:
            dec_file.write(const.ConstrName + "\n")
        dec_file.close()

    def solve(self):
        logger.debug("Start optimization")
        self.m.optimize()
        logger.debug("End optimization")
        self.is_feasible = self.m.SolCount != 0

    def write_output(self):
        self.write_solver_statistic()
        self.write_events()
        self.write_timetable()
        self.write_activities()
        self.write_objective()

    def write_solver_statistic(self):
        a = open(self.parameters.solver_statistic_filename, 'w')
        a.write(f'solver_time; {self.m.Runtime}\n')
        a.write(f'gap; {self.m.MIPGap}\n')
        a.close()

    def write_events(self):
        logger.debug("Print events")
        events_file = open(self.parameters.periodic_event_file_name, 'w')
        events_file.write("# event_index; type; stop; line; passengers; direction; repetition\n")
        event_index = 1
        for event in self.ean.get_events_network():
            passengers = event.get_n_passengers()
            for activity in event.get_outgoing_activities():
                for od_pair in self.od.get_active_od_pairs():
                    for t in range(1, od_pair.get_n_time_slices()):
                        if round(self.arc_used[activity][od_pair][t].x) == 1:
                            passengers += od_pair.get_n_passengers(t)
            event.set_event_id(event_index)
            event.set_n_passengers(passengers)
            # events_file.write("%s %d\n" % (event.to_events_periodic(), passengers))
            events_file.write(f"{event.to_events_periodic()}\n")
            event_index += 1
        events_file.close()

    def write_timetable(self):
        logger.debug("Print timetable")
        timetable_file = open(self.parameters.periodic_timetable_filename, 'w')
        timetable_file.write("# event_index; time\n")
        for event in self.ean.get_events_network():
            timetable_file.write(f"{event.get_event_id()}; {round(self.pi[event].x)}\n")
        timetable_file.close()

    def write_activities(self):
        logger.debug("Print activities")
        activities_file = open(self.parameters.periodic_activity_file_name, 'w')
        activities_file.write("# activity_index; type; from_event; to_event; lower_bound; upper_bound; passengers\n")
        activity_index = 1
        for activity in self.ean.get_activities(['drive', 'wait']):

            passengers = activity.get_n_passengers()
            for od_pair in self.od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if round(self.arc_used[activity][od_pair][t].x) == 1:
                        passengers += od_pair.get_n_passengers(t)
            activity.set_activity_id(activity_index)
            activity.set_n_passengers(passengers)
            activities_file.write(f"{activity.to_activities_periodic()} {passengers}\n")
            activity_index += 1
        for activity in self.ean.get_activities(['sync']):
            passengers = activity.get_n_passengers()
            activity.set_activity_id(activity_index)
            activities_file.write(f"{activity.to_activities_periodic()} {passengers}\n")
            activity_index += 1
        for activity in self.ean.get_activities(['trans']):
            passengers = activity.get_n_passengers()
            for od_pair in self.od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if round(self.arc_used[activity][od_pair][t].x) == 1:
                        passengers += od_pair.get_n_passengers(t)
            activity.set_activity_id(activity_index)
            activity.set_n_passengers(passengers)
            activities_file.write(f"{activity.to_activities_periodic()} {passengers}\n")
            activity_index += 1
        activities_file.close()

    def write_objective(self):
        logger.debug("Computing all parts of the objective")
        sum_drive_time = 0
        sum_travel_time = 0
        sum_wait_time = 0
        sum_transfer_time = 0
        sum_penalty_changing_time_slices = 0
        sum_transfers = 0
        for od_pair in self.active_od_pairs:
            for t in range(1, od_pair.get_n_time_slices() + 1):
                sum_travel_time_od_pair = 0
                for activity in self.activities_drive_wait_trans:
                    sum_travel_time += round(self.travel_time_linear[activity][od_pair][t].x) \
                                       * od_pair.get_n_passengers(t)
                    sum_travel_time_od_pair += round(self.travel_time_linear[activity][od_pair][t].x) \
                                               * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'trans':
                        sum_transfers += round(self.arc_used[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                        sum_transfer_time += round(self.travel_time_linear[activity][od_pair][t].x) \
                                             * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'wait':
                        sum_wait_time += round(self.travel_time_linear[activity][od_pair][t].x) \
                                         * od_pair.get_n_passengers(t)
                    if activity.get_activity_type() == 'drive':
                        sum_drive_time += round(self.travel_time_linear[activity][od_pair][t].x) \
                                          * od_pair.get_n_passengers(t)
                if od_pair.get_n_passengers(t) != 0 and sum_travel_time_od_pair == 0:
                    print(f"No path for od pair {od_pair}")
                for activity in self.ean.get_activities(['time']):
                    target_event = activity.get_right_event()
                    if not isinstance(target_event, ean_data.EanEventOD):
                        raise LinTimException("Expected OD event, but got none!")
                    time_2 = target_event.get_time_2()
                    sum_penalty_changing_time_slices += od_pair.get_penalty(t, time_2) * self.parameters.period_length \
                                                        * round(self.arc_used[activity][od_pair][t].x) \
                                                        * od_pair.get_n_passengers(t)
        travel_time_routed_od_pairs = self.parameters.factor_travel_time * sum_travel_time \
                                      + self.parameters.factor_drive_time * sum_drive_time \
                                      + self.parameters.factor_transfer_time * sum_transfer_time \
                                      + self.parameters.factor_wait_time * sum_wait_time \
                                      + self.parameters.transfer_penalty * sum_transfers
        lower_bound_objective = self.length_fix_passenger_lower_bound + travel_time_routed_od_pairs
        n_passengers = 0
        for od_pair in self.od.get_all_od_pairs():
            n_passengers += od_pair.get_n_passengers(1)
        objective_str = 'objective; ' + str(self.m.objVal)
        objective_per_pass_str = 'objective_per_passenger; ' + str(self.m.objVal / n_passengers)
        length_fix_passenger_lower_bound_str = f'length_fix_passengers_lower_bound; ' \
                                               f'{self.length_fix_passenger_lower_bound}'
        travel_time_routed_od_pairs_str = f'travel_time_routed_OD_pairs; {travel_time_routed_od_pairs}'
        lower_bound_objective_str = f'LB_objective; {lower_bound_objective}'
        lower_bound_objective_per_pass_str = f'LB_objective_per_passenger; {lower_bound_objective / n_passengers}'
        a = open(self.parameters.solver_statistic_filename, 'a')
        a.write(objective_str + '\n')
        a.write(objective_per_pass_str + '\n')
        a.write(length_fix_passenger_lower_bound_str + '\n')
        a.write(travel_time_routed_od_pairs_str + '\n')
        a.write(lower_bound_objective_str + '\n')
        a.write(lower_bound_objective_per_pass_str)
        a.close()


