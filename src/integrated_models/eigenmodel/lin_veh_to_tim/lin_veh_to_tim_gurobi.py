import gurobipy
from ean_data import *
from lin_veh_to_tim_helper import LinVehToTimParameters
from vehicle_schedule import *
from vs_helper import TurnaroundData

def solve(parameters: LinVehToTimParameters, ean: Ean, line_pool: line_data.LinePool,
          vehicle_schedule: VehicleSchedule, turn_around_data: TurnaroundData) -> None:
    # Dictionaries for storing variables
    pi = {}
    modulo_parameter = {}
    duration = {}
    start = {}
    end = {}

    # Initialize Model
    print("Initialize model")
    m = gurobipy.Model("LinVehToTim")
    if parameters.time_limit > 0:
        m.params.timeLimit = parameters.time_limit
    if parameters.mip_gap >= 0:
        m.params.MIPGap = parameters.mip_gap
    m.modelSense = gurobipy.GRB.MINIMIZE
    # gurobipy.GRB.Attr.ModelSense = +1

    # Initialize Variables
    print("Initialize variables")

    # pi
    for event in ean.get_events_network():
        pi[event] = m.addVar(0, parameters.period_length - 1, vtype=gurobipy.GRB.INTEGER, name='pi_%s' % event.get_event_id())

    # modulo parameter
    for activity in ean.get_all_activities():
        modulo_parameter[activity] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='z_%s' % activity.get_activity_id())

    # Duration
    for line in line_pool.get_lines():
        duration[line] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='duration_%s' % line.get_directed_line_id())

    # Start
    for line in line_pool.get_lines():
        start[line] = {}
        for p in range(1, vehicle_schedule.get_drivings(line) + 1):
            start[line][p] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='start(%d,%s)' % (p, line.get_directed_line_id()))

    # End
    for line in line_pool.get_lines():
        end[line] = {}
        for p in range(1, vehicle_schedule.get_drivings(line) + 1):
            end[line][p] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='end(%d,%s)' % (p, line.get_directed_line_id()))

    # Add variables to model m
    m.update()
    print("Number of variables: " + str(m.getAttr(gurobipy.GRB.Attr.NumVars)))

    # objective function
    print("Initialize objective function")
    sum_travel_time = 0

    for activity in ean.get_activities(['drive', 'wait', 'trans']):
        sum_travel_time += activity.get_n_passengers() * (
            pi[activity.get_right_event()] - pi[activity.get_left_event()] + modulo_parameter[activity] * parameters.period_length)

    m.setObjective(sum_travel_time)

    # m.update()

    # Add constraints
    print("Add constraints:")
    # timetabling constraints
    print("\ttimetabling")
    for activity in ean.get_all_activities():
        i = activity.get_left_event()
        j = activity.get_right_event()
        m.addConstr(pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length >= activity.get_lower_bound(),
                    "l_" + str(activity.get_activity_id()))
        m.addConstr(pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length <= activity.get_upper_bound(),
                    "u_" + str(activity.get_activity_id()))

    # Duration
    print("\tduration of a line")
    for line in line_pool.get_lines():
        expr_sum = 0
        for activity in ean.get_activities_in_line(line):
            i = activity.get_left_event()
            j = activity.get_right_event()
            expr_sum += pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length
        m.addConstr(expr_sum == duration[line], "duration_" + str(line.get_directed_line_id()))

    # Start and End
    print("\tstart and end")
    for line in line_pool.get_lines():
        for p in range(1, vehicle_schedule.get_drivings(line) + 1):
            m.addConstr(start[line][p] == (p - 1) * parameters.period_length + pi[ean.get_first_event_in_line(line)],
                        "start(" + str(p) + "," + str(line.get_directed_line_id()) + ")")
            m.addConstr(
                end[line][p] == (p - 1) * parameters.period_length + pi[ean.get_first_event_in_line(line)] + duration[line],
                "end(" + str(p) + "," + str(line.get_directed_line_id()) + ")")

    # minimum time difference
    print("\tminimum time difference")
    for connection in vehicle_schedule.get_connections():
        p_1 = connection.get_period_1()
        l_1 = connection.get_line_1()
        p_2 = connection.get_period_2()
        l_2 = connection.get_line_2()
        m.addConstr(start[l_2][p_2] - end[l_1][p_1] >= turn_around_data.get_min_turnaround_time(l_1.get_last_stop(), l_2.get_first_stop()),
                    "L(" + str(l_1.get_directed_line_id()) + "," + str(p_1) + ","
                    + str(l_2.get_directed_line_id()) + "," + str(p_2) + ")")

    # write lp-file
    m.update()

    if parameters.write_lp_output:
        m.write("LinVeh_to_Tim_GUROBI.lp")
    # return

    # Set start solution
    if parameters.set_starting_timetable:
        m.update()
        print("Set start solution")
        for event in ean.get_events_network():
            pi[event].start = event.get_event_time()
        m.update()

    # Optimization
    print("Start optimization")
    m.optimize()
    print("End optimization")

    if m.SolCount == 0:
        # m.computeIIS()
        # m.write("LinVeh_to_Tim.ilp")
        print("No feasible solution was found!")
        return
    else:
        print("Feasible solution was found!")

    # print("\tminimum time difference")
    # for connection in vehicle_schedule.get_connections():
    #    p_1 = connection.get_period_1()
    #    l_1 = connection.get_line_1()
    #    p_2 = connection.get_period_2()
    #    l_2 = connection.get_line_2()
    #    print("Connection: %s, end 1: %d, start 2: %d, difference: %d" % (
    #        connection.to_string(), end[l_1][p_1].x, start[l_2][p_2].x, start[l_2][p_2].x - end[l_1][p_1].x))

    # for v in m.getVars():
    #    print('%s %g' % (v.varName, v.x))

    # print("Frequencies:")
    # for line in line_pool.get_lines():
    #    print("Frequency of line " + line.to_string() + ": %d" % frequencies[line].x)

    timetable_file = open(parameters.periodic_timetable_filename, 'w')
    timetable_file.write("# event_index; time\n")
    for event in ean.get_events_network():
        event.set_event_time(round(pi[event].x))
        timetable_file.write("%d; %d\n" % (event.get_event_id(), round(pi[event].x)))
    timetable_file.close()

    # Complete vehicle schedule
    for vehicle in vehicle_schedule.get_vehicles():
        vehicle.find_all_connections(vehicle_schedule.get_connections())

    # Aperiodic EAN construction and output

    print("Construct aperiodic ean:")
    pi_solution = {event: int(round(pi[event].x)) for event in pi.keys()}
    duration_solution = {line: int(round(var.x)) for line, var in duration.items()}

    aperiodic_ean = AperiodicEan(ean)
    aperiodic_ean.aperiodic_ean_from_vehicle_schedule(ean, vehicle_schedule, duration_solution, pi_solution, parameters.period_length)
    aperiodic_ean.update_aperiodic_times(parameters.ean_earliest_time)

    events_expanded_file = open(parameters.event_expanded_file_name, 'w')
    events_expanded_file.write("# event-id; periodic-id; type; time; passengers\n")

    for event in ean.get_all_events():
        for aperiodic_event in aperiodic_ean.get_aperiodic_events_by_periodic_event(event):
            events_expanded_file.write(aperiodic_event.to_events_expanded() + "\n")

    events_expanded_file.close()

    timetable_expanded_file = open(parameters.timetable_expanded_file_name, 'w')
    timetable_expanded_file.write("# event-id; time\n")

    for event in ean.get_all_events():
        for aperiodic_event in aperiodic_ean.get_aperiodic_events_by_periodic_event(event):
            timetable_expanded_file.write(aperiodic_event.to_timetable_expanded() + "\n")

    timetable_expanded_file.close()

    # print("Durations:")
    # for line in line_pool.get_lines():
    #     print("line " + line.to_string() + ", duration " + str(duration[line].x))

    activities_expanded_file = open(parameters.activities_expanded_file_name, 'w')
    activities_expanded_file.write(
        "# activity-id; periodic-id; type; tail-event-id; head-event-id; lower-bound; upper-bound; passengers\n")

    for activity in ean.get_activities(['drive', 'wait', 'sync']):
        for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
            activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")

    for activity in ean.get_activities(['trans']):
        for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
            activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")

    for activity in ean.get_activities(['headway']):
        for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
            activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")

    activities_expanded_file.close()

    print("Output vehicle_schedule, trips and end events of trips:")
    vehicle_file = open(parameters.vehicle_file_name, 'w')
    vehicle_file.write("# Format: circulation-ID; vehicle-ID; trip-number of this vehicle; type;" +
                       "start-ID; periodic-start-ID; start-station; start-time; end-ID; periodic-end-id; end-station; "
                       + "end-time; line\n")

    trip_file = open(parameters.trip_file_name, 'w')
    trip_file.write("# start-ID; periodic-start-ID; start-station; start-time; end-ID; periodic-end-ID; end-station; end-time; line\n")

    end_events_file = open(parameters.end_events_file_name, 'w')
    end_events_file.write("# event-id\n")

    circ_id = 1
    # get first event to save for last empty trip
    last_trip = Trip()
    vehicle = vehicle_schedule.get_vehicles()[0]
    last_trip.set_end(circ_id, vehicle.get_start_line(), vehicle.get_start_period(), parameters.period_length, ean,
                      aperiodic_ean)
    trip = Trip()
    first = True
    vehicle_swap_trip = Trip()
    for vehicle in vehicle_schedule.get_vehicles():
        if not first:
            vehicle_swap_trip.set_end(circ_id, vehicle.get_start_line(), vehicle.get_start_period(), parameters.period_length, ean,
                                      aperiodic_ean)
            # vehicle_file.write(vehicle_swap_trip.to_csv() + "\n")
        first = False
        trip_id = 1
        vehicle_id = vehicle.get_vehicle_id()
        # First Trip:
        # output trip
        trip.trip(circ_id, trip_id, vehicle_id, vehicle.get_start_line(), vehicle.get_start_period(),
                  parameters.period_length, ean, aperiodic_ean, duration_solution)
        vehicle_file.write(trip.to_csv() + "\n")
        trip_file.write(trip.to_csv_trip() + "\n")
        end_events_file.write(trip.to_csv_end_events() + "\n")
        for connection in vehicle.get_connections():
            # output empty trip
            trip_id += 1
            trip.empty_trip(circ_id, trip_id, vehicle_id, connection, parameters.period_length, ean, aperiodic_ean,
                            duration_solution)
            vehicle_file.write(trip.to_csv() + "\n")
            # output trip
            trip_id += 1
            trip.trip(circ_id, trip_id, vehicle_id, connection.get_line_2(), connection.get_period_2(),
                      parameters.period_length, ean, aperiodic_ean, duration_solution)
            vehicle_file.write(trip.to_csv() + "\n")
            trip_file.write(trip.to_csv_trip() + "\n")
            end_events_file.write(trip.to_csv_end_events() + "\n")
        # set vehicle swap trip
        trip_id += 1
        vehicle_swap_trip.set_start(trip_id, vehicle.get_vehicle_id(), vehicle.get_last_line(),
                                    vehicle.get_last_period(), parameters.period_length, ean, aperiodic_ean,
                                    duration_solution)

    # set end of circulation
    vehicle = vehicle_schedule.get_vehicles()[-1]
    last_trip.set_start(trip_id, vehicle.get_vehicle_id(), vehicle.get_last_line(), vehicle.get_last_period(),
                        parameters.period_length, ean, aperiodic_ean, duration_solution)
    # vehicle_file.write(last_trip.to_csv())
    vehicle_file.close()

    trip_file.close()
    end_events_file.close()

    print('Obj: %g' % m.objVal)

    # for v in m.getVars():
    #    if v.x > 0:
    #        print('%s %g' % (v.varName, v.x))
