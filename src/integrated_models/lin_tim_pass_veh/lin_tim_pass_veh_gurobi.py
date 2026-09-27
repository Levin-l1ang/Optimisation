import gurobipy
from ean_data import *
import od_data
from lin_tim_pass_veh_helper import LinTimPassVehParameters
from vehicle_schedule import *
from preprocessing import *
from vs_helper import TurnaroundData


def solve(ean: Ean, ptn: ptn_data.Ptn, line_pool: line_data.LinePool, od: od_data.OD, turnaround_data: TurnaroundData,
          parameters: LinTimPassVehParameters) -> None:
    # raise Exception("Adapt to frequencies!")

    # Preprocessing
    print("Compute fixed passenger paths...")
    preprocessing = PtnPreprocessor(ptn, line_pool, ean, parameters, parameters, parameters)
    if parameters.add_fix_passenger_paths:
        ptn_drive_weights, ptn_wait_weights, ptn_transfer_weights = preprocessing.compute_weights_unrouted_passengers(
            ptn, od)
    print("done!")

    # Big M constraints
    max_upper_bound = ean.compute_max_upper_bound()
    max_n_edges_in_line = line_pool.get_max_n_edges_in_line()

    m_1 = parameters.period_length
    m_2 = parameters.period_length
    m_3 = parameters.p_max * parameters.period_length + max_n_edges_in_line * max_upper_bound
    m_4 = max_upper_bound
    m_5 = parameters.p_max * parameters.period_length + max_n_edges_in_line * max_upper_bound

    print("m_1=%d\nm_2=%d\nm_3=%d\nm_4=%d\nm_5=%d" % (m_1, m_2, m_3, m_4, m_5))

    # Lists for loops
    activities_drive_wait_trans = ean.get_activities(['drive', 'wait', 'trans'])
    activities_drive_wait_trans_sync = ean.get_activities(['drive', 'wait', 'trans', 'sync'])
    activities_drive = ean.get_activities(['drive'])
    activities_wait = ean.get_activities(['wait'])
    activities_trans_wait = ean.get_activities(['wait', 'trans'])
    activities_time_to = ean.get_activities(['time', 'to'])
    activities_from = ean.get_activities(['from'])
    activities_no_sync = ean.get_activities(['drive', 'wait', 'trans', 'time', 'to', 'from'])
    active_od_pairs = od.get_active_od_pairs()

    # for od_pair in active_od_pairs:
    #    print(od_pair.to_string())

    # Dictionaries for storing variables
    frequencies = {}
    lines_established = {}
    pi = {}
    modulo_parameter = {}
    # path_used = {}
    arc_used = {}
    travel_time_linear = {}
    vehicle_connect = {}
    vehicle_from_depot = {}
    vehicle_to_depot = {}
    duration = {}
    start = {}
    end = {}
    time_connection = {}
    drive_time = {}
    time_est_drive = {}
    time_est_wait = {}
    time_est_transfer = {}

    # Initialize Model
    print("Initialize model")
    m = gurobipy.Model("LinTimVeh")
    if parameters.time_limit != -1:
        m.params.timeLimit = parameters.time_limit
    if parameters.mip_gap >= 0:
        m.params.MIPGap = parameters.mip_gap
    m.params.threads = parameters.n_threads
    m.modelSense = gurobipy.GRB.MINIMIZE

    # Initialize Variables
    print("Initialize variables")
    # Frequencies
    for line in line_pool.get_lines():
        frequencies[line] = m.addVar(vtype=gurobipy.GRB.BINARY, name='f_%s' % line.to_string())

    # Lines_established
    for activity in ean.get_activities(['drive', 'wait', 'trans', 'sync']):
        lines_established[activity] = m.addVar(vtype=gurobipy.GRB.BINARY, name='y_%s' % activity.to_string())

    # pi
    for event in ean.get_events_network():
        pi[event] = m.addVar(0, parameters.period_length - 1, vtype=gurobipy.GRB.INTEGER, name='pi_%s' % event.to_string())

    # modulo parameter
    for activity in ean.get_activities(['drive', 'wait', 'trans', 'sync']):
        modulo_parameter[activity] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='z_%s' % activity.to_string())

    # # Path used
    # for od_pair in od.get_active_od_pairs():
    #     path_used[od_pair] = {}
    #     for t in range(1, od_pair.get_n_time_slices() + 1):
    #         path_used[od_pair][t] = m.addVar(vtype=gurobipy.GRB.BINARY, name='x_%d_%s' % (t, od_pair.to_string()))

    # Arc used
    for activity in activities_no_sync:
        arc_used[activity] = {}
        for od_pair in od.get_active_od_pairs():
            arc_used[activity][od_pair] = {}
            for t in range(1, od_pair.get_n_time_slices() + 1):
                arc_used[activity][od_pair][t] = m.addVar(vtype=gurobipy.GRB.BINARY, name='z_%s_%s%d' % (
                    activity.to_string(), od_pair.to_string(), t))

    # Travel time linear
    for activity in ean.get_activities(['drive', 'wait', 'trans']):
        travel_time_linear[activity] = {}
        for od_pair in od.get_active_od_pairs():
            travel_time_linear[activity][od_pair] = {}
            for t in range(1, od_pair.get_n_time_slices() + 1):
                travel_time_linear[activity][od_pair][t] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='d_%s_%s%d' % (
                    activity.to_string(), od_pair.to_string(), t))

    if parameters.add_fix_passenger_paths:
        # Time estimation drive
        print("\ttime estimation drive")
        for edge in ptn_drive_weights.keys():
            time_est_drive[edge] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='time_est_drive_%s' % str(edge))

        # Time estimation wait
        print("\ttime estimation wait")
        for node in ptn_wait_weights.keys():
            time_est_wait[node] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='time_est_wait_%s' % str(node))

        # Time estimation transfer
        print("\ttime estimation transfer stations")
        for edge_in in ptn_transfer_weights.keys():
            time_est_transfer[edge_in] = {}
            for edge_out in ptn_transfer_weights.values().keys():
                time_est_transfer[edge_in][edge_out] = m.addVar(vtype=gurobipy.GRB.INTEGER,
                                                                name='time_est_transfer_station_%s_%s'
                                                                     % (str(edge_in), str(edge_out)))

    # Vehicle Connect
    for p_1 in range(1, parameters.p_max + 1):
        vehicle_connect[p_1] = {}
        for l_1 in line_pool.get_lines():
            vehicle_connect[p_1][l_1] = {}
            for p_2 in range(1, parameters.p_max + 1):
                vehicle_connect[p_1][l_1][p_2] = {}
                for l_2 in line_pool.get_lines():
                    vehicle_connect[p_1][l_1][p_2][l_2] = m.addVar(vtype=gurobipy.GRB.BINARY,
                                                                   name='x_(%d,%s)(%d,%s)' % (
                                                                       p_1, l_1.to_string(), p_2, l_2.to_string()))

    # Vehicle from depot
    for p in range(1, parameters.p_max + 1):
        vehicle_from_depot[p] = {}
        for line in line_pool.get_lines():
            vehicle_from_depot[p][line] = m.addVar(vtype=gurobipy.GRB.BINARY,
                                                   name='x_depot,(%d,%s)' % (p, line.to_string()))

    # Vehicle to depot
    for p in range(1, parameters.p_max + 1):
        vehicle_to_depot[p] = {}
        for line in line_pool.get_lines():
            vehicle_to_depot[p][line] = m.addVar(vtype=gurobipy.GRB.BINARY,
                                                 name='x_(%d,%s),depot' % (p, line.to_string()))

    # Duration
    for line in line_pool.get_lines():
        duration[line] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='duration_%s' % line.to_string())

    # Start
    for p in range(1, parameters.p_max + 1):
        start[p] = {}
        for line in line_pool.get_lines():
            start[p][line] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='start(%d,%s)' % (p, line.to_string()))

    # End
    for p in range(1, parameters.p_max + 1):
        end[p] = {}
        for line in line_pool.get_lines():
            end[p][line] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='end(%d,%s)' % (p, line.to_string()))

    # Time Connection
    for p_1 in range(1, parameters.p_max + 1):
        time_connection[p_1] = {}
        for l_1 in line_pool.get_lines():
            time_connection[p_1][l_1] = {}
            for p_2 in range(1, parameters.p_max + 1):
                time_connection[p_1][l_1][p_2] = {}
                for l_2 in line_pool.get_lines():
                    time_connection[p_1][l_1][p_2][l_2] = m.addVar(vtype=gurobipy.GRB.INTEGER,
                                                                   name='y_(%d,%s)(%d,%s)' % (
                                                                       p_1, l_1.to_string(), p_2, l_2.to_string()))

    # Drive Time
    for line in line_pool.get_lines():
        drive_time[line] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='drive_time_%s' % line.to_string())

    # Add variables to model m
    m.update()
    print("Number of variables: " + str(m.getAttr(gurobipy.GRB.Attr.NumVars)))

    # objective function
    print("Initialize objective function")
    sum_drive_time_unweighted = 0
    sum_line_length = 0
    sum_travel_time = 0
    sum_drive_time = 0
    sum_wait_time = 0
    sum_transfer_time = 0
    sum_penalty_changing_time_slices = 0
    sum_transfers = 0
    sum_turn_around_time = 0
    sum_turn_around_distance = 0
    sum_vehicles = 0

    for line in line_pool.get_lines():
        sum_line_length += line.compute_length_from_ptn() * frequencies[line]
        sum_drive_time_unweighted += drive_time[line]
    for od_pair in active_od_pairs:
        for t in range(1, od_pair.get_n_time_slices() + 1):
            for activity in activities_drive_wait_trans:
                sum_travel_time += travel_time_linear[activity][od_pair][t] * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'trans':
                    sum_transfers += arc_used[activity][od_pair][t] * od_pair.get_n_passengers(t)
                    sum_transfer_time += travel_time_linear[activity][od_pair][t] * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'wait':
                    sum_wait_time += travel_time_linear[activity][od_pair][t] * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'drive':
                    sum_drive_time += travel_time_linear[activity][od_pair][t] * od_pair.get_n_passengers(t)
            for activity in ean.get_activities(['time']):
                time_2 = activity.get_right_event().get_time_2()
                sum_penalty_changing_time_slices += od_pair.get_penalty(t, time_2) * parameters.period_length * \
                                                    arc_used[activity][od_pair][t] * od_pair.get_n_passengers(t)

    # add passengers on fixed paths
    if parameters.add_fix_passenger_paths:
        for edge, passengers in ptn_drive_weights.items():
            sum_travel_time += time_est_drive[edge] * passengers
            sum_drive_time += time_est_drive[edge] * passengers
        for stop, passengers in ptn_wait_weights.items():
            sum_travel_time += time_est_wait[stop] * passengers
            sum_wait_time += time_est_wait[stop] * passengers
        for edge_in in ptn_transfer_weights.keys():
            for edge_out, passengers in ptn_transfer_weights[edge_in].items():
                sum_transfers += passengers
                sum_travel_time += time_est_transfer[edge_in][edge_out] * passengers
                sum_transfer_time += time_est_transfer[edge_in][edge_out] * passengers

    for p_1 in range(1, parameters.p_max + 1):
        for l_1 in line_pool.get_lines():
            for p_2 in range(1, parameters.p_max + 1):
                for l_2 in line_pool.get_lines():
                    sum_turn_around_time += time_connection[p_1][l_1][p_2][l_2]
                    sum_turn_around_distance += vehicle_connect[p_1][l_1][p_2][l_2] * \
                                                turnaround_data.get_min_turnaround_distance(l_1.get_last_stop(),
                                                                                            l_2.get_first_stop())
            sum_turn_around_time += vehicle_from_depot[p_1][l_1] * turnaround_data.\
                get_min_from_depot_time(l_1.get_first_stop())
            sum_turn_around_time += vehicle_to_depot[p_1][l_1] * turnaround_data.\
                get_min_to_depot_time(l_1.get_last_stop())
            sum_turn_around_distance += vehicle_from_depot[p_1][l_1] * turnaround_data.\
                get_min_from_depot_distance(l_1.get_first_stop())
            sum_turn_around_distance += vehicle_to_depot[p_1][l_1] * turnaround_data.\
                get_min_to_depot_distance(l_1.get_last_stop())
            sum_vehicles += vehicle_from_depot[p_1][l_1]

    m.setObjective(parameters.factor_drive_time_unweighted * sum_drive_time_unweighted + parameters.factor_line_length * sum_line_length +
                   parameters.factor_travel_time * sum_travel_time +
                   parameters.factor_drive_time * sum_drive_time + parameters.factor_transfer_time * sum_transfer_time +
                   parameters.factor_wait_time * sum_wait_time +
                   parameters.factor_penalty_time_slice * sum_penalty_changing_time_slices +
                   parameters.transfer_penalty * sum_transfers + parameters.factor_turn_around_time * sum_turn_around_time +
                   parameters.factor_turn_around_distance * sum_turn_around_distance + parameters.factor_vehicles * sum_vehicles)

    # m.update()

    # Add constraints
    print("Add constraints:")
    # timetabling constraints
    print("\ttimetabling")
    timetabling_constraints = []
    timetabling_established_lines_constraints = []
    for activity in activities_drive_wait_trans_sync:
        i = activity.get_left_event()
        j = activity.get_right_event()
        const_1 = m.addConstr(pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length
                              >= lines_established[activity] * activity.get_lower_bound(),
                              name="timetabling_1_%s" % activity.to_string())
        const_2 = m.addConstr(pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length
                              <= activity.get_upper_bound() + (1 - lines_established[activity]) * m_1,
                              name="timetabling_2_%s" % activity.to_string())
        const_3 = m.addConstr(lines_established[activity] <= frequencies[i.get_line()],
                              name="timetabling_est_lines_1_%s" % activity.to_string())
        const_4 = m.addConstr(lines_established[activity] <= frequencies[j.get_line()],
                              name="timetabling_est_lines_2_%s" % activity.to_string())
        const_5 = m.addConstr(lines_established[activity] + 1 >= frequencies[i.get_line()] + frequencies[j.get_line()],
                              name="timetabling_est_lines_3_%s" % activity.to_string())
        if parameters.write_lp_output:
            timetabling_constraints.append(const_1)
            timetabling_constraints.append(const_2)
            timetabling_established_lines_constraints.append(const_3)
            timetabling_established_lines_constraints.append(const_4)
            timetabling_established_lines_constraints.append(const_5)

    # In undirected case forward and backward direction of a line have to be used together
    undirected_lines_constraints = []
    if not parameters.directed:
        print("\tundirected lines")
        for line in line_pool.get_lines():
            if line.get_directed_line_id() < 0:
                backwards_line = line_pool.get_line_by_directed_id_and_repetition(line.get_undirected_line_id(),
                                                                                  line.get_repetition())
                const_1 = m.addConstr(frequencies[line] == frequencies[backwards_line],
                                      name="undirected_lines_%s" % line.to_string())
                if parameters.write_lp_output:
                    undirected_lines_constraints.append(const_1)

    # Lines have to be used with frequency 0, 1 or the system frequency.
    system_frequency_constraints = []
    print("\tsystem frequency")
    for line_id in range(1, line_pool.get_max_id() + 1):
        lines = line_pool.get_lines_by_directed_id(line_id)
        for repetition in range(1, lines[0].get_frequency()):
            # if only frequency 1 is used, it has to be the line with repetition 1
            if repetition == 1 and not parameters.use_system_frequency:
                const_1 = m.addConstr(frequencies[lines[repetition]] >= frequencies[lines[repetition + 1]],
                                      name="system_frequency_%d_%d" % (line_id, repetition))
            # if the system frequency is used, all repetitions have to be used
            else:
                const_1 = m.addConstr(frequencies[lines[repetition]] == frequencies[lines[repetition + 1]],
                                      name="system_frequency_%d_%d" % (line_id, repetition))
            if parameters.write_lp_output:
                system_frequency_constraints.append(const_1)

    # Line frequencies have to be according to minimal/maximal frequencies for each edge
    upper_lower_frequency_constraints = []
    if parameters.check_lower_frequencies or parameters.check_upper_frequencies:
        print('\tlower and upper frequencies')
        lines_by_edges = {}
        for edge in ptn.get_edges():
            lines_by_edges[edge] = []
        for line in line_pool.get_lines():
            for edge in line.get_edges():
                lines_by_edges[edge].append(line)
        for edge in ptn.get_edges():
            if not lines_by_edges[edge]:
                if parameters.check_lower_frequencies and edge.getLowerFrequencyBound() > 0:
                    print("Edge %s is not covered by any line, lower frequency bound %d!" % (
                        str(edge), edge.getLowerFrequencyBound()))
                    print("No feasible solution was found!")
                    return
                continue
            sum_frequencies = 0
            for line in lines_by_edges[edge]:
                sum_frequencies += frequencies[line]
            if parameters.check_lower_frequencies:
                const_1 = m.addConstr(sum_frequencies >= edge.getLowerFrequencyBound(),
                                      name="lower_freq_%d" % edge.getId())
            if parameters.check_upper_frequencies:
                const_2 = m.addConstr(sum_frequencies <= edge.getUpperFrequencyBound(),
                                      name="upper_freq_%d" % edge.getId())
            if parameters.write_lp_output:
                upper_lower_frequency_constraints.append(const_1)
                upper_lower_frequency_constraints.append(const_2)

    # Restricting the number of lines
    # print("\trestricting number of lines")
    # m.addConstr(gurobipy.quicksum(frequencies.values()) <= max_number_of_lines)

    # Only arcs belonging to established lines can be used
    print("\testablished lines")
    established_lines_const = []
    for activity in activities_no_sync:
        i = activity.get_left_event()
        j = activity.get_right_event()
        lines = set()
        if isinstance(i, EanEventNetwork):
            lines.add(i.get_line())
        if isinstance(j, EanEventNetwork):
            lines.add((j.get_line()))
        for line in lines:
            for od_pair in od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    const_1 = m.addConstr(arc_used[activity][od_pair][t] <= frequencies[line],
                                          name="established_lines_%s_%s_%s_%d" % (
                                          activity.to_string(), line.to_string(), od_pair.to_string(), t))
                    if parameters.write_lp_output:
                        established_lines_const.append(const_1)

    # # Only arcs belonging to used paths can be used
    # print("\tarcs belonging to used paths")
    # for activity in ean.get_all_activities():
    #     for od_pair in od.get_active_od_pairs():
    #         for t in range(1, od_pair.get_n_time_slices() + 1):
    #             m.addConstr(arc_used[activity][od_pair][t] <= path_used[od_pair][t])

    # Passenger flow
    print("\tpassenger flow")
    passenger_flow_constraints = []
    counter = 1
    for od_pair in active_od_pairs:
        print("\t\tOd-pair: %d" % counter)
        counter += 1

        if parameters.use_preprocessing:
            used_tuple = preprocessing.compute_potentially_used_events_and_activities(ean, ptn, od_pair)
            used_events = used_tuple[0]
            used_activities_drive_wait_trans = used_tuple[1]
        else:
            used_events = ean.get_all_events()
            used_activities_drive_wait_trans = activities_drive_wait_trans

        for t in range(1, od_pair.get_n_time_slices() + 1):
            # for event in ean.get_all_events():
            for event in used_events:
                sum_out = 0
                sum_in = 0
                right_hand_side = 0
                empty = True
                activities_to_check = []
                # for activity in activities_drive_wait_trans:
                for activity in used_activities_drive_wait_trans:
                    activities_to_check.append(activity)
                for activity in activities_time_to:
                    event_to_check = activity.get_left_event()
                    if event_to_check.check_attributes_od(od_pair.get_origin(), od_pair.get_destination(), t):
                        activities_to_check.append(activity)
                for activity in activities_from:
                    event_to_check = activity.get_right_event()
                    if event_to_check.check_attributes_od(od_pair.get_origin(), od_pair.get_destination(), t):
                        activities_to_check.append(activity)
                # if isinstance(event, EanEventOD) and event.check_attributes_od(od_pair.get_origin(),
                #                                                               od_pair.get_destination(), t):
                #    for activity in ean.get_activities(['time', 'to', 'from']):
                #        if activity.get_left_event() == event or activity.get_right_event() == event:
                #            activities_to_check.append(activity)
                for activity in activities_to_check:
                    if activity.get_left_event() == event:
                        sum_out += arc_used[activity][od_pair][t]
                        empty = False
                    if activity.get_right_event() == event:
                        sum_in += arc_used[activity][od_pair][t]
                        empty = False
                if isinstance(event, EanEventOD):
                    if event.check_attributes(od_pair.get_origin(), od_pair.get_destination(), 'source', t, t):
                        right_hand_side = 1  # path_used[od_pair][t]
                    elif event.check_attributes(od_pair.get_origin(), od_pair.get_destination(), 'target', t, None):
                        right_hand_side = -1  # -path_used[od_pair][t]
                if not empty:
                    const_1 = m.addConstr(sum_out - sum_in == right_hand_side,
                                          name="pass_routing_%s_%d_%s" % (od_pair.to_string(), t, event.to_string()))
                    if parameters.write_lp_output:
                        passenger_flow_constraints.append(const_1)

    # First network-event in path in right time-slice
    print("\ttime slice")
    time_slice_constraints = []
    for activity in ean.get_activities(['to']):
        left_event = activity.get_left_event()
        right_event = activity.get_right_event()
        origin = left_event.get_start()
        destination = left_event.get_end()
        time_1 = left_event.get_time_1()
        time_2 = left_event.get_time_2()
        od_pair = od.get_od_pair(origin, destination)
        if od_pair.is_active():
            const_1 = m.addConstr(pi[right_event] >= arc_used[activity][od_pair][time_1] * (time_2 - 1) * (
                parameters.period_length / od_pair.get_n_time_slices()), name="timeslice_lb_%s" % activity.to_string())
            const_2 = m.addConstr(
                pi[right_event] <= (time_2 * (parameters.period_length / od_pair.get_n_time_slices()) - 1) + m_2 * (
                    1 - arc_used[activity][od_pair][time_1]), name="timeslice_ub_%s" % activity.to_string())
            if parameters.write_lp_output:
                time_slice_constraints.append(const_1)
                time_slice_constraints.append(const_2)

    # Fixed passenger paths
    use_fixed_passenger_paths_constraints = []
    if parameters.add_fix_passenger_paths:
        print("\tfixed passenger paths")
        for edge, passengers in ptn_drive_weights.items():
            for activity in activities_drive:
                if activity.belongs_to_edge_drive(edge):
                    const = m.addConstr(
                        pi[activity.get_right_event()] - pi[activity.get_left_event()]
                        + modulo_parameter[activity] * parameters.period_length <=
                        time_est_drive[edge] + m_4 * (1 - lines_established[activity]),
                        name="fixed_pass_paths_drive_%s_%s" % (str(edge), activity.to_string()))
                    if parameters.write_lp_output:
                        use_fixed_passenger_paths_constraints.append(const)
        for node, passengers in ptn_wait_weights.items():
            for activity in activities_wait:
                if activity.belongs_to_node_wait(node):
                    const = m.addConstr(
                        pi[activity.get_right_event()] - pi[activity.get_left_event()]
                        + modulo_parameter[activity] * parameters.period_length <=
                        time_est_wait[node] + m_4 * (1 - lines_established[activity]),
                        name="fixed_pass_paths_wait_%s_%s" % (str(node), activity.to_string()))
                    if parameters.write_lp_output:
                        use_fixed_passenger_paths_constraints.append(const)
        for edge_in in ptn_transfer_weights.keys():
            for edge_out, passengers in ptn_transfer_weights[edge_in].items():
                for activity in activities_trans_wait:
                    if activity.belongs_to_transfer_node(edge_in, edge_out):
                        const = m.addConstr(
                            pi[activity.get_right_event()] - pi[activity.get_left_event()]
                            + modulo_parameter[activity] * parameters.period_length <=
                            time_est_transfer[edge_in][edge_out] + m_4 * (1 - lines_established[activity]),
                            name="fixed_pass_paths_transfer_%s_%s_%s" % (str(edge_in), str(edge_out),
                                                                         activity.to_string()))
                        if parameters.write_lp_output:
                            use_fixed_passenger_paths_constraints.append(const)

    # Duration
    print("\tduration of a line")
    duration_constraints = []
    for line in line_pool.get_lines():
        expr_sum = 0
        for activity in ean.get_activities_in_line(line):
            i = activity.get_left_event()
            j = activity.get_right_event()
            expr_sum += pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length
        const_1 = m.addConstr(expr_sum == duration[line], name="duration_%s" % line.to_string())
        if parameters.write_lp_output:
            duration_constraints.append(const_1)

    # Start and End
    print("\tstart and end")
    start_end_constraints = []
    for p in range(1, parameters.p_max + 1):
        for line in line_pool.get_lines():
            const_1 = m.addConstr(start[p][line] == p * parameters.period_length + pi[ean.get_first_event_in_line(line)],
                                  name="start_%d_%s" % (p, line.to_string()))
            const_2 = m.addConstr(
                end[p][line] == p * parameters.period_length + pi[ean.get_first_event_in_line(line)] + duration[line],
                name="end_%d_%s" % (p, line.to_string()))
            if parameters.write_lp_output:
                start_end_constraints.append(const_1)
                start_end_constraints.append(const_2)

    # minimum time difference
    print("\tminimum time difference")
    min_time_difference_constraints = []
    for p_1 in range(1, parameters.p_max + 1):
        for l_1 in line_pool.get_lines():
            for p_2 in range(1, parameters.p_max + 1):
                for l_2 in line_pool.get_lines():
                    const_1 = m.addConstr(start[p_2][l_2] - end[p_1][l_1]
                                          >= vehicle_connect[p_1][l_1][p_2][l_2]
                                          * turnaround_data.get_min_turnaround_time(l_1.get_last_stop(),
                                                                                    l_2.get_first_stop())
                                          - (1 - vehicle_connect[p_1][l_1][p_2][l_2]) * m_3,
                                          name="min_time_diff_%d_%s_%d_%s"
                                               % (p_1, l_1.to_string(), p_2, l_2.to_string()))
                    if parameters.write_lp_output:
                        min_time_difference_constraints.append(const_1)

    # flow conservation vehicles 1
    print("\tvehicle flow 1")
    vehicle_flow_constraints = []
    for p_2 in range(1, parameters.p_max + 1):
        for l_2 in line_pool.get_lines():
            expr_sum = vehicle_from_depot[p_2][l_2]
            for p_1 in range(1, parameters.p_max + 1):
                for l_1 in line_pool.get_lines():
                    expr_sum += vehicle_connect[p_1][l_1][p_2][l_2]
            const_1 = m.addConstr(expr_sum == frequencies[l_2], name="vehicle_flow_1_%d_%s" % (p_2, l_2.to_string()))
            if parameters.write_lp_output:
                vehicle_flow_constraints.append(const_1)

    # flow conservation vehicles 2
    print("\tvehicle flow 2")
    for p_1 in range(1, parameters.p_max + 1):
        for l_1 in line_pool.get_lines():
            expr_sum = vehicle_to_depot[p_1][l_1]
            for p_2 in range(1, parameters.p_max + 1):
                for l_2 in line_pool.get_lines():
                    expr_sum += vehicle_connect[p_1][l_1][p_2][l_2]
            const_1 = m.addConstr(expr_sum == frequencies[l_1], name="vehicle_flow_2_%d_%s" % (p_1, l_1.to_string()))
            if parameters.write_lp_output:
                vehicle_flow_constraints.append(const_1)

    # vehicle connections only for established lines
    print("\tvehicle connections only for established lines")
    vehicles_for_established_lines = []
    for p_1 in range(1, parameters.p_max + 1):
        for l_1 in line_pool.get_lines():
            const_1 = m.addConstr(vehicle_from_depot[p_1][l_1] <= frequencies[l_1],
                                  name="vehicle_est_line_1_%d_%s" % (p_1, l_1.to_string()))
            const_2 = m.addConstr(vehicle_to_depot[p_1][l_1] <= frequencies[l_1],
                                  name="vehicle_est_line_2_%d_%s" % (p_1, l_1.to_string()))
            if parameters.write_lp_output:
                vehicles_for_established_lines.append(const_1)
                vehicles_for_established_lines.append(const_2)
            for p_2 in range(1, parameters.p_max + 1):
                for l_2 in line_pool.get_lines():
                    const_1 = m.addConstr(vehicle_connect[p_1][l_1][p_2][l_2] <= frequencies[l_1],
                                          name="vehicle_est_line_3_%d_%s_%d_%s"
                                               % (p_1, l_1.to_string(), p_2, l_2.to_string()))
                    const_2 = m.addConstr(vehicle_connect[p_2][l_2][p_1][l_1] <= frequencies[l_1],
                                          name="vehicle_est_line_4_%d_%s_%d_%s"
                                               % (p_1, l_1.to_string(), p_2, l_2.to_string()))
                    if parameters.write_lp_output:
                        vehicles_for_established_lines.append(const_1)
                        vehicles_for_established_lines.append(const_2)

    # Linearization travel time
    print("\tlinearization travel time")
    linearization_travel_time_constraints = []
    for activity in activities_drive_wait_trans:
        i = activity.get_left_event()
        j = activity.get_right_event()
        for od_pair in od.get_active_od_pairs():
            for t in range(1, od_pair.get_n_time_slices() + 1):
                const_1 = m.addConstr(travel_time_linear[activity][od_pair][t] >= 0,
                                      name="linearization_travel_1_%s_%d_%s"
                                           % (od_pair.to_string(), t, activity.to_string()))
                const_2 = m.addConstr(travel_time_linear[activity][od_pair][t] >=
                                      pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length - (
                                          1 - arc_used[activity][od_pair][t]) * m_4,
                                      name="linearization_travel_2_%s_%d_%s"
                                           % (od_pair.to_string(), t, activity.to_string()))
                if parameters.write_lp_output:
                    linearization_travel_time_constraints.append(const_1)
                    linearization_travel_time_constraints.append(const_2)

    # Linearization turn around time
    print("\tlinearization turn around time")
    linearization_turn_around_time_constraints = []
    for p_1 in range(1, parameters.p_max + 1):
        for l_1 in line_pool.get_lines():
            for p_2 in range(1, parameters.p_max + 1):
                for l_2 in line_pool.get_lines():
                    const_1 = m.addConstr(time_connection[p_1][l_1][p_2][l_2] >= 0,
                                          name="linearization_turn_1_%d_%s_%d_%s"
                                               % (p_1, l_1.to_string(), p_2, l_2.to_string()))
                    const_2 = m.addConstr(
                        time_connection[p_1][l_1][p_2][l_2] >= start[p_2][l_2] - end[p_1][l_1] - m_5 * (
                            1 - vehicle_connect[p_1][l_1][p_2][l_2]),
                        name="linearization_turn_2_%d_%s_%d_%s"
                             % (p_1, l_1.to_string(), p_2, l_2.to_string()))
                    if parameters.write_lp_output:
                        linearization_turn_around_time_constraints.append(const_1)
                        linearization_turn_around_time_constraints.append(const_2)

    # Linearization drive time lines
    print("\tlinearization drive time lines")
    linearization_drive_time_constraints = []
    for line in line_pool.get_lines():
        const_1 = m.addConstr(drive_time[line] >= 0, name="linearization_drive_1_%s" %line.to_string())
        const_2 = m.addConstr(drive_time[line] >= duration[line] - (1 - frequencies[line]) * m_3,
                              name="linearization_drive_2_%s" % line.to_string())
        if parameters.write_lp_output:
            linearization_drive_time_constraints.append(const_1)
            linearization_drive_time_constraints.append(const_2)

    # write lp-file
    if parameters.write_lp_output:
        m.update()
        m.write("LinTimVeh.lp")

        dec_file = open('LinTimVeh.dec', 'w')
        dec_file.write("PRESOLVED\n0\n")
        dec_file.write("NBLOCKS\n4\n")
        # Block 1: Line planning
        dec_file.write("BLOCK 1\n")
        for const in undirected_lines_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in system_frequency_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in upper_lower_frequency_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Block 2: Timetabling
        dec_file.write("BLOCK 2\n")
        for const in timetabling_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in duration_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in start_end_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in use_fixed_passenger_paths_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Block 3: Vehicle Scheduling
        dec_file.write("BLOCK 3\n")
        for const in vehicle_flow_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Block 3: Passenger flow
        dec_file.write("BLOCK 4\n")
        for const in passenger_flow_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Master-constraints: Coupling
        dec_file.write("MASTERCONSS\n")
        # Line planning + time tabling
        for const in timetabling_established_lines_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in linearization_drive_time_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Line planning + vehicle scheduling
        for const in vehicles_for_established_lines:
            dec_file.write(const.ConstrName + "\n")
        # Line planing + routing
        for const in established_lines_const:
            dec_file.write(const.ConstrName + "\n")
        # timetabling + vehicle scheduling
        for const in min_time_difference_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in linearization_turn_around_time_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Timetabling + routing
        for const in time_slice_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in linearization_travel_time_constraints:
            dec_file.write(const.ConstrName + "\n")
        dec_file.close()
    # return

    # Optimization
    print("Start optimization")
    m.optimize()
    gap = 'gap; ' + str(m.MIPGap)
    solver_time = 'solver_time; ' + str(m.Runtime)
    objective = 'objective; ' + str(m.objVal)
    # adapt path
    a = open(parameters.solver_statistic_filename, 'w')
    a.write(solver_time + '\n')
    a.write(gap + '\n')
    a.write(objective + '\n')
    a.close()
    print("End optimization")

    # Check if a feasible solution was found
    if m.SolCount == 0:
        print("No feasible solution was found!")
        return
    else:
        print("Feasible solution was found!")

    # for v in m.getVars():
    #    print('%s %g' % (v.varName, v.x))

    # print("Frequencies:")
    # for line in line_pool.get_lines():
    #    print("Frequency of line " + line.to_string() + ": %d" % frequencies[line].x)

    print("Print events")

    events_file = open(parameters.periodic_event_file_name, 'w')
    events_file.write("# event_id; type; stop-id; line-id; passengers; line-direction; line-freq-repetition\n")
    event_index = 1
    for event in ean.get_events_network():
        if round(frequencies[event.get_line()].x) == 1:
            passengers = 0
            for activity in event.get_outgoing_activities():
                for od_pair in od.get_active_od_pairs():
                    for t in range(1, od_pair.get_n_time_slices()):
                        if round(arc_used[activity][od_pair][t].x) == 1:
                            passengers += od_pair.get_n_passengers(t)
            event.set_event_id(event_index)
            event.set_n_passengers(passengers)
            events_file.write("%s\n" % event.to_events_periodic())
            event_index += 1
    events_file.close()

    print("Print timetable")

    timetable_file = open(parameters.periodic_timetable_filename, 'w')
    timetable_file.write("# event_index; time\n")
    for event in ean.get_events_network():
        if round(frequencies[event.get_line()].x) == 1:
            timetable_file.write("%d; %d\n" % (event.get_event_id(), round(pi[event].x)))
    timetable_file.close()

    print("Print activities")

    activities_file = open(parameters.periodic_activity_file_name, 'w')
    activities_file.write("# activity-id; type; tail-event-id; head-event-id; lower-bound; upper-bound; passengers\n")
    activity_index = 1
    for activity in ean.get_activities(['drive', 'wait', 'sync']):
        passengers = 0
        if activity.get_activity_type() != 'sync':
            for od_pair in od.get_active_od_pairs():
                for t in range(1, od_pair.get_n_time_slices() + 1):
                    if round(arc_used[activity][od_pair][t].x) == 1:
                        passengers += od_pair.get_n_passengers(t)
        if round(lines_established[activity].x) == 1:
            activity.set_activity_id(activity_index)
            activity.set_n_passengers(passengers)
            activities_file.write("%s %d\n" % (activity.to_activities_periodic(), passengers))
            activity_index += 1
    for activity in ean.get_activities(['trans']):
        passengers = 0
        for od_pair in od.get_active_od_pairs():
            for t in range(1, od_pair.get_n_time_slices() + 1):
                if round(arc_used[activity][od_pair][t].x) == 1:
                    passengers += od_pair.get_n_passengers(t)
        if round(lines_established[activity].x) == 1:
            activity.set_activity_id(activity_index)
            activity.set_n_passengers(passengers)
            activities_file.write("%s %d\n" % (activity.to_activities_periodic(), passengers))
            activity_index += 1
    activities_file.close()

    print("Print line concept")

    line_concept_file = open(parameters.line_concept_file_name, 'w')
    line_concept_file.write("# line-id; edge-order; edge-id; frequency\n")
    max_index = line_pool.get_max_id()
    line_range = range(1, int(max_index) + 1)
    for line_id in line_range:
        edge_index = 1
        lines = line_pool.get_lines_by_directed_id(line_id)
        frequency = 0
        for line in lines.values():
            frequency += round(frequencies[line].x)
        for edge in line_pool.get_line_by_directed_id(line_id).get_edges():
            line_concept_file.write("%d; %d; %d; %d\n" % (
                line_id, edge_index, abs(edge.getId()), frequency))
            edge_index += 1
    line_concept_file.close()

    # Output objective
    print("Computing all parts of the objective")
    sum_drive_time_unweighted = 0
    sum_line_length = 0
    sum_travel_time = 0
    sum_drive_time = 0
    sum_wait_time = 0
    sum_transfer_time = 0
    sum_penalty_changing_time_slices = 0
    sum_transfers = 0
    sum_turn_around_time = 0
    sum_turn_around_distance = 0
    sum_vehicles = 0

    for line in line_pool.get_lines():
        sum_line_length += line.compute_length_from_ptn() * round(frequencies[line].x)
        sum_drive_time_unweighted += round(drive_time[line].x)
    for od_pair in active_od_pairs:
        for t in range(1, od_pair.get_n_time_slices() + 1):
            for activity in activities_drive_wait_trans:
                sum_travel_time += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'trans':
                    sum_transfers += round(arc_used[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                    sum_transfer_time += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'wait':
                    sum_wait_time += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'drive':
                    sum_drive_time += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
            for activity in ean.get_activities(['time']):
                time_2 = activity.get_right_event().get_time_2()
                sum_penalty_changing_time_slices += od_pair.get_penalty(t, time_2) * parameters.period_length * \
                                                    round(arc_used[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
    for p_1 in range(1, parameters.p_max + 1):
        for l_1 in line_pool.get_lines():
            for p_2 in range(1, parameters.p_max + 1):
                for l_2 in line_pool.get_lines():
                    sum_turn_around_time += round(time_connection[p_1][l_1][p_2][l_2].x)
                    sum_turn_around_distance += round(vehicle_connect[p_1][l_1][p_2][l_2].x) * \
                                                turnaround_data.get_min_turnaround_distance(l_1.get_last_stop(),
                                                                                            l_2.get_first_stop())
            sum_turn_around_time += round(vehicle_from_depot[p_1][l_1].x) * turnaround_data.\
                get_min_from_depot_time(l_1.get_first_stop())
            sum_turn_around_time += round(vehicle_to_depot[p_1][l_1].x) * turnaround_data.\
                get_min_to_depot_time(l_1.get_last_stop())
            sum_turn_around_distance += round(vehicle_from_depot[p_1][l_1].x) * turnaround_data.\
                get_min_from_depot_distance(l_1.get_first_stop())
            sum_turn_around_distance += round(vehicle_to_depot[p_1][l_1].x) * turnaround_data.\
                get_min_to_depot_distance(l_1.get_last_stop())
            sum_vehicles += round(vehicle_from_depot[p_1][l_1].x)

    objective_file = open(parameters.objectives_file_name, 'w')
    objective_file.write("sum drive time vehicles full; %f\n" % sum_drive_time_unweighted)
    objective_file.write("sum drive distance vehicles full; %f\n" % sum_line_length)
    objective_file.write("sum passenger travel time; %f\n" % sum_travel_time)
    objective_file.write("sum passenger drive time; %f\n" % sum_drive_time)
    objective_file.write("sum passenger wait time; %f\n" % sum_wait_time)
    objective_file.write("sum passenger transfer time; %f\n" % sum_transfer_time)
    objective_file.write("sum penalty changing time slices; %f\n" % sum_penalty_changing_time_slices)
    objective_file.write("number of transfers; %f\n" % sum_transfers)
    objective_file.write("sum drive time vehicles empty; %f\n" % sum_turn_around_time)
    objective_file.write("sum drive distances vehicles empty; %f\n" % sum_turn_around_distance)
    objective_file.write("number of vehicles; %f\n" % sum_vehicles)
    objective_file.write("felt travel time; %f\n" % (
        parameters.factor_drive_time * sum_drive_time + parameters.factor_wait_time * sum_wait_time +
        parameters.factor_transfer_time * sum_transfer_time + parameters.transfer_penalty * sum_transfers))
    objective_file.write("vehicle cost; %f\n" % (
        parameters.factor_drive_time_unweighted * sum_drive_time_unweighted + parameters.factor_line_length * sum_line_length +
        parameters.factor_turn_around_distance * sum_turn_around_distance + parameters.factor_turn_around_time * sum_turn_around_time +
        parameters.factor_vehicles * sum_vehicles))
    objective_file.write('Total value: %g' % m.objVal)
    objective_file.close()

    # Vehicle schedule

    # print("Print vehicle Schedule:")
    vehicle_schedule = VehicleSchedule(line_pool)
    vehicle_schedule.add_connections_from_ip_model(vehicle_connect, parameters.p_max, line_pool)
    # print("Connections: " + str(len(vehicle_schedule.get_connections())))
    print("Add vehicles")
    for p_1 in range(1, parameters.p_max + 1):
        for l_1 in line_pool.get_lines():
            if round(vehicle_from_depot[p_1][l_1].x) == 1:
                vehicle_schedule.add_vehicle(Vehicle(vehicle_schedule, p_1, l_1))

    for vehicle in vehicle_schedule.get_vehicles():
        vehicle.find_all_connections(vehicle_schedule.get_connections())

    # for vehicle in vehicle_schedule.get_vehicles():
    #     print("Vehicle: " + str(vehicle.get_vehicle_id()))
    #     for connection in vehicle.get_connections():
    #         print("\t" + connection.to_string())

    # Aperiodic EAN construction and output

    print("Construct aperiodic ean")

    aperiodic_ean = AperiodicEan(ean)
    aperiodic_ean.aperiodic_ean_from_vehicle_schedule(ean, vehicle_schedule, duration, pi, parameters.period_length)
    aperiodic_ean.update_aperiodic_times(parameters.ean_earliest_time)

    events_expanded_file = open(parameters.event_expanded_file_name, 'w')
    events_expanded_file.write("# event-id; periodic-id; type; time; passengers\n")

    aperiodic_event_id = 1
    for event in ean.get_events_network():
        if round(frequencies[event.get_line()].x) == 1:
            for aperiodic_event in aperiodic_ean.get_aperiodic_events_by_periodic_event(event):
                aperiodic_event.reset_event_id(aperiodic_event_id)
                aperiodic_event_id += 1
                events_expanded_file.write(aperiodic_event.to_events_expanded() + "\n")

    events_expanded_file.close()

    timetable_expanded_file = open(parameters.timetable_expanded_file_name, 'w')
    timetable_expanded_file.write("# event-id; time\n")

    for event in ean.get_events_network():
        if round(frequencies[event.get_line()].x) == 1:
            for aperiodic_event in aperiodic_ean.get_aperiodic_events_by_periodic_event(event):
                timetable_expanded_file.write(aperiodic_event.to_timetable_expanded() + "\n")

    timetable_expanded_file.close()

    # print("Durations:")
    # for line in line_pool.get_lines():
    #     print("line " + line.to_string() + ", duration " + str(duration[line].x))

    activities_expanded_file = open(parameters.activities_expanded_file_name, 'w')
    activities_expanded_file.write(
        "# activity-id; periodic-id; type; tail-event-id; head-event-id; lower-bound; upper-bound; passengers\n")

    aperiodic_activity_id = 1
    for activity in ean.get_activities(['drive', 'wait', 'sync']):
        if round(lines_established[activity].x) == 1:
            for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
                aperiodic_activity.reset_activity_id(aperiodic_activity_id)
                aperiodic_activity_id += 1
                activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")

    for activity in ean.get_activities(['trans']):
        if round(lines_established[activity].x) == 1:
            for aperiodic_activity in aperiodic_ean.get_aperiodic_activities_by_periodic_activity(activity):
                aperiodic_activity.reset_activity_id(aperiodic_activity_id)
                aperiodic_activity_id += 1
                activities_expanded_file.write(aperiodic_activity.to_activities_expanded() + "\n")

    activities_expanded_file.close()

    print("Output vehicle_schedule, trips and end events of trips")
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
                  parameters.period_length, ean, aperiodic_ean, duration)
        vehicle_file.write(trip.to_csv() + "\n")
        trip_file.write(trip.to_csv_trip() + "\n")
        end_events_file.write(trip.to_csv_end_events() + "\n")
        for connection in vehicle.get_connections():
            # output empty trip
            trip_id += 1
            trip.empty_trip(circ_id, trip_id, vehicle_id, connection, parameters.period_length, ean, aperiodic_ean, duration)
            vehicle_file.write(trip.to_csv() + "\n")
            # output trip
            trip_id += 1
            trip.trip(circ_id, trip_id, vehicle_id, connection.get_line_2(), connection.get_period_2(),
                      parameters.period_length, ean, aperiodic_ean, duration)
            vehicle_file.write(trip.to_csv() + "\n")
            trip_file.write(trip.to_csv_trip() + "\n")
            end_events_file.write(trip.to_csv_end_events() + "\n")
        # set vehicle swap trip
        trip_id += 1
        vehicle_swap_trip.set_start(trip_id, vehicle.get_vehicle_id(), vehicle.get_last_line(),
                                    vehicle.get_last_period(), parameters.period_length, ean, aperiodic_ean, duration)

    # set end of circulation
    vehicle = vehicle_schedule.get_vehicles()[-1]
    last_trip.set_start(trip_id, vehicle.get_vehicle_id(), vehicle.get_last_line(), vehicle.get_last_period(),
                        parameters.period_length, ean, aperiodic_ean, duration)
    # vehicle_file.write(last_trip.to_csv())
    vehicle_file.close()

    trip_file.close()
    end_events_file.close()
