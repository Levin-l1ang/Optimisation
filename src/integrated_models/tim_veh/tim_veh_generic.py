from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.solver.generic_solver_interface import Model, Solver, IntAttribute, \
    VariableType, LinearExpression, OptimizationSense, ConstraintSense, DoubleAttribute, Status, Variable
from ean_data import *
from ean_data import construct_aperiodic_ean
from line_data import Line
from tim_veh_helper import TimVehParameters
from vehicle_schedule import *
from vehicle_schedule import construct_vehicle_schedule_from_ip
from vs_helper import TurnaroundData
from write_csv import write_solver_statistic, write_periodic_timetable, write_aperiodic_ean, write_vehicle_schedule

logger = logging.getLogger(__name__)


MINUTES_PER_HOUR = 60


class TimVehGenericModel:

    def __init__(self, ean: Ean, line_pool: line_data.LinePool, turnaround_data: TurnaroundData,
                 parameters: TimVehParameters):
        self._ean = ean
        self._line_pool = line_pool
        self._turnaround_data = turnaround_data
        self._parameters = parameters
        # Model variables. Will be initialized/set later in self.create_model
        self._solver = Solver.createSolver(parameters.solver_type)
        self._m = None  # type: Union[None, Model]
        self.is_feasible = False
        # Big m constraints
        self._m_1 = 0
        self._m_2 = 0
        self._m_3 = 0
        self._m_4 = 0
        self._m_5 = 0
        # Variable dictionaries
        self._pi = {}  # type: Dict[EanEvent, Variable]
        self._modulo_parameter = {}  # type: Dict[EanActivity, Variable]
        self._vehicle_connect = {}  # type: Dict[int, Dict[Line, Dict[int, Dict[Line, Variable]]]]
        self._vehicle_from_depot = {}  # type: Dict[int, Dict[Line, Variable]]
        self._vehicle_to_depot = {}  # type: Dict[int, Dict[Line, Variable]]
        self._duration = {}  # type: Dict[Line, Variable]
        self._start = {}  # type: Dict[int, Dict[Line, Variable]]
        self._end = {}  # type: Dict[int, Dict[Line, Variable]]
        self._time_connection = {}  # type: Dict[int, Dict[Line, Dict[int, Dict[Line, Variable]]]]
        self._sum_vehicles = None  # type: Union[None, LinearExpression]

    def create_model(self):
        # Big M constraints
        self._set_big_m_constraints()
        # Initialize Model
        logger.debug("Initialize model")
        self._m = self._parameters.initialize_generic_model()
        self._create_variables()
        self._initialize_objective_function()
        self._add_constraints()
        if self._parameters.write_lp_output:
            self._write_lp_output()

    def _set_big_m_constraints(self):
        max_upper_bound = self._ean.compute_max_upper_bound()
        max_n_edges_in_line = self._line_pool.get_max_n_edges_in_line()
        self._m_1 = self._parameters.period_length
        self._m_2 = self._parameters.period_length
        self._m_3 = self._parameters.p_max * self._parameters.period_length + max_n_edges_in_line * max_upper_bound
        self._m_4 = max_upper_bound
        self._m_5 = self._parameters.p_max * self._parameters.period_length + max_n_edges_in_line * max_upper_bound
        logger.debug("m_1=%d\nm_2=%d\nm_3=%d\nm_4=%d\nm_5=%d" % (self._m_1, self._m_2, self._m_3, self._m_4, self._m_5))

    def _create_variables(self):
        logger.debug("Initialize variables")
        self._create_timetabling_variables()
        self._create_modulo_variables()
        self._create_vehicle_connection_variables()
        self._create_from_depot_variables()
        self._create_to_depot_variables()
        self._create_duration_variables()
        self._create_start_variables()
        self._create_end_variables()
        self._create_time_connection_variables()
        logger.debug(f"Number of variables: {self._m.getIntAttribute(IntAttribute.NUM_VARIABLES)}")

    def _create_timetabling_variables(self):
        logger.debug("\tPi")
        for event in self._ean.get_events_network():
            self._pi[event] = self._m.addVariable(0, self._parameters.period_length - 1, VariableType.INTEGER, 0,
                                                  f'pi_{event.get_event_id()}')
            if self._parameters.set_starting_timetable:
                self._m.setStartValue(self._pi[event], event.get_event_time())

    def _create_modulo_variables(self):
        logger.debug("\tModulo")
        for activity in self._ean.get_all_activities():
            self._modulo_parameter[activity] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                                   name=f'z_{activity.get_activity_id()}')

    def _create_vehicle_connection_variables(self):
        logger.debug("\tVehicle Connection")
        for p_1 in range(1, self._parameters.p_max + 1):
            self._vehicle_connect[p_1] = {}
            for l_1 in self._line_pool.get_lines():
                self._vehicle_connect[p_1][l_1] = {}
                for p_2 in range(1, self._parameters.p_max + 1):
                    self._vehicle_connect[p_1][l_1][p_2] = {}
                    for l_2 in self._line_pool.get_lines():
                        if self._parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            self._vehicle_connect[p_1][l_1][p_2][l_2] = \
                                self._m.addVariable(var_type=VariableType.BINARY,
                                                    name=f'x_({p_1},{l_1})'
                                                         f'({p_2},{l_2})')

    def _create_from_depot_variables(self):
        logger.debug("\tFrom Depot")
        for p in range(1, self._parameters.p_max + 1):
            self._vehicle_from_depot[p] = {}
            for line in self._line_pool.get_lines():
                self._vehicle_from_depot[p][line] = self._m.addVariable(var_type=VariableType.BINARY,
                                                                        name=f'x_depot,({p},'
                                                                             f'{line})')

    def _create_to_depot_variables(self):
        logger.debug("\tTo Depot")
        for p in range(1, self._parameters.p_max + 1):
            self._vehicle_to_depot[p] = {}
            for line in self._line_pool.get_lines():
                self._vehicle_to_depot[p][line] = self._m.addVariable(var_type=VariableType.BINARY,
                                                                      name=f'x_({p},'
                                                                           f'{line}),depot')

    def _create_duration_variables(self):
        logger.debug("\tDuration")
        for line in self._line_pool.get_lines():
            self._duration[line] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                       name=f'duration_{line}')

    def _create_start_variables(self):
        logger.debug("\tStart")
        for p in range(1, self._parameters.p_max + 1):
            self._start[p] = {}
            for line in self._line_pool.get_lines():
                self._start[p][line] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                           name=f'start({p},{line})')

    def _create_end_variables(self):
        logger.debug("\tEnd")
        for p in range(1, self._parameters.p_max + 1):
            self._end[p] = {}
            for line in self._line_pool.get_lines():
                self._end[p][line] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                         name=f'end({p},{line})')

    def _create_time_connection_variables(self):
        logger.debug("\tTime Connection")
        if self._parameters.factor_turn_around_time != 0:
            for p_1 in range(1, self._parameters.p_max + 1):
                self._time_connection[p_1] = {}
                for l_1 in self._line_pool.get_lines():
                    self._time_connection[p_1][l_1] = {}
                    for p_2 in range(1, self._parameters.p_max + 1):
                        self._time_connection[p_1][l_1][p_2] = {}
                        for l_2 in self._line_pool.get_lines():
                            if self._parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                                self._time_connection[p_1][l_1][p_2][l_2] = \
                                    self._m.addVariable(var_type=VariableType.INTEGER,
                                                        name=f'y_({p_1},{l_1})'
                                                             f'({p_2},{l_2})')

    def _initialize_objective_function(self):
        logger.debug("Initialize objective function")
        sum_travel_time = self._m.createExpression()
        sum_vehicle_time_full = self._m.createExpression()
        sum_vehicle_distance_full = self._m.createExpression()
        sum_turn_around_time = self._m.createExpression()
        sum_turn_around_distance = self._m.createExpression()
        self._sum_vehicles = self._m.createExpression()

        for activity in self._ean.get_activities(['drive', 'wait', 'trans']):
            sum_travel_time.multiAdd(activity.get_n_passengers(), self._pi[activity.get_right_event()])
            sum_travel_time.multiAdd(-activity.get_n_passengers(), self._pi[activity.get_left_event()])
            sum_travel_time.multiAdd(activity.get_n_passengers() * self._parameters.period_length,
                                    self._modulo_parameter[activity])

        for line in self._line_pool.get_lines():
            sum_vehicle_distance_full.addConstant(line.compute_length_from_ptn())
            sum_vehicle_time_full.multiAdd(1 / MINUTES_PER_HOUR, self._duration[line])

        for p_1 in range(1, self._parameters.p_max + 1):
            for l_1 in self._line_pool.get_lines():
                for p_2 in range(1, self._parameters.p_max + 1):
                    for l_2 in self._line_pool.get_lines():
                        if self._parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            if self._parameters.factor_turn_around_time != 0:
                                sum_turn_around_time.multiAdd(1 / MINUTES_PER_HOUR,
                                                            self._time_connection[p_1][l_1][p_2][l_2])
                            sum_turn_around_distance.multiAdd(self._turnaround_data
                                                             .get_min_turnaround_distance(l_1.get_last_stop(),
                                                                                      l_2.get_first_stop()),
                                                             self._vehicle_connect[p_1][l_1][p_2][l_2])
                sum_turn_around_time.multiAdd(self._turnaround_data.get_min_from_depot_time(l_1.get_first_stop()) /
                                             MINUTES_PER_HOUR, self._vehicle_from_depot[p_1][l_1])
                sum_turn_around_time.multiAdd(self._turnaround_data.get_min_to_depot_time(l_1.get_last_stop()) /
                                             MINUTES_PER_HOUR, self._vehicle_to_depot[p_1][l_1])
                sum_turn_around_distance.multiAdd(self._turnaround_data
                                                 .get_min_from_depot_distance(l_1.get_first_stop()),
                                                 self._vehicle_from_depot[p_1][l_1])
                sum_turn_around_distance.multiAdd(self._turnaround_data
                                                 .get_min_to_depot_distance(l_1.get_last_stop()),
                                                 self._vehicle_to_depot[p_1][l_1])
                self._sum_vehicles.multiAdd(1, self._vehicle_from_depot[p_1][l_1])

        objective = self._m.createExpression()
        objective.multiAdd(self._parameters.factor_travel_time, sum_travel_time)
        objective.multiAdd(self._parameters.factor_drive_time_unweighted, sum_vehicle_time_full)
        objective.multiAdd(self._parameters.factor_line_length, sum_vehicle_distance_full)
        objective.multiAdd(self._parameters.factor_turn_around_time, sum_turn_around_time)
        objective.multiAdd(self._parameters.factor_turn_around_distance, sum_turn_around_distance)
        objective.multiAdd(self._parameters.factor_vehicles, self._sum_vehicles)
        self._m.setObjective(objective, OptimizationSense.MINIMIZE)


    def _add_constraints(self):
        logger.debug("Add constraints:")
        self._add_timetabling_constraints()
        self._add_duration_constraints()
        self._add_start_and_end_constraints()
        self._add_minimum_time_difference_constraints()
        self._add_vehicle_flow_constraints()
        if self._parameters.factor_turn_around_time != 0:
            self._add_turnaround_time_constraints()
        if self._parameters.use_lower_bound:
            self._add_lower_bound_constraint()

    def get_duration(self, activity: EanActivity) -> LinearExpression:
        duration = self._m.createExpression()
        duration.multiAdd(1, self._pi[activity.get_right_event()])
        duration.multiAdd(-1, self._pi[activity.get_left_event()])
        duration.multiAdd(self._parameters.period_length, self._modulo_parameter[activity])
        return duration

    def _add_timetabling_constraints(self):
        logger.debug("\ttimetabling")
        for activity in self._ean.get_all_activities():
            lhs = self.get_duration(activity)
            self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, activity.get_lower_bound(),
                                  f"l_{activity.get_activity_id()}")
            self._m.addConstraint(lhs, ConstraintSense.LESS_EQUAL, activity.get_upper_bound(),
                                  f"u_{activity.get_activity_id()}")

    def _add_duration_constraints(self):
        lhs = self._m.createExpression()
        logging.debug("\tduration of a line")
        for line in self._line_pool.get_lines():
            lhs.clear()
            for activity in self._ean.get_activities_in_line(line):
                lhs.add(self.get_duration(activity))
            lhs.multiAdd(-1, self._duration[line])
            self._m.addConstraint(lhs, ConstraintSense.EQUAL, 0, f"line_duration_{line}")

    def _add_start_and_end_constraints(self):
        logging.debug("\tstart and end")
        lhs = self._m.createExpression()
        for p in range(1, self._parameters.p_max + 1):
            for line in self._line_pool.get_lines():
                lhs.clear()
                lhs.multiAdd(1, self._start[p][line])
                lhs.multiAdd(-1, self._pi[self._ean.get_first_event_in_line(line)])
                self._m.addConstraint(lhs, ConstraintSense.EQUAL, p * self._parameters.period_length,
                                      f"start_{p}_{line}")
                lhs.clear()
                lhs.multiAdd(1, self._end[p][line])
                lhs.multiAdd(-1, self._pi[self._ean.get_first_event_in_line(line)])
                lhs.multiAdd(-1, self._duration[line])
                self._m.addConstraint(lhs, ConstraintSense.EQUAL, p * self._parameters.period_length,
                                      f"end_{p}_{line}")

    def _add_minimum_time_difference_constraints(self):
        logger.debug("\tminimum time difference")
        lhs = self._m.createExpression()
        for p_1 in range(1, self._parameters.p_max + 1):
            for l_1 in self._line_pool.get_lines():
                for p_2 in range(1, self._parameters.p_max + 1):
                    for l_2 in self._line_pool.get_lines():
                        if self._parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            lhs.clear()
                            lhs.multiAdd(1, self._start[p_2][l_2])
                            lhs.multiAdd(-1, self._end[p_1][l_1])
                            lhs.multiAdd(-self._turnaround_data.
                                        get_min_turnaround_time(l_1.get_last_stop(),l_2.get_first_stop()),
                                        self._vehicle_connect[p_1][l_1][p_2][l_2])
                            lhs.multiAdd(-self._m_3, self._vehicle_connect[p_1][l_1][p_2][l_2])
                            self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, -self._m_3,
                                                  f"min_time_diff_{p_1}_{l_1}_"
                                                  f"{p_2}_{l_2}")

    def _add_vehicle_flow_constraints(self):
        self._add_outgoing_vehicle_flow_constraints()
        self._add_incoming_vehicle_flow_constraints()

    def _add_incoming_vehicle_flow_constraints(self):
        logger.debug("\tIncoming vehicle flow")
        lhs = self._m.createExpression()
        for p_2 in range(1, self._parameters.p_max + 1):
            for l_2 in self._line_pool.get_lines():
                lhs.clear()
                lhs.multiAdd(1, self._vehicle_from_depot[p_2][l_2])
                for p_1 in range(1, self._parameters.p_max + 1):
                    for l_1 in self._line_pool.get_lines():
                        if self._parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            lhs.multiAdd(1, self._vehicle_connect[p_1][l_1][p_2][l_2])
                self._m.addConstraint(lhs, ConstraintSense.EQUAL, 1, f"incoming_{p_2}_{l_2}")

    def _add_outgoing_vehicle_flow_constraints(self):
        logger.debug("\tOutgoing vehicle flow")
        lhs = self._m.createExpression()
        for p_1 in range(1, self._parameters.p_max + 1):
            for l_1 in self._line_pool.get_lines():
                lhs.clear()
                lhs.multiAdd(1, self._vehicle_to_depot[p_1][l_1])
                for p_2 in range(1, self._parameters.p_max + 1):
                    for l_2 in self._line_pool.get_lines():
                        if self._parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            lhs.multiAdd(1, self._vehicle_connect[p_1][l_1][p_2][l_2])
                self._m.addConstraint(lhs, ConstraintSense.EQUAL, 1, f"outgoing_{p_1}_{l_1}")

    def _add_turnaround_time_constraints(self):
        logger.debug("\tlinearization turn around time")
        lhs = self._m.createExpression()
        for p_1 in range(1, self._parameters.p_max + 1):
            for l_1 in self._line_pool.get_lines():
                for p_2 in range(1, self._parameters.p_max + 1):
                    for l_2 in self._line_pool.get_lines():
                        if self._parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            lhs.clear()
                            lhs.multiAdd(1, self._time_connection[p_1][l_1][p_2][l_2])
                            self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, 0,
                                                 f"lin_turnaround_1_{p_1}_{l_1}_{p_2}_{l_2}")
                            lhs.clear()
                            lhs.multiAdd(1, self._time_connection[p_1][l_1][p_2][l_2])
                            lhs.multiAdd(-1, self._start[p_2][l_2])
                            lhs.multiAdd(1, self._end[p_1][l_1])
                            lhs.multiAdd(-self._m_5, self._vehicle_connect[p_1][l_1][p_2][l_2])
                            self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL, -self._m_5,
                                                 f"lin_turnaround_2_{p_1}_{l_1}_{p_2}_{l_2}")

    def _add_lower_bound_constraint(self):
        lower_bound_duration = sum([a.get_lower_bound() for a in self._ean.get_activities(['drive', 'wait'])])
        self._m.addConstraint(self._sum_vehicles, ConstraintSense.GREATER_EQUAL,
                              math.floor(lower_bound_duration / self._parameters.period_length), "sum_vehicles")

    def _write_lp_output(self):
        self._m.write("TimVeh.lp")

    def solve(self):
        logger.debug("Start optimization")
        self._m.solve()
        self.is_feasible = self._m.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0
        if not self.is_feasible:
            logger.debug("No feasible solution found")
            if self._m.getStatus() == Status.INFEASIBLE:
                self._m.computeIIS("TimVeh.ilp")
            raise AlgorithmStoppingCriterionException("Tim Veh")
        if self._m.getStatus() == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        logger.debug("End optimization")

    def write_output(self):
        pi_solution = {event: int(round(self._m.getValue(var))) for event, var in self._pi.items()}
        duration_solution = {line: int(round(self._m.getValue(var))) for line, var in self._duration.items()}
        vehicle_connect_solution = {p_1:
                                        {l_1:
                                             {p_2:
                                                  {l_2: int(round(self._m.getValue(var)))
                                                   for l_2, var in temp_3.items()}
                                              for p_2, temp_3 in temp_2.items()}
                                         for l_1, temp_2 in temp.items()}
                                    for p_1, temp in self._vehicle_connect.items()}
        vehicle_from_depot_solution = {p:
                                           {l: int(round(self._m.getValue(var))) for l, var in temp.items()}
                                       for p, temp in self._vehicle_from_depot.items()}

        vs = construct_vehicle_schedule_from_ip(self._line_pool, vehicle_connect_solution, self._parameters,
                                                vehicle_from_depot_solution, self._parameters.vs_allow_empty_trips)
        aperiodic_ean = construct_aperiodic_ean(self._ean, vs, duration_solution, pi_solution,
                                                self._parameters.ean_earliest_time, self._parameters.period_length)

        write_solver_statistic(self._parameters, self._m.getDoubleAttribute(DoubleAttribute.RUNTIME),
                               self._m.getDoubleAttribute(DoubleAttribute.MIP_GAP),
                               self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL))
        write_periodic_timetable(self._parameters, self._ean, pi_solution)
        write_aperiodic_ean(self._parameters, self._ean, aperiodic_ean)
        write_vehicle_schedule(self._parameters, self._ean, aperiodic_ean, vs, duration_solution,
                               self._parameters.period_length)

