import logging
from collections import defaultdict
import gurobipy

from gurobipy.gurobipy import Var

import ean_data
from cycle_base import *
import math

from tim_pass_gurobi import TimPassGurobiModel
from tim_pass_helper import TimPassParameters

logger = logging.getLogger(__name__)


class CycleTimPassGurobiModel(TimPassGurobiModel):

    def __init__(self, ean: ean_data.Ean, ptn: ptn_data.Ptn, line_pool: line_data.LinePool, od: od_data.OD,
                 parameters: TimPassParameters):
        super().__init__(ean, ptn, line_pool, od, parameters)
        self.tension = {}  # type: Dict[EanActivity, Var]
        self.cb = None  # type: Union[Dict[EanActivity, Dict[EanActivity, int]], None]

    def create_timetable_variables(self):
        logger.debug("Calculating cycle-base")
        self.cb = cycle_base(self.ean)
        logger.debug("Done")

        logger.debug("\ttension")
        for activity in self.activities_drive_wait_trans_sync:
            self.tension[activity] = self.m.addVar(vtype=gurobipy.GRB.INTEGER,
                                                   name=f'tension_{activity.get_activity_id()}',
                                                   lb=activity.lower_bound, ub=activity.upper_bound)

    def create_modulo_variables(self):
        logger.debug("\tmodulo parameter")
        for non_tree_activity in self.cb:
            lb = 0
            ub = 0
            for activity, orientation in self.cb[non_tree_activity]:
                if orientation == 1:
                    ub += activity.upper_bound
                    lb += activity.lower_bound
                else:
                    ub -= activity.lower_bound
                    lb -= activity.upper_bound
            ub = math.floor(ub / (self.parameters.period_length * 1.0))
            lb = math.ceil(lb / (self.parameters.period_length * 1.0))
            self.modulo_parameter[non_tree_activity] = self.m.addVar(lb=lb, ub=ub, vtype=gurobipy.GRB.INTEGER,
                                                                     name=f'z_{non_tree_activity}')

    def get_duration(self, activity):
        return self.tension[activity]

    def create_timetabling_constraints(self):
        logger.debug("\ttimetabling")
        for non_tree_activity in self.cb:
            con = gurobipy.LinExpr()
            for activity, orientation in self.cb[non_tree_activity]:
                con += self.tension[activity] * orientation
            constraint = self.m.addConstr(con == self.parameters.period_length * self.modulo_parameter[non_tree_activity],
                                          name=f'cycle_constraint_{non_tree_activity}')
            if self.parameters._write_lp_output:
                self.timetabling_constraints.append(constraint)

    def create_time_slice_constraints(self):
        logger.debug("\ttime slice")
        for activity in self.activities_drive_wait_trans_restricted:
            for od_pair in self.od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if activity.get_lower_bound() != activity.get_upper_bound():
                        const_1 = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] >= 0,
                                                   name=f"linearization_1_{od_pair}_{t}_{activity}")
                        const_2 = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] >=
                                                   self.tension[activity] - (1 - self.arc_used[activity][od_pair][t])
                                                   * self.m_4,
                                                   name=f"linearization_2_{od_pair}_{t}_{activity}")
                        if self.parameters._write_lp_output:
                            self.linearization_travel_time_constraints.append(const_1)
                            self.linearization_travel_time_constraints.append(const_2)
                    else:
                        const = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] ==
                                                 activity.get_lower_bound() * self.arc_used[activity][od_pair][t],
                                                 name=f"linearization_3_{od_pair}_{t}_{activity}")
                        if self.parameters._write_lp_output:
                            self.linearization_travel_time_constraints.append(const)

    def create_linearization_constraints(self):
        logger.debug("\tlinearization travel time")
        for activity in self.activities_drive_wait_trans_restricted:
            for od_pair in self.od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if activity.get_lower_bound() != activity.get_upper_bound():
                        const_1 = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] >= 0,
                                                   name=f"linearization_1_{od_pair}_{t}_{activity}")
                        const_2 = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] >=
                                                   self.tension[activity] - (1 - self.arc_used[activity][od_pair][t])
                                                   * self.m_4,
                                                   name=f"linearization_2_{od_pair}_{t}_{activity}")
                        if self.parameters._write_lp_output:
                            self.linearization_travel_time_constraints.append(const_1)
                            self.linearization_travel_time_constraints.append(const_2)
                    else:
                        const = self.m.addConstr(self.travel_time_linear[activity][od_pair][t] ==
                                                 activity.get_lower_bound() * self.arc_used[activity][od_pair][t],
                                                 name=f"linearization_3_{od_pair}_{t}_{activity}")
                        if self.parameters._write_lp_output:
                            self.linearization_travel_time_constraints.append(const)

    def solve(self):
        logger.debug("Start optimization")
        self.m.optimize()
        logger.debug("End optimization")
        self.is_feasible = self.m.SolCount != 0

    def write_timetable(self):
        logger.debug("Print timetable")

        tension_solution = {}
        for activity in self.tension:
            tension_solution[activity] = int(round(self.tension[activity].x))
            if tension_solution[activity] < activity.get_lower_bound() or \
                    tension_solution[activity] > activity.get_upper_bound():
                logger.warning(f"Tension does not match bounds: {activity}")
        pi = {}
        used = defaultdict(lambda: False)

        for event in self.ean.get_events_network():
            if used[event]:
                continue
            pi[event] = 0
            used[event] = True
            queue = [event]
            while len(queue) > 0:
                cur = queue.pop(0)
                for activity in cur.get_incident_activities():
                    if activity in self.activities_drive_wait_trans_sync:
                        if cur == activity.left_event:
                            if not used[activity.right_event]:
                                queue.append(activity.right_event)
                                used[activity.right_event] = True
                                pi[activity.right_event] = (pi[cur] + tension_solution[activity]) % \
                                                           self.parameters.period_length
                        elif cur == activity.right_event:
                            if not used[activity.left_event]:
                                queue.append(activity.left_event)
                                used[activity.left_event] = True
                                pi[activity.left_event] = (pi[cur] - tension_solution[activity]) % \
                                                          self.parameters.period_length

        timetable_file = open(self.parameters.periodic_timetable_filename, 'w')
        timetable_file.write("# event_index; time\n")
        for event in self.ean.get_events_network():
            timetable_file.write(f"{event.get_event_id()}; {pi[event]}\n")
        timetable_file.close()
