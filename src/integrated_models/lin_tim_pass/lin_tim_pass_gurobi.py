import logging

import gurobipy
from ean_data import *
import ptn_data
import line_data
import vehicle_schedule
import od_data
from lin_tim_pass_helper import LinTimPassParameters
from preprocessing import *

logger = logging.getLogger(__name__)

def solve(ean: Ean, ptn: ptn_data.Ptn, line_pool: line_data.LinePool, od: od_data.OD, parameters: LinTimPassParameters) -> None:
    # raise Exception("Adapt to frequencies!")

    # Preprocessing
    logging.debug("Compute fixed passenger paths...")
    preprocessor = PtnPreprocessor(ptn, line_pool, ean, parameters, parameters, parameters)
    if parameters.add_fix_passenger_paths:
        ptn_drive_weights, ptn_wait_weights, ptn_transfer_weights = \
            preprocessor.compute_weights_unrouted_passengers(ptn, od)
    logging.debug("done!")

    # Big M constraints
    max_upper_bound = ean.compute_max_upper_bound()

    m_1 = parameters.period_length
    m_2 = parameters.period_length
    m_4 = max_upper_bound

    logging.debug("m_1=%d\nm_2=%d\nm_4=%d\n" % (m_1, m_2, m_4))

    # Lists for loops
    activities_drive_wait_trans = ean.get_activities(['drive', 'wait', 'trans'])
    activities_drive = ean.get_activities(['drive'])
    activities_wait = ean.get_activities(['wait'])
    activities_trans = ean.get_activities(['trans'])
    activities_trans_wait = ean.get_activities(['wait', 'trans'])
    activities_drive_wait_trans_sync = ean.get_activities(['drive', 'wait', 'trans', 'sync'])
    activities_time_to = ean.get_activities(['time', 'to'])
    activities_from = ean.get_activities(['from'])
    activities_no_sync = ean.get_activities(['drive', 'wait', 'trans', 'time', 'to', 'from'])
    active_od_pairs = od.get_active_od_pairs()

    logging.debug("Number of drive, wait, transfer activities: %d" % activities_drive_wait_trans.__len__())

    # for od_pair in active_od_pairs:
    #    logging.debug(od_pair.to_string())

    # Dictionaries for storing variables
    frequencies = {}
    lines_established = {}
    pi = {}
    modulo_parameter = {}
    # path_used = {}
    arc_used = {}
    travel_time_linear = {}
    drive_time = {}
    time_est_drive = {}
    time_est_wait = {}
    time_est_transfer = {}

    # Initialize Model
    logging.debug("Initialize model")
    m = gurobipy.Model("LinTimPass")
    if parameters.time_limit != -1:
        m.params.timeLimit = parameters.time_limit
    if parameters.mip_gap >= 0:
        m.params.MIPGap = parameters.mip_gap
    m.params.threads = parameters.n_threads
    m.modelSense = gurobipy.GRB.MINIMIZE

    # Initialize Variables
    logging.debug("Initialize variables")
    # Frequencies
    logging.debug("\tfrequencies")
    for line in line_pool.get_lines():
        frequencies[line] = m.addVar(vtype=gurobipy.GRB.BINARY, name='f_%s' % line.to_string())

    # Lines_established
    logging.debug("\tlines established")
    for activity in activities_drive_wait_trans_sync:
        lines_established[activity] = m.addVar(vtype=gurobipy.GRB.BINARY, name='y_%s' % activity.to_string())

    # pi
    logging.debug("\tpi")
    for event in ean.get_events_network():
        pi[event] = m.addVar(0, parameters.period_length - 1, vtype=gurobipy.GRB.INTEGER, name='pi_%s' % event.to_string())

    # modulo parameter
    logging.debug("\tmodulo parameter")
    for activity in activities_drive_wait_trans_sync:
        modulo_parameter[activity] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='z_%s' % activity.to_string())

    # # Path used
    # for od_pair in od.get_active_od_pairs():
    #     path_used[od_pair] = {}
    #     for t in range(1, od_pair.get_n_time_slices() + 1):
    #         path_used[od_pair][t] = m.addVar(vtype=gurobipy.GRB.BINARY, name='x_%d_%s' % (t, od_pair.to_string()))

    # Arc used
    logging.debug("\tarc used")
    for activity in activities_no_sync:
        arc_used[activity] = {}
        for od_pair in active_od_pairs:
            arc_used[activity][od_pair] = {}
            for t in range(1, od_pair.get_n_time_slices() + 1):
                arc_used[activity][od_pair][t] = m.addVar(vtype=gurobipy.GRB.BINARY, name='p_%s_%s%d' % (
                    activity.to_string(), od_pair.to_string(), t))

    # Travel time linear
    logging.debug("\ttravel time linear")
    for activity in activities_drive_wait_trans:
        travel_time_linear[activity] = {}
        for od_pair in active_od_pairs:
            travel_time_linear[activity][od_pair] = {}
            for t in range(1, od_pair.get_n_time_slices() + 1):
                travel_time_linear[activity][od_pair][t] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='d_%s_%s%d' % (
                    activity.to_string(), od_pair.to_string(), t))

    if parameters.add_fix_passenger_paths:
        # Time estimation drive
        logging.debug("\ttime estimation drive")
        for edge in ptn_drive_weights.keys():
            time_est_drive[edge] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='time_est_drive_%s' % str(edge))

        # Time estimation wait
        logging.debug("\ttime estimation wait")
        for node in ptn_wait_weights.keys():
            time_est_wait[node] = m.addVar(vtype=gurobipy.GRB.INTEGER, name='time_est_wait_%s' % str(node))

        # Time estimation transfer
        logging.debug("\ttime estimation transfer stations")
        for edge_in in ptn_transfer_weights.keys():
            time_est_transfer[edge_in] = {}
            for edge_out in ptn_transfer_weights.values().keys():
                time_est_transfer[edge_in][edge_out] = m.addVar(vtype=gurobipy.GRB.INTEGER,
                                                                name='time_est_transfer_station_%s_%s'
                                                                     % (str(edge_in), str(edge_out)))

    # Add variables to model m
    m.update()
    logging.debug("Number of variables: " + str(m.getAttr(gurobipy.GRB.Attr.NumVars)))

    # objective function
    logging.debug("Initialize objective function")
    sum_line_length = 0
    sum_line_cost = 0
    sum_travel_time = 0
    sum_drive_time = 0
    sum_wait_time = 0
    sum_transfer_time = 0
    sum_penalty_changing_time_slices = 0
    sum_transfers = 0

    for line in line_pool.get_lines():
        sum_line_length += line.compute_length_from_ptn() * frequencies[line]
        sum_line_cost += line.get_cost() * frequencies[line]
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

    m.setObjective(parameters.factor_line_length * sum_line_length +
                   # parameters.factor_line_cost * sum_line_cost +
                   parameters.factor_travel_time * sum_travel_time +
                   parameters.factor_drive_time * sum_drive_time + parameters.factor_transfer_time * sum_transfer_time +
                   parameters.factor_wait_time * sum_wait_time +
                   parameters.factor_penalty_time_slice * sum_penalty_changing_time_slices +
                   parameters.transfer_penalty * sum_transfers)

    # m.update()

    # Add constraints
    logging.debug("Add constraints:")
    # timetabling constraints
    logging.debug("\ttimetabling")
    timetabling_constraints = []
    timetabling_established_lines_constraints = []
    for activity in activities_drive_wait_trans_sync:
        i = activity.get_left_event()
        j = activity.get_right_event()
        const_1 = m.addConstr(pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length >= lines_established[
            activity] * activity.get_lower_bound(), name="timetabling_1_%s" % activity.to_string())
        const_2 = m.addConstr(
            pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length <= activity.get_upper_bound() + (
                1 - lines_established[activity]) * m_1, name="timetabling_2_%s" % activity.to_string())
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
        logging.debug("\tundirected lines")
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
    for line_id in range(1, line_pool.get_max_id() + 1):
        lines = line_pool.get_lines_by_directed_id(line_id)
        for repetition in range(1, line.get_frequency()):
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
        logging.debug('\tlower and upper frequencies')
        lines_by_edges = {}
        for edge in ptn.get_edges():
            lines_by_edges[edge] = []
        for line in line_pool.get_lines():
            for edge in line.get_edges():
                lines_by_edges[edge].append(line)
        for edge in ptn.get_edges():
            if not lines_by_edges[edge]:
                if parameters.check_lower_frequencies and edge.getLowerFrequencyBound() > 0:
                    logging.debug("Edge %s is not covered by any line, lower frequency bound %d!" % (
                        str(edge), edge.getLowerFrequencyBound()))
                    logging.debug("No feasible solution was found!")
                    return
                continue
            sum_frequencies = 0
            for line in lines_by_edges[edge]:
                sum_frequencies += frequencies[line]
            if parameters.check_lower_frequencies:
                const_1 = m.addConstr(sum_frequencies >= edge.getLowerFrequencyBound(),
                                      name="lower_freq_%s" % str(edge.getId()))
            if parameters.check_upper_frequencies:
                const_2 = m.addConstr(sum_frequencies <= edge.getUpperFrequencyBound(),
                                      name="upper_freq_%s" % str(edge.getId()))
            if parameters.write_lp_output:
                upper_lower_frequency_constraints.append(const_1)
                upper_lower_frequency_constraints.append(const_2)

    # Restricting the number of lines
    # logging.debug("\trestricting number of lines")
    # m.addConstr(gurobipy.quicksum(frequencies.values()) <= max_number_of_lines)

    # Only arcs belonging to established lines can be used
    logging.debug("\testablished lines")
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
    # logging.debug("\tarcs belonging to used paths")
    # for activity in ean.get_all_activities():
    #     for od_pair in od.get_active_od_pairs():
    #         for t in range(1, od_pair.get_n_time_slices() + 1):
    #             m.addConstr(arc_used[activity][od_pair][t] <= path_used[od_pair][t])

    # Passenger flow
    logging.debug("\tpassenger flow")
    passenger_flow_constraints = []
    counter = 1
    for od_pair in active_od_pairs:
        logging.debug("\t\tOd-pair: %d" % counter)
        counter += 1

        if parameters.use_preprocessing:
            used_tuple = preprocessor.compute_potentially_used_events_and_activities(ean, ptn, od_pair)
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
                    event_to_check_od = activity.get_left_event()
                    event_to_check_used = activity.get_right_event()
                    if not parameters.use_preprocessing or event_to_check_used in used_events:
                        if event_to_check_od.check_attributes_od(od_pair.get_origin(), od_pair.get_destination(), t):
                            activities_to_check.append(activity)
                for activity in activities_from:
                    event_to_check_od = activity.get_right_event()
                    event_to_check_used = activity.get_left_event()
                    if not parameters.use_preprocessing or event_to_check_used in used_events:
                        if event_to_check_od.check_attributes_od(od_pair.get_origin(), od_pair.get_destination(), t):
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
    logging.debug("\ttime slice")
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

    # Linearization travel time
    logging.debug("\tlinearization travel time")
    linearization_travel_time_constraints = []
    for activity in activities_drive_wait_trans:
        i = activity.get_left_event()
        j = activity.get_right_event()
        for od_pair in od.get_active_od_pairs():
            for t in range(1, od_pair.get_n_time_slices() + 1):
                const_1 = m.addConstr(travel_time_linear[activity][od_pair][t] >= 0,
                                      name="linearization_1_%s_%d_%s" % (od_pair.to_string(), t, activity.to_string()))
                const_2 = m.addConstr(travel_time_linear[activity][od_pair][t] >=
                                      pi[j] - pi[i] + modulo_parameter[activity] * parameters.period_length - (
                                          1 - arc_used[activity][od_pair][t]) * m_4,
                                      name="linearization_2_%s_%d_%s" % (od_pair.to_string(), t, activity.to_string()))
                if parameters.write_lp_output:
                    linearization_travel_time_constraints.append(const_1)
                    linearization_travel_time_constraints.append(const_2)

    # Fixed passenger paths
    use_fixed_passenger_paths_constraints = []
    if parameters.add_fix_passenger_paths:
        logging.debug("\tfixed passenger paths")
        for edge, passengers in ptn_drive_weights.items():
            for activity in activities_drive:
                if activity.belongs_to_edge_drive(edge):
                    const = m.addConstr(
                        pi[activity.get_right_event()] - pi[activity.get_left_event()]
                        + modulo_parameter[activity] * parameters.period_length <=
                        time_est_drive[edge] + m_4 * (1 - lines_established[activity]),
                        name="fixed_pass_paths_drive_%s_%s" % (str(edge), str(activity)))
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
                    if activity.belongs_to_node_transfer(edge_in, edge_out):
                        const = m.addConstr(
                            pi[activity.get_right_event()] - pi[activity.get_left_event()]
                            + modulo_parameter[activity] * parameters.period_length <=
                            time_est_transfer[edge_in][edge_out] + m_4 * (1 - lines_established[activity]),
                            name="fixed_pass_paths_transfer_%s_%s_%s" % (str(edge_in), str(edge_out),
                                                                         activity.to_string()))
                        if parameters.write_lp_output:
                            use_fixed_passenger_paths_constraints.append(const)
    # write lp-file
    if parameters.write_lp_output:
        m.update()
        m.write("LinTimPass.lp")

        dec_file = open('LinTimPass.dec', 'w')
        dec_file.write("PRESOLVED\n0\n")
        dec_file.write("NBLOCKS\n4\n")
        # Block 1: Line planning
        dec_file.write("BLOCK 1\n")
        for const in undirected_lines_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in system_frequency_constraints:
            dec_file.writable(const.ConstrName + "\n")
        for const in upper_lower_frequency_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Block 2: Timetabling
        dec_file.write("BLOCK 2\n")
        for const in timetabling_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in use_fixed_passenger_paths_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Block 3: Passenger flow
        dec_file.write("BLOCK 3\n")
        for const in passenger_flow_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Master-constraints: Coupling
        dec_file.write("MASTERCONSS\n")
        # Line planning + timetabling
        for const in timetabling_established_lines_constraints:
            dec_file.write(const.ConstrName + "\n")
        # Line planing + routing
        for const in established_lines_const:
            dec_file.write(const.ConstrName + "\n")
        # Timetabling + routing
        for const in time_slice_constraints:
            dec_file.write(const.ConstrName + "\n")
        for const in linearization_travel_time_constraints:
            dec_file.write(const.ConstrName + "\n")
        dec_file.close()
    # return

    # Optimization
    logging.debug("Start optimization")
    m.optimize()

    # Check if a feasible solution was found
    if m.SolCount == 0:
        logging.debug("No feasible solution was found!")
        m.computeIIS()
        m.write('lin-tim.ilp')
        return
    else:
        logging.debug("Feasible solution was found!")
    gap = 'gap; ' + str(m.MIPGap)
    solver_time = 'solver_time; ' + str(m.Runtime)
    objective = 'objective; ' + str(m.objVal)
    # adapt path
    a = open(parameters.solver_statistic_file_name, 'w')
    a.write(solver_time + '\n')
    a.write(gap + '\n')
    a.write(objective + '\n')
    a.close()
    logging.debug("End optimization")

    # for v in m.getVars():
    #    logging.debug('%s %g' % (v.varName, v.x))

    # logging.debug("Frequencies:")
    # for line in line_pool.get_lines():
    #    logging.debug("Frequency of line " + line.to_string() + ": %d" % frequencies[line].x)

    logging.debug("Print events")

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

    logging.debug("Print timetable")

    timetable_file = open(parameters.periodic_timetable_filename, 'w')
    timetable_file.write("# event_index; time\n")
    for event in ean.get_events_network():
        if round(frequencies[event.get_line()].x) == 1:
            timetable_file.write("%d; %d\n" % (event.get_event_id(), round(pi[event].x)))
    timetable_file.close()

    logging.debug("Print activities")

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

    logging.debug("Print line concept")

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
    logging.debug("Computing all parts of the objective")
    sum_drive_time = 0
    sum_line_length = 0
    sum_line_cost = 0
    sum_travel_time = 0
    sum_wait_time = 0
    sum_transfer_time = 0
    sum_penalty_changing_time_slices = 0
    sum_transfers = 0

    for line in line_pool.get_lines():
        sum_line_length += line.compute_length_from_ptn() * round(frequencies[line].x)
        sum_line_cost += line.get_cost() * round(frequencies[line].x)
    for od_pair in active_od_pairs:
        for t in range(1, od_pair.get_n_time_slices() + 1):
            sum_travel_time_od_pair = 0
            for activity in activities_drive_wait_trans:
                sum_travel_time += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                sum_travel_time_od_pair += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'trans':
                    sum_transfers += round(arc_used[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                    sum_transfer_time += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'wait':
                    sum_wait_time += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
                if activity.get_activity_type() == 'drive':
                    sum_drive_time += round(travel_time_linear[activity][od_pair][t].x) * od_pair.get_n_passengers(t)
            if sum_travel_time_od_pair == 0:
                logging.debug("No path for od pair %s" % od_pair.to_string())
            for activity in ean.get_activities(['time']):
                time_2 = activity.get_right_event().get_time_2()
                sum_penalty_changing_time_slices += od_pair.get_penalty(t, time_2) * parameters.period_length * \
                                                    round(arc_used[activity][od_pair][t].x) * od_pair.get_n_passengers(t)

    objective_file = open(parameters.objectives_file_name, 'w')
    objective_file.write("sum drive distance vehicles full; %f\n" % sum_line_length)
    objective_file.write("sum line cost; %f\n" % sum_line_cost)
    objective_file.write("sum passenger travel time; %f\n" % sum_travel_time)
    objective_file.write("sum passenger drive time; %f\n" % sum_drive_time)
    objective_file.write("sum passenger wait time; %f\n" % sum_wait_time)
    objective_file.write("sum passenger transfer time; %f\n" % sum_transfer_time)
    objective_file.write("sum penalty changing time slices; %f\n" % sum_penalty_changing_time_slices)
    objective_file.write("number of transfers; %f\n" % sum_transfers)
    objective_file.write("percieved travel time; %f\n" % (
        parameters.factor_drive_time * sum_drive_time + parameters.factor_wait_time * sum_wait_time +
        parameters.factor_transfer_time * sum_transfer_time + parameters.transfer_penalty * sum_transfers))
    objective_file.write('Total value: %g' % m.objVal)
    objective_file.close()
