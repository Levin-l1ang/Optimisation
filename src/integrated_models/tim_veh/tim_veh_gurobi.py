import logging

import gurobipy

from ean_data import *
from ean_data import Ean, AperiodicEan
from gurobi_helper import initialize_gurobi_model
from line_data import LinePool, Line
from tim_veh_helper import TimVehParameters
from vehicle_schedule import *
from vehicle_schedule import Trip, VehicleSchedule, Vehicle
from vs_helper import TurnaroundData

logger = logging.getLogger(__name__)


MINUTES_PER_HOUR = 60


class TimVehGurobiModel:

    def __init__(self, ean: Ean, line_pool: line_data.LinePool, turnaround_data: TurnaroundData,
                 parameters: TimVehParameters):
        self.ean = ean
        self.line_pool = line_pool
        self.turnaround_data = turnaround_data
        self.parameters = parameters
        # Model variables. Will be initialized/set later in self.create_model
        self.m = None
        self.is_feasible = False
        # Big m constraints
        self.m_1 = self.m_2 = self.m_3 = self.m_4 = self.m_5 = None
        # Variable dictionaries
        self.pi = {}
        self.modulo_parameter = {}
        self.vehicle_connect = {}
        self.vehicle_from_depot = {}
        self.vehicle_to_depot = {}
        self.duration = {}
        self.start = {}
        self.end = {}
        self.time_connection = {}
        #Constraints
        self.timetabling_constraints = []
        self.duration_constraints = []
        self.start_constraints = []
        self.end_constraints = []
        self.time_difference_constraints = []
        self.vehicle_flow_constraints = []
        self.linearization_turn_around_time_constraints = []
        self.sum_vehicles = 0

    def create_model(self):
        # Big M constraints
        self.set_big_m_values()
        # Initialize Model
        logger.debug("Initialize model")
        self.m = initialize_gurobi_model(self.parameters, "TimVeh")
        self.m.modelSense = gurobipy.GRB.MINIMIZE
        self.create_variables()
        logger.debug("Number of variables: " + str(self.m.getAttr(gurobipy.GRB.Attr.NumVars)))
        self.initialize_objective_function()
        self.add_constraints()
        if self.parameters.write_lp_output:
            self.write_lp_output()

    def set_big_m_values(self):
        max_upper_bound = self.ean.compute_max_upper_bound()
        max_n_edges_in_line = self.line_pool.get_max_n_edges_in_line()
        self.m_1 = self.parameters.period_length
        self.m_2 = self.parameters.period_length
        self.m_3 = self.parameters.p_max * self.parameters.period_length + max_n_edges_in_line * max_upper_bound
        self.m_4 = max_upper_bound
        self.m_5 = self.parameters.p_max * self.parameters.period_length + max_n_edges_in_line * max_upper_bound
        logger.debug("m_1=%d\nm_2=%d\nm_3=%d\nm_4=%d\nm_5=%d" % (self.m_1, self.m_2, self.m_3, self.m_4, self.m_5))

    def create_variables(self):
        logger.debug("Initialize variables")
        # pi
        for event in self.ean.get_events_network():
            self.pi[event] = self.m.addVar(0, self.parameters.period_length - 1,
                                           vtype=gurobipy.GRB.INTEGER,
                                           name='pi_%s' % event.to_string())

        # modulo parameter
        for activity in self.ean.get_all_activities():
            self.modulo_parameter[activity] = self.m.addVar(vtype=gurobipy.GRB.INTEGER,
                                                            name='z_%s' % activity.to_string())

        # Vehicle Connect
        for p_1 in range(1, self.parameters.p_max + 1):
            self.vehicle_connect[p_1] = {}
            for l_1 in self.line_pool.get_lines():
                self.vehicle_connect[p_1][l_1] = {}
                for p_2 in range(1, self.parameters.p_max + 1):
                    self.vehicle_connect[p_1][l_1][p_2] = {}
                    for l_2 in self.line_pool.get_lines():
                        self.vehicle_connect[p_1][l_1][p_2][l_2] = self.m.addVar(vtype=gurobipy.GRB.BINARY,
                                                                                 name='x_(%d,%s)(%d,%s)'
                                                                                      % (p_1, l_1.to_string(),
                                                                                         p_2, l_2.to_string()))

        # Vehicle from depot
        for p in range(1, self.parameters.p_max + 1):
            self.vehicle_from_depot[p] = {}
            for line in self.line_pool.get_lines():
                self.vehicle_from_depot[p][line] = self.m.addVar(vtype=gurobipy.GRB.BINARY,
                                                       name='x_depot,(%d,%s)' % (p, line.to_string()))

        # Vehicle to depot
        for p in range(1, self.parameters.p_max + 1):
            self.vehicle_to_depot[p] = {}
            for line in self.line_pool.get_lines():
                self.vehicle_to_depot[p][line] = self.m.addVar(vtype=gurobipy.GRB.BINARY,
                                                     name='x_(%d,%s),depot' % (p, line.to_string()))

        # Duration
        for line in self.line_pool.get_lines():
            self.duration[line] = self.m.addVar(vtype=gurobipy.GRB.INTEGER, name='duration_%s' % line.to_string())

        # Start
        for p in range(1, self.parameters.p_max + 1):
            self.start[p] = {}
            for line in self.line_pool.get_lines():
                self.start[p][line] = self.m.addVar(vtype=gurobipy.GRB.INTEGER,
                                                    name='start(%d,%s)' % (p, line.to_string()))

        # End
        for p in range(1, self.parameters.p_max + 1):
            self.end[p] = {}
            for line in self.line_pool.get_lines():
                self.end[p][line] = self.m.addVar(vtype=gurobipy.GRB.INTEGER, name='end(%d,%s)' % (p, line.to_string()))

        # Time Connection
        if self.parameters.factor_turn_around_time != 0:
            for p_1 in range(1, self.parameters.p_max + 1):
                self.time_connection[p_1] = {}
                for l_1 in self.line_pool.get_lines():
                    self.time_connection[p_1][l_1] = {}
                    for p_2 in range(1, self.parameters.p_max + 1):
                        self.time_connection[p_1][l_1][p_2] = {}
                        for l_2 in self.line_pool.get_lines():
                            if self.parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                                self.time_connection[p_1][l_1][p_2][l_2] = self.m.addVar(vtype=gurobipy.GRB.INTEGER,
                                                                                         name='y_(%d,%s)(%d,%s)'
                                                                                              % (p_1, l_1.to_string(),
                                                                                                 p_2, l_2.to_string()))
        self.m.update()

    def initialize_objective_function(self):
        logger.debug("Initialize objective function")
        sum_travel_time = 0
        sum_vehicle_time_full = 0
        sum_vehicle_distance_full = 0
        sum_turn_around_time = 0
        sum_turn_around_distance = 0

        for activity in self.ean.get_activities(['drive', 'wait', 'trans']):
            sum_travel_time += activity.get_n_passengers() * (
                self.pi[activity.get_right_event()] - self.pi[activity.get_left_event()]
                + self.modulo_parameter[activity] * self.parameters.period_length)

        for line in self.line_pool.get_lines():
            sum_vehicle_distance_full += line.compute_length_from_ptn()
            sum_vehicle_time_full += self.duration[line] / MINUTES_PER_HOUR

        for p_1 in range(1, self.parameters.p_max + 1):
            for l_1 in self.line_pool.get_lines():
                if self.parameters.factor_turn_around_time != 0:
                    for p_2 in range(1, self.parameters.p_max + 1):
                        for l_2 in self.line_pool.get_lines():
                            if self.parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                                sum_turn_around_time += self.time_connection[p_1][l_1][p_2][l_2] / MINUTES_PER_HOUR
                                sum_turn_around_distance += self.vehicle_connect[p_1][l_1][p_2][l_2] * \
                                                            self.turnaround_data\
                                                                .get_min_turnaround_distance(l_1.get_last_stop(),
                                                                                             l_2.get_first_stop())
                    sum_turn_around_time += self.vehicle_from_depot[p_1][l_1] * self.turnaround_data.get_min_from_depot_time(l_1.get_first_stop()) / MINUTES_PER_HOUR
                    sum_turn_around_time += self.vehicle_to_depot[p_1][l_1] * self.turnaround_data. \
                        get_min_to_depot_time(l_1.get_last_stop()) / MINUTES_PER_HOUR
                    sum_turn_around_distance += self.vehicle_from_depot[p_1][l_1] * self.turnaround_data. \
                        get_min_from_depot_distance(l_1.get_first_stop())
                    sum_turn_around_distance += self.vehicle_to_depot[p_1][l_1] * self.turnaround_data. \
                        get_min_to_depot_distance(l_1.get_last_stop())
                self.sum_vehicles += self.vehicle_from_depot[p_1][l_1]

        self.m.setObjective(self.parameters.factor_travel_time * sum_travel_time +
                       self.parameters.factor_drive_time_unweighted * sum_vehicle_time_full +
                       self.parameters.factor_line_length * sum_vehicle_distance_full +
                       self.parameters.factor_turn_around_time * sum_turn_around_time +
                       self.parameters.factor_turn_around_distance * sum_turn_around_distance +
                       self.parameters.factor_vehicles * self.sum_vehicles)

    def add_constraints(self):
        logger.debug("Add constraints:")
        self.add_timetabling_constraints()
        self.add_duration_constraints()
        self.add_start_and_end_constraints()
        self.add_minimum_time_difference_constraints()
        self.add_vehicle_flow_constraints()
        if self.parameters.factor_turn_around_time != 0:
            self.add_turnaround_time_constraints()
        if self.parameters.use_lower_bound:
            self.add_lower_bound_constraint()

    def add_timetabling_constraints(self):
        logger.debug("\ttimetabling")
        for activity in self.ean.get_all_activities():
            i = activity.get_left_event()
            j = activity.get_right_event()
            const_1 = self.m.addConstr(self.pi[j] - self.pi[i] + self.modulo_parameter[activity] *
                                       self.parameters.period_length >= activity.get_lower_bound())
            const_2 = self.m.addConstr(self.pi[j] - self.pi[i] + self.modulo_parameter[activity] *
                                  self.parameters.period_length <= activity.get_upper_bound())
            self.timetabling_constraints.append(const_1)
            self.timetabling_constraints.append(const_2)

    def add_duration_constraints(self):
        logging.debug("\tduration of a line")
        for line in self.line_pool.get_lines():
            expr_sum = 0
            for activity in self.ean.get_activities_in_line(line):
                i = activity.get_left_event()
                j = activity.get_right_event()
                expr_sum += self.pi[j] - self.pi[i] + self.modulo_parameter[activity] * self.parameters.period_length
            const = self.m.addConstr(expr_sum == self.duration[line], name=f"dur_constr_{line}")
            self.duration_constraints.append(const)

    def add_start_and_end_constraints(self):
        logging.debug("\tstart and end")
        for p in range(1, self.parameters.p_max + 1):
            for line in self.line_pool.get_lines():
                const_1 = self.m.addConstr(self.start[p][line] == p * self.parameters.period_length +
                                           self.pi[self.ean.get_first_event_in_line(line)])
                const_2 = self.m.addConstr(self.end[p][line] == p * self.parameters.period_length +
                                           self.pi[self.ean.get_first_event_in_line(line)] + self.duration[line])
                self.start_constraints.append(const_1)
                self.end_constraints.append(const_2)

    def add_minimum_time_difference_constraints(self):
        logger.debug("\tminimum time difference")
        for p_1 in range(1, self.parameters.p_max + 1):
            for l_1 in self.line_pool.get_lines():
                for p_2 in range(1, self.parameters.p_max + 1):
                    for l_2 in self.line_pool.get_lines():
                        if self.parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            const = self.m.addConstr(self.start[p_2][l_2] - self.end[p_1][l_1] >=
                                                     self.vehicle_connect[p_1][l_1][p_2][l_2] *
                                                     self.turnaround_data.get_min_turnaround_time(l_1.get_last_stop(),
                                                                                                  l_2.get_first_stop())
                                                     - (1 - self.vehicle_connect[p_1][l_1][p_2][l_2]) * self.m_3)
                            self.time_difference_constraints.append(const)

    def add_vehicle_flow_constraints(self):
        self.add_outgoing_vehicle_flow_constraints()
        self.add_incoming_vehicle_flow_constraints()

    def add_incoming_vehicle_flow_constraints(self):
        logger.debug("\tIncoming vehicle flow")
        for p_2 in range(1, self.parameters.p_max + 1):
            for l_2 in self.line_pool.get_lines():
                expr_sum = self.vehicle_from_depot[p_2][l_2]
                for p_1 in range(1, self.parameters.p_max + 1):
                    for l_1 in self.line_pool.get_lines():
                        if self.parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            expr_sum += self.vehicle_connect[p_1][l_1][p_2][l_2]
                const = self.m.addConstr(expr_sum == 1)
                self.vehicle_flow_constraints.append(const)

    def add_outgoing_vehicle_flow_constraints(self):
        logger.debug("\tOutgoing vehicle flow")
        for p_1 in range(1, self.parameters.p_max + 1):
            for l_1 in self.line_pool.get_lines():
                expr_sum = self.vehicle_to_depot[p_1][l_1]
                for p_2 in range(1, self.parameters.p_max + 1):
                    for l_2 in self.line_pool.get_lines():
                        if self.parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            expr_sum += self.vehicle_connect[p_1][l_1][p_2][l_2]
                const = self.m.addConstr(expr_sum == 1)
                self.vehicle_flow_constraints.append(const)

    def add_turnaround_time_constraints(self):
        logger.debug("\tlinearization turn around time")
        for p_1 in range(1, self.parameters.p_max + 1):
            for l_1 in self.line_pool.get_lines():
                for p_2 in range(1, self.parameters.p_max + 1):
                    for l_2 in self.line_pool.get_lines():
                        if self.parameters.vs_allow_empty_trips or l_1.get_last_stop() == l_2.get_first_stop():
                            const_1 = self.m.addConstr(self.time_connection[p_1][l_1][p_2][l_2] >= 0)
                            const_2 = self.m.addConstr(self.time_connection[p_1][l_1][p_2][l_2] >=
                                                       self.start[p_2][l_2] - self.end[p_1][l_1]
                                                       - self.m_5 * (1 - self.vehicle_connect[p_1][l_1][p_2][l_2]))
                            self.linearization_turn_around_time_constraints.append(const_1)
                            self.linearization_turn_around_time_constraints.append(const_2)

    def add_lower_bound_constraint(self):
        lower_bound_duration = sum([a.get_lower_bound() for a in self.ean.get_activities(['drive', 'wait'])])
        self.m.addConstr(self.sum_vehicles >= math.floor(lower_bound_duration / self.parameters.period_length),
                         "sum_vehicles")

    def write_lp_output(self):
        self.m.update()
        self.m.write("TimVeh.lp")
        dec_file = open('TimVeh.dec', 'w')
        dec_file.write("PRESOLVED\n0\n")
        dec_file.write("NBLOCKS\n2\n")
        dec_file.write("BLOCK 1\n")
        for const in self.timetabling_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in self.duration_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in self.start_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in self.end_constraints:
            dec_file.write(const.ConstrName + "\n")
        dec_file.write("BLOCK 2\n")
        for const in self.vehicle_flow_constraints:
            dec_file.write(const.ConstrName + "\n")
        dec_file.write("MASTERCONSS\n")
        for const in self.time_difference_constraints:
            dec_file.write(const.ConstrName + "\n")
        if self.parameters.factor_turn_around_time != 0:
            for const in self.linearization_turn_around_time_constraints:
                dec_file.write(const.ConstrName + "\n")
        dec_file.close()

    def solve(self):
        logger.debug("Start optimization")
        self.m.optimize()
        logger.debug("End optimization")
        self.is_feasible = self.m.solcount > 0

    def write_output(self):
        self.write_solver_statistic()
        self.write_periodic_timetable()
        vehicle_schedule = self.construct_vehicle_schedule()
        aperiodic_ean = self.construct_aperiodic_ean(vehicle_schedule)
        self.write_aperiodic_ean(aperiodic_ean)
        self.write_vehicle_schedule(aperiodic_ean, vehicle_schedule)

    def write_vehicle_schedule(self, aperiodic_ean, vehicle_schedule):
        vehicle_file = open(self.parameters.vehicle_file_name, 'w')
        vehicle_file.write("#" + self.parameters.vehicle_schedule_header + "\n")
        trip_file = open(self.parameters.trip_file_name, 'w')
        trip_file.write("#" + self.parameters.trip_header + "\n")
        end_events_file = open(self.parameters.end_events_file_name, 'w')
        end_events_file.write("# event-id\n")
        circ_id = 1
        # get first event to save for last empty trip
        last_trip = Trip()
        vehicle = vehicle_schedule.get_vehicles()[0]
        last_trip.set_end(circ_id, vehicle.get_start_line(), vehicle.get_start_period(), self.parameters.period_length,
                          self.ean, aperiodic_ean)
        trip = Trip()
        first = True
        vehicle_swap_trip = Trip()
        duration_solution = {line: int(round(var.x)) for line, var in self.duration.items()}
        for vehicle in vehicle_schedule.get_vehicles():
            if not first:
                vehicle_swap_trip.set_end(circ_id, vehicle.get_start_line(), vehicle.get_start_period(),
                                          self.parameters.period_length, self.ean, aperiodic_ean)
                # vehicle_file.write(vehicle_swap_trip.to_csv() + "\n")
            first = False
            trip_id = 1
            vehicle_id = vehicle.get_vehicle_id()
            # First Trip:
            # output trip
            trip.trip(circ_id, trip_id, vehicle_id, vehicle.get_start_line(), vehicle.get_start_period(),
                      self.parameters.period_length, self.ean, aperiodic_ean, duration_solution)
            vehicle_file.write(trip.to_csv() + "\n")
            trip_file.write(trip.to_csv_trip() + "\n")
            end_events_file.write(trip.to_csv_end_events() + "\n")
            for connection in vehicle.get_connections():
                # output empty trip
                trip_id += 1
                trip.empty_trip(circ_id, trip_id, vehicle_id, connection, self.parameters.period_length, self.ean,
                                aperiodic_ean, duration_solution)
                vehicle_file.write(trip.to_csv() + "\n")
                # output trip
                trip_id += 1
                trip.trip(circ_id, trip_id, vehicle_id, connection.get_line_2(), connection.get_period_2(),
                          self.parameters.period_length, self.ean, aperiodic_ean, duration_solution)
                vehicle_file.write(trip.to_csv() + "\n")
                trip_file.write(trip.to_csv_trip() + "\n")
                end_events_file.write(trip.to_csv_end_events() + "\n")
            # set vehicle swap trip
            trip_id += 1
            vehicle_swap_trip.set_start(trip_id, vehicle.get_vehicle_id(), vehicle.get_last_line(),
                                    vehicle.get_last_period(), self.parameters.period_length, self.ean, aperiodic_ean,
                                    duration_solution)
        # set end of circulation
        vehicle = vehicle_schedule.get_vehicles()[-1]
        last_trip.set_start(trip_id, vehicle.get_vehicle_id(), vehicle.get_last_line(), vehicle.get_last_period(),
                            self.parameters.period_length, self.ean, aperiodic_ean, duration_solution)
        # vehicle_file.write(last_trip.to_csv())
        vehicle_file.close()
        trip_file.close()
        end_events_file.close()

    def write_aperiodic_ean(self, aperiodic_ean):
        events_expanded_file = open(self.parameters.event_expanded_file_name, 'w')
        events_expanded_file.write("#" + self.parameters.aperiodic_event_header + "\n")
        for event in self.ean.get_all_events():
            for aperiodic_event in aperiodic_ean.get_aperiodic_events_by_periodic_event(event):
                events_expanded_file.write(aperiodic_event.to_events_expanded() + "\n")
        events_expanded_file.close()
        timetable_expanded_file = open(self.parameters.timetable_expanded_file_name, 'w')
        timetable_expanded_file.write("#" + self.parameters.aperiodic_timetable_header + "\n")
        for event in self.ean.get_all_events():
            for aperiodic_event in aperiodic_ean.get_aperiodic_events_by_periodic_event(event):
                timetable_expanded_file.write(aperiodic_event.to_timetable_expanded() + "\n")
        timetable_expanded_file.close()
        # print("Durations:")
        # for line in line_pool.get_lines():
        #     print("line " + line.to_string() + ", duration " + str(duration[line].x))
        activities_expanded_file = open(self.parameters.activities_expanded_file_name, 'w')
        activities_expanded_file.write("#" + self.parameters.aperiodic_activity_header + "\n")
        for activity in self.ean.get_activities(['drive', 'wait', 'sync']):
            for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
                activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")
        for activity in self.ean.get_activities(['trans']):
            for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
                activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")
        for activity in self.ean.get_activities(['headway']):
            for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
                activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")
        activities_expanded_file.close()

    def construct_aperiodic_ean(self, vehicle_schedule):
        logger.debug("Construct aperiodic ean")
        aperiodic_ean = AperiodicEan(self.ean)
        duration_solution = {line: int(round(var.x)) for line, var in self.duration.items()}
        pi_solution = {event: int(round(var.x)) for event, var in self.pi.items()}
        aperiodic_ean.aperiodic_ean_from_vehicle_schedule(self.ean, vehicle_schedule, duration_solution, pi_solution,
                                                          self.parameters.period_length)
        aperiodic_ean.update_aperiodic_times(self.parameters.ean_earliest_time)
        return aperiodic_ean

    def construct_vehicle_schedule(self):
        logger.debug("Construct vehicle schedule")
        vehicle_schedule = VehicleSchedule(self.line_pool)
        connections_solution = {p_1: {l_1: {p_2: {l_2: int(round(var.x))
                                                  for l_2, var in self.vehicle_connect[p_1][l_1][p_2].items()}
                                            for p_2 in self.vehicle_connect[p_1][l_1].keys()}
                                      for l_1 in self.vehicle_connect[p_1].keys()}
                                for p_1 in self.vehicle_connect.keys()}
        vehicle_schedule.add_connections_from_ip_model(connections_solution, self.parameters.p_max, self.line_pool,
                                                       self.parameters.vs_allow_empty_trips)
        # print("Connections: " + str(len(vehicle_schedule.get_connections())))
        for p_1 in range(1, self.parameters.p_max + 1):
            for l_1 in self.line_pool.get_lines():
                if self.vehicle_from_depot[p_1][l_1].x == 1:
                    vehicle_schedule.add_vehicle(Vehicle(vehicle_schedule, p_1, l_1))
        for vehicle in vehicle_schedule.get_vehicles():
            vehicle.find_all_connections(vehicle_schedule.get_connections())
        return vehicle_schedule

    def write_solver_statistic(self):
        gap = 'gap; ' + str(self.m.MIPGap)
        solver_time = 'solver_time; ' + str(self.m.Runtime)
        a = open(self.parameters.solver_statistic_file_name, 'w')
        a.write(solver_time + '\n')
        a.write(gap + '\n')
        a.close()

    def write_periodic_timetable(self):
        timetable_file = open(self.parameters.periodic_timetable_filename, 'w')
        timetable_file.write("#" + self.parameters.periodic_timetable_header + "\n")
        for event in self.ean.get_events_network():
            timetable_file.write(f"{event.get_event_id()}; {round(self.pi[event].x)}\n")
        timetable_file.close()