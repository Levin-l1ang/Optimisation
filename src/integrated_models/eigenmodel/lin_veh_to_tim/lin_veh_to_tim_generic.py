import logging
from typing import Dict, Union

from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.solver.generic_solver_interface import IntAttribute, Model, Variable, VariableType, LinearExpression, \
    OptimizationSense, ConstraintSense, Status, DoubleAttribute
from ean_data import EanEvent, EanActivity, Ean, AperiodicEan
from lin_veh_to_tim_helper import LinVehToTimParameters
from line_data import LinePool, Line
from vehicle_schedule import VehicleSchedule
from vs_helper import TurnaroundData
from write_csv import write_periodic_timetable, write_aperiodic_ean, write_solver_statistic, write_vehicle_schedule

logger = logging.getLogger(__name__)


class LinVehToTimGenericModel:

    def __init__(self, parameters: LinVehToTimParameters, ean: Ean, line_pool: LinePool,
                 vehicle_schedule: VehicleSchedule, turn_around_data: TurnaroundData) -> None:
        # Given data
        self._parameters = parameters
        self._line_pool = line_pool
        self._ean = ean
        self._turn_around_data = turn_around_data
        self._vehicle_schedule = vehicle_schedule

        # Instance data

        # Model data
        self._m = None  # type: Union[None, Model]
        self.is_feasible = False
        self._pi = {}  # type: Dict[EanEvent, Variable]
        self._modulo_parameter = {}  # type: Dict[EanActivity, Variable]
        self._duration = {}  # type: Dict[Line, Variable]
        self._start = {}  # type: Dict[Line, Dict[int, Variable]]
        self._end = {}  # type: Dict[Line, Dict[int, Variable]]

    def create_model(self) -> None:
        self._m = self._parameters.initialize_generic_model()
        self._create_variables()
        self._initialize_objective_function()
        self._create_constraints()
        if self._parameters.write_lp_output:
            self._write_lp_output()

    def _create_variables(self) -> None:
        logger.debug("Initialize variables")
        self._add_timetabling_variables()
        self._add_duration_variables()
        self._add_start_variables()
        self._add_end_variables()
        logger.debug(f"Number of variables: {self._m.getIntAttribute(IntAttribute.NUM_VARIABLES)}")

    def _add_timetabling_variables(self):
        for event in self._ean.get_events_network():
            self._pi[event] = self._m.addVariable(0, self._parameters.period_length - 1, VariableType.INTEGER,
                                                  name=f'pi_{event.get_event_id()}')
            if self._parameters.set_starting_timetable:
                self._m.setStartValue(self._pi[event], event.get_event_time())
        for activity in self._ean.get_all_activities():
            self._modulo_parameter[activity] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                                   name=f'z_{activity.get_activity_id()}')

    def _add_duration_variables(self):
        for line in self._line_pool.get_lines():
            self._duration[line] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                       name=f'duration_{line}')

    def _add_start_variables(self):
        for line in self._line_pool.get_lines():
            self._start[line] = {}
            for p in range(1, self._vehicle_schedule.get_drivings(line) + 1):
                self._start[line][p] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                           name=f'start({p},{line})')

    def _add_end_variables(self):
        for line in self._line_pool.get_lines():
            self._end[line] = {}
            for p in range(1, self._vehicle_schedule.get_drivings(line) + 1):
                self._end[line][p] = self._m.addVariable(var_type=VariableType.INTEGER,
                                                         name=f'end({p},{line})')

    def _initialize_objective_function(self) -> None:
        logger.debug("Initialize objective function")
        sum_travel_time = self._m.createExpression()
        sum_drive_time = self._m.createExpression()
        sum_wait_time = self._m.createExpression()
        sum_transfer_time = self._m.createExpression()

        for activity in self._ean.get_activities(['drive', 'wait', 'trans']):
            sum_travel_time.multiAdd(activity.get_n_passengers(), self._get_duration(activity))
            if activity.get_activity_type() == 'drive':
                sum_drive_time.multiAdd(activity.get_n_passengers(), self._get_duration(activity))
            elif activity.get_activity_type() == 'wait':
                sum_wait_time.multiAdd(activity.get_n_passengers(), self._get_duration(activity))
            elif activity.get_activity_type() == 'trans':
                sum_transfer_time.multiAdd(activity.get_n_passengers(), self._get_duration(activity))

        objective = self._m.createExpression()
        objective.multiAdd(self._parameters.factor_travel_time, sum_travel_time)
        objective.multiAdd(self._parameters.factor_drive_time, sum_drive_time)
        objective.multiAdd(self._parameters.factor_wait_time, sum_wait_time)
        objective.multiAdd(self._parameters.factor_transfer_time, sum_transfer_time)
        self._m.setObjective(objective, OptimizationSense.MINIMIZE)

    def _get_duration(self, activity: EanActivity) -> LinearExpression:
        duration = self._m.createExpression()
        duration.multiAdd(1, self._pi[activity.get_right_event()])
        duration.multiAdd(-1, self._pi[activity.get_left_event()])
        duration.multiAdd(self._parameters.period_length, self._modulo_parameter[activity])
        return duration

    def _create_constraints(self):
        logger.debug("Add constraints:")
        self._add_timetabling_constraints()
        self._add_duration_constraints()
        self._add_start_and_end_constraints()
        self._add_time_difference_constraints()

    def _add_timetabling_constraints(self):
        logger.debug("\ttimetabling")
        for activity in self._ean.get_all_activities():
            duration = self._get_duration(activity)
            self._m.addConstraint(duration, ConstraintSense.GREATER_EQUAL, activity.get_lower_bound(),
                                 f"l_{activity.get_activity_id()}")
            self._m.addConstraint(duration, ConstraintSense.LESS_EQUAL, activity.get_upper_bound(),
                                 f"u_{activity.get_activity_id()}")

    def _add_duration_constraints(self):
        logger.debug("\tduration of a line")
        duration_expression = self._m.createExpression()
        for line in self._line_pool.get_lines():
            duration_expression.clear()
            for activity in self._ean.get_activities_in_line(line):
                duration_expression.add(self._get_duration(activity))
            duration_expression.multiAdd(-1, self._duration[line])
            self._m.addConstraint(duration_expression, ConstraintSense.EQUAL, 0,
                                 f"duration_{line}")

    def _add_start_and_end_constraints(self):
        logger.debug("\tstart and end")
        lhs = self._m.createExpression()
        for line in self._line_pool.get_lines():
            for p in range(1, self._vehicle_schedule.get_drivings(line) + 1):
                lhs.clear()
                lhs.multiAdd(1, self._start[line][p])
                lhs.multiAdd(-1, self._pi[self._ean.get_first_event_in_line(line)])
                self._m.addConstraint(lhs, ConstraintSense.EQUAL, (p - 1) * self._parameters.period_length,
                                     f"start({p},{line})")
                lhs.clear()
                lhs.multiAdd(1, self._end[line][p])
                lhs.multiAdd(-1, self._pi[self._ean.get_first_event_in_line(line)])
                lhs.multiAdd(-1, self._duration[line])
                self._m.addConstraint(lhs, ConstraintSense.EQUAL, (p - 1) * self._parameters.period_length,
                                     f"end({p},{line})")

    def _add_time_difference_constraints(self):
        logger.debug("\tminimum time difference")
        lhs = self._m.createExpression()
        for connection in self._vehicle_schedule.get_connections():
            p_1 = connection.get_period_1()
            l_1 = connection.get_line_1()
            p_2 = connection.get_period_2()
            l_2 = connection.get_line_2()
            lhs.clear()
            lhs.multiAdd(1, self._start[l_2][p_2])
            lhs.multiAdd(-1, self._end[l_1][p_1])
            self._m.addConstraint(lhs, ConstraintSense.GREATER_EQUAL,
                                  self._turn_around_data.get_min_turnaround_time(l_1.get_last_stop(),
                                                                                l_2.get_first_stop()),
                                 f"L({l_1},{p_1},{l_2},{p_2})")

    def _write_lp_output(self):
        self._m.write("LinVehToTim.lp")

    def solve(self):
        logger.debug("Start optimization")
        self._m.solve()
        self.is_feasible = self._m.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0
        if not self.is_feasible:
            logger.debug("No feasible solution found")
            if self._m.getStatus() == Status.INFEASIBLE:
                self._m.computeIIS("LinVehToTim.ilp")
            raise AlgorithmStoppingCriterionException("Lin Veh To Tim")
        if self._m.getStatus() == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        logger.debug(f"Objective: {self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL)}")
        logger.debug("End optimization")

    def write_output(self):
        logger.debug("Start constructing output")
        pi_solution = {event: int(round(self._m.getValue(self._pi[event]))) for event in self._pi.keys()}
        duration_solution = {line: int(round(self._m.getValue(var))) for line, var in self._duration.items()}
        write_solver_statistic(self._parameters, self._m.getDoubleAttribute(DoubleAttribute.RUNTIME),
                               self._m.getDoubleAttribute(DoubleAttribute.MIP_GAP),
                               self._m.getDoubleAttribute(DoubleAttribute.OBJ_VAL))
        write_periodic_timetable(self._parameters, self._ean, pi_solution)
        for vehicle in self._vehicle_schedule.get_vehicles():
            vehicle.find_all_connections(self._vehicle_schedule.get_connections())
        logger.debug("Construct aperiodic ean:")
        aperiodic_ean = AperiodicEan(self._ean)
        aperiodic_ean.aperiodic_ean_from_vehicle_schedule(self._ean, self._vehicle_schedule, duration_solution,
                                                          pi_solution, self._parameters.period_length)
        aperiodic_ean.update_aperiodic_times(self._parameters.ean_earliest_time)
        write_aperiodic_ean(self._parameters, self._ean, aperiodic_ean)
        write_vehicle_schedule(self._parameters, self._ean, aperiodic_ean, self._vehicle_schedule, duration_solution,
                               self._parameters.period_length)
