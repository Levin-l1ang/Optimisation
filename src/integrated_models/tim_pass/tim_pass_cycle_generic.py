from collections import defaultdict

import ean_data
from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.exceptions.exceptions import LinTimException
from core.solver.generic_solver_interface import Variable, VariableType, ConstraintSense, Status, IntAttribute
from cycle_base import *
import math

from tim_pass_generic import TimPassGenericModel
from tim_pass_helper import TimPassParameters
from write_csv import write_periodic_timetable

logger = logging.getLogger(__name__)


class CycleTimPassGenericModel(TimPassGenericModel):

    def __init__(self, ean: ean_data.Ean, ptn: ptn_data.Ptn, line_pool: line_data.LinePool, od: od_data.OD,
                 parameters: TimPassParameters):
        super().__init__(ean, ptn, line_pool, od, parameters)
        self.tension = {}  # type: Dict[EanActivity, Variable]
        self.cb = None  # type: Union[Dict[EanActivity, Dict[EanActivity, int]], None]

    def _create_timetable_variables(self):
        logger.debug("Calculating cycle-base")
        self.cb = cycle_base(self._ean)
        logger.debug("Done")

        logger.debug("\ttension")
        for activity in self._activities_drive_wait_trans_sync:
            self.tension[activity] = self._m.addVariable(activity.lower_bound, activity.upper_bound,
                                                         VariableType.INTEGER, 0,
                                                         f'tension_{activity.get_activity_id()}')

    def _create_modulo_variables(self):
        logger.debug("\tmodulo parameter")
        for non_tree_activity in self.cb:
            lb = 0
            ub = 0
            for activity, orientation in self.cb[non_tree_activity]:
                if orientation == 1:
                    ub += activity.get_upper_bound()
                    lb += activity.get_lower_bound()
                else:
                    ub -= activity.get_lower_bound()
                    lb -= activity.get_upper_bound()
            ub = math.floor(ub / (self._parameters.period_length * 1.0))
            lb = math.ceil(lb / (self._parameters.period_length * 1.0))
            self._modulo_parameter[non_tree_activity] = self._m.addVariable(lb, ub, VariableType.INTEGER, 0,
                                                                          f'z_{non_tree_activity.get_activity_id()}')

    def _get_duration(self, activity):
        expr = self._m.createExpression()
        expr.add(self.tension[activity])
        return expr

    def _create_timetabling_constraints(self):
        logger.debug("\ttimetabling")
        lhs = self._m.createExpression()
        for non_tree_activity in self.cb:
            lhs.clear()
            for activity, orientation in self.cb[non_tree_activity]:
                lhs.multiAdd(orientation, self.tension[activity])
            lhs.multiAdd(-self._parameters.period_length, self._modulo_parameter[non_tree_activity])
            constraint = self._m.addConstraint(lhs, ConstraintSense.EQUAL, 0,
                                          f'cycle_constraint_{non_tree_activity.get_activity_id()}')
            if self._parameters.write_lp_output:
                self._timetabling_constraints.append(constraint)

    def _create_time_slice_constraints(self):
        if self._parameters.global_n_time_slices > 1:
            logger.error("Tim Pass Cycle base formulation does not support multiple time slices!")
            raise LinTimException("Invalid configuration for tim pass formulation")

    def _create_linearization_constraints(self):
        logger.debug("\tlinearization travel time")
        lhs = self._m.createExpression()
        for activity in self._activities_drive_wait_trans_restricted:
            for od_pair in self._od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if activity.get_lower_bound() != activity.get_upper_bound():
                        const_1 = self._m.addConstraint(self._travel_time_linear[activity][od_pair][t], ConstraintSense.GREATER_EQUAL, 0,
                                                        name=f"linearization_1_{od_pair}_"
                                                             f"{t}_{activity.get_activity_id()}")
                        const_2 = self._m.addConstraint(self._travel_time_linear[activity][od_pair][t] - self.tension[activity]
                                                        - self._m_4 * self._arc_used[activity][od_pair][t],
                                                        ConstraintSense.GREATER_EQUAL, -self._m_4,
                                                        name=f"linearization_2_{od_pair}_"
                                                             f"{t}_{activity.get_activity_id()}")
                        if self._parameters.write_lp_output:
                            self._linearization_travel_time_constraints.append(const_1)
                            self._linearization_travel_time_constraints.append(const_2)
                    else:
                        lhs.clear()
                        lhs.add(self._travel_time_linear[activity][od_pair][t])
                        lhs.multiAdd(-activity.get_lower_bound(), self._arc_used[activity][od_pair][t])
                        const = self._m.addConstraint(lhs, ConstraintSense.EQUAL, 0,
                                                      name=f"linearization_3_{od_pair}_"
                                                           f"{t}_{activity.get_activity_id()}")
                        if self._parameters.write_lp_output:
                            self._linearization_travel_time_constraints.append(const)

    def solve(self):
        logger.debug("Start optimization")
        self._m.solve()
        logger.debug("End optimization")
        self.is_feasible = self._m.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0
        if not self.is_feasible:
            logger.debug("No feasible solution found")
            if self._m.getStatus() == Status.INFEASIBLE:
                self._m.computeIIS("TimPassCycle.ilp")
            raise AlgorithmStoppingCriterionException("Tim Pass")
        if self._m.getStatus() == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        logger.debug("End optimization")

    def _write_timetable(self):
        tension_solution = {}
        for activity in self.tension:
            tension_solution[activity] = int(round(self._m.getValue(self.tension[activity])))
            if tension_solution[activity] < activity.get_lower_bound() or \
                    tension_solution[activity] > activity.get_upper_bound():
                logger.warning(f"Tension does not match bounds: {activity}")
        pi = self._reconstruct_times_from_tensions(tension_solution)
        write_periodic_timetable(self._parameters, self._ean, pi)

    def _reconstruct_times_from_tensions(self, tension_solution):
        pi = {}
        used = defaultdict(lambda: False)
        for event in self._ean.get_events_network():
            if used[event]:
                continue
            pi[event] = 0
            used[event] = True
            queue = [event]
            while len(queue) > 0:
                cur = queue.pop(0)
                for activity in cur.get_incident_activities():
                    if activity in self._activities_drive_wait_trans_sync:
                        if cur == activity.get_left_event():
                            if not used[activity.right_event]:
                                queue.append(activity.get_right_event())
                                used[activity.get_right_event()] = True
                                pi[activity.get_right_event()] = (pi[cur] + tension_solution[activity]) % \
                                                           self._parameters.period_length
                        elif cur == activity.get_right_event():
                            if not used[activity.get_left_event()]:
                                queue.append(activity.get_left_event())
                                used[activity.get_left_event()] = True
                                pi[activity.get_left_event()] = (pi[cur] - tension_solution[activity]) % \
                                                          self._parameters.period_length
        return pi
