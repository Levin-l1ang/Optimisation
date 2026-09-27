import logging
import sys
import math
from math import ceil
import numpy as np
import csv

from core.exceptions.config_exceptions import ConfigNoFileNameGivenException, ConfigKeyNotFoundException
from core.exceptions.solver_exceptions import  SolverFoundNoFeasibleSolutionException
from core.io.config import ConfigReader
from core.io.od import ODReader
from core.io.ptn import PTNReader, PTNWriter
from core.model.impl.mapOD import MapOD
from core.solver.generic_solver_interface import Solver, Status, SolverType, VariableType, ConstraintSense, OptimizationSense, DoubleAttribute, IntAttribute
from core.solver.solver_parameters import SolverParameters
from core.algorithm.dijkstra import Dijkstra

logger = logging.getLogger(__name__)

# Use Dijkstra to calculate shortest distances for each OD pair
# Returns a dictionary "distances", where distance[o.getId()][d.getId()] is the shortest distance from o to d.
def calculate_distances_from_graph(ptn):
    distances = {}
    for node in ptn.getNodes():
        dijkstra = Dijkstra(ptn,node,lambda y: y.getLength())
        d = {}
        for node2 in ptn.getNodes():
            if node2.getId() > node.getId() and od.getValue(node.getId(),node2.getId()) + od.getValue(node2.getId(),node.getId())>0:
                shortest_distance = dijkstra.computeShortestPath(node2)
                d[node2.getId()] = shortest_distance
        distances[node.getId()] = d
    return(distances)

# Assuming that "distances1" contains the shortest distances for each OD pair
# in the full graph, and "distances2" contains the shortest distances for a subgraph,
# return the maximum detour factor in the subgraph
def calculate_eps(distances1,distances2):
    eps_max = 0
    for (i,d) in distances1.items():
        for (j,d2) in d.items():
            eps_current = distances2[i][j]/d2
            if eps_current > eps_max:
                eps_max = eps_current
    return eps_max - 1

# Calculate the building costs of the minimum spanning tree
def calculate_minimum_spanning_tree_cost(ptn):
    cost = 0
    nodes_in_MST = [ptn.getNodes()[0].getId()]
    while len(nodes_in_MST) < len(ptn.getNodes()):
        lowest_cost = math.inf
        new_node = None
        for edge in ptn.getEdges():
            if (edge.getRightNode().getId() in nodes_in_MST) != (edge.getLeftNode().getId() in nodes_in_MST):
                if edge.getLength() < lowest_cost:
                    if (edge.getRightNode().getId() in nodes_in_MST):
                        new_node = edge.getLeftNode().getId()
                    else:
                        new_node = edge.getRightNode().getId()
                    lowest_cost = edge.getLength()
        nodes_in_MST.append(new_node)
        cost += lowest_cost
    return cost

# Calculate the bulding costs of the subgraph where x[e]=1 if edge e is
# included in the subgraph and x[e]=0 otherwise
def calculate_building_costs(ptn,x):
    sum = 0
    for edge in ptn.getEdges():
        sum += edge.getLength()*x[edge.getId()]
    return sum

# Greedy spanner heuristic
def calculate_greedy_spanner(ptn, epsilon):
    edges = []
    lengths = []
    for edge in ptn.getEdges():
        edges.append(edge)
        lengths.append(edge.getLength())
    edges_sorted = [edge for _, edge in sorted(zip(lengths, edges), key=lambda pair: pair[0])]
    ptn_new = PTNReader.read(read_links=False)
    x_bar = {}
    for edge in edges_sorted:
        dijkstra = Dijkstra(ptn_new,edge.getLeftNode(),lambda y: y.getLength())
        if dijkstra.computeShortestPath(edge.getRightNode()) > epsilon*edge.getLength():
            ptn_new.addEdge(edge)
            x_bar[edge.getId()] = 1
        else:
            x_bar[edge.getId()] = 0
    return x_bar

# Add general valid inequalities to the spanner MIP formulation
# These seem to improve computational performance significantly
def add_gen_cuts(ptn, model, x):
    # The number of edges in the graph must be at least |V|-1, otherwise it is not connected
    sum_constraint2 = model.createExpression()
    for edge in ptn.getEdges():
        sum_constraint2.add(x[edge.getId()])
    model.addConstraint(sum_constraint2, ConstraintSense.GREATER_EQUAL, len(ptn.getNodes())-1, "vi1")

    # Each node must have at least one edge connected to it
    for node in ptn.getNodes():
        all_nodes_connected = model.createExpression()
        for edge in ptn.getOutgoingEdges(node):
            all_nodes_connected.add(x[edge.getId()])
        model.addConstraint(all_nodes_connected, ConstraintSense.GREATER_EQUAL, 1, "vi2_%d"%(node.getId()))

    # There cannot be edges for which no other edges connect to either of its endpoints
    for edge in ptn.getEdges():
        edge_consistency = model.createExpression()
        edge_consistency.multiAdd(-1,x[edge.getId()])
        for edge1 in ptn.getOutgoingEdges(edge.getLeftNode()):
            edge_consistency.add(x[edge1.getId()])
        for edge2 in ptn.getOutgoingEdges(edge.getRightNode()):
            edge_consistency.add(x[edge2.getId()])
        model.addConstraint(edge_consistency, ConstraintSense.GREATER_EQUAL, 0, "vi3_%d"%(edge.getId()))

# Add building cost to the model as either an objective or constraint, if so specified in the config files
def add_building_cost(ptn, model, x, config):
    if config.getStringValue("load_generator_building_cost") != "none":
        building_cost = model.createExpression()
        for edge in ptn.getEdges():
            building_cost.multiAdd(edge.getLength(),x[edge.getId()])
        if config.getStringValue("load_generator_building_cost") == "obj":
            model.setObjective(building_cost,OptimizationSense.MINIMIZE)
        elif config.getStringValue("load_generator_building_cost") == "cons":
            model.addConstraint(building_cost, ConstraintSense.LESS_EQUAL, config.getDoubleValue("load_generator_max_building_cost")*config.getDoubleValue("gen_conversion_length"),"cn")
        else:
            logger.error("load_generator_building_cost is set to %s, should be one of \"obj\", \"cons\" or \"none\"." %(config.getStringValue("load_generator_building_cost")))

# Add the flow variables and their respective constraints and objectives.
def create_flow_constraints(model,ptn,od,x,parameters):
    flow_forward = {}
    flow_backward = {}
    detour_flag = (config.getStringValue("load_generator_detour_factor") == "none")
    for node in ptn.getNodes():
        f_forward = {}
        f_backward = {}
        total_demand = sum(od.getValue(node.getId(),n.getId()) + od.getValue(n.getId(),node.getId()) for n in ptn.getNodes() if n.getId() > node.getId())
        for edge in ptn.getEdges():
            name_forward = "f%d~%d_%d"% (node.getId(),edge.getRightNode().getId(),edge.getLeftNode().getId())
            name_backward = "f%d~%d_%d"% (node.getId(),edge.getLeftNode().getId(),edge.getRightNode().getId())
            #Flow right to left
            f_forward[edge.getId()] = model.addVariable(0, total_demand, VariableType.CONTINUOUS, 0, name_forward)
            #Flow left to right
            f_backward[edge.getId()] = model.addVariable(0, total_demand, VariableType.CONTINUOUS, 0, name_backward)
            if detour_flag:
                # Big-M style constraints for deactivating edges that are not a part of the solution
                cn1 = model.createExpression()
                cn1.add(f_forward[edge.getId()])
                cn1.multiAdd(-total_demand, x[edge.getId()])
                model.addConstraint(cn1, ConstraintSense.LESS_EQUAL, 0, "f_ub%d~%d_%d"% (node.getId(),edge.getRightNode().getId(),edge.getLeftNode().getId()))
                cn2 = model.createExpression()
                cn2.add(f_backward[edge.getId()])
                cn2.multiAdd(-total_demand, x[edge.getId()])
                model.addConstraint(cn2, ConstraintSense.LESS_EQUAL, 0, "f_ub%d~%d_%d"% (node.getId(),edge.getLeftNode().getId(),edge.getRightNode().getId()))
        flow_forward[node.getId()] = f_forward
        flow_backward[node.getId()] = f_backward

        # Flow balance constraints
        for node2 in ptn.getNodes():
            cn4 = model.createExpression()
            for edge in ptn.getIncidentEdges(node2):
                if node2.getId() == edge.getRightNode().getId():
                    cn4.multiAdd(-1,f_backward[edge.getId()])
                    cn4.multiAdd(1,f_forward[edge.getId()])
                else:
                    cn4.multiAdd(-1,f_forward[edge.getId()])
                    cn4.multiAdd(1,f_backward[edge.getId()])
            if node2.getId() > node.getId():
                # node2 is a destination node, incoming minus outgoing flow equals demand from node to node 2 plus demand from node2 to node
                rhs = od.getValue(node.getId(),node2.getId()) + od.getValue(node2.getId(),node.getId())
            elif node2.getId() == node.getId():
                # node2 is the source node, outgoing minus incoming flow equals total demand from node to nodes with larger indices
                rhs = -total_demand
            else: # node2.getId() < node.getId()
                # the index of node2 is smaller than that of the source node, and demand is assumed to go from smaller to larger indices only
                rhs = 0
            model.addConstraint(cn4, ConstraintSense.EQUAL, rhs, "flow_balance%d_%d"% (node.getId(),node2.getId()))

    # Total travel time
    if config.getStringValue("load_generator_travel_time") != "none":
        travel_time = model.createExpression()
        for node in ptn.getNodes():
            f_forward = flow_forward[node.getId()]
            f_backward = flow_backward[node.getId()]
            for edge in ptn.getEdges():
                travel_time.multiAdd(edge.getLength(),f_forward[edge.getId()])
                travel_time.multiAdd(edge.getLength(),f_backward[edge.getId()])
        if config.getStringValue("load_generator_travel_time") == "obj":
            model.setObjective(travel_time,OptimizationSense.MINIMIZE)
        elif config.getStringValue("load_generator_travel_time") == "cons":
            model.addConstraint(travel_time, ConstraintSense.LESS_EQUAL, config.getDoubleValue("load_generator_max_travel_time")*config.getDoubleValue("gen_conversion_length"),"traveltime")
        else:
            logger.error("load_generator_travel_time is set to %s, should be one of \"obj\", \"cons\" or \"none\"." %(config.getStringValue("load_generator_travel_time")))
    return (flow_forward, flow_backward)


# Maximum detour factor
def create_detour_constraints(model,ptn,od,x,parameters):
    if config.getStringValue("load_generator_detour_factor") != "none":
        shortest_distances = calculate_distances_from_graph(ptn)
        epsilon = model.addVariable(0, math.inf, VariableType.CONTINUOUS, 0, "epsilon")
        I_y = {}
        I_y_reverse = {}
        for node in ptn.getNodes():
            vi3 = model.createExpression()
            I_y_node = {}
            I_y_reverse_node = {}
            for edge in ptn.getEdges():
                I_y_node[edge.getId()] = model.addVariable(0, 1, VariableType.BINARY, 0, "I_y%d_%d"% (node.getId(),edge.getId()))
                I_y_reverse_node[edge.getId()] = model.addVariable(0, 1, VariableType.BINARY, 0, "I_y_reverse%d_%d"% (node.getId(),edge.getId()))
                vi1 = model.createExpression()
                vi1.add(I_y_node[edge.getId()])
                vi1.add(I_y_reverse_node[edge.getId()])
                model.addConstraint(vi1, ConstraintSense.LESS_EQUAL, x[edge.getId()],"detour_vi1%d_%d"%(node.getId(), edge.getId()))
                vi3.add(I_y_node[edge.getId()])
                vi3.add(I_y_reverse_node[edge.getId()])
                if sum(od.getValue(node.getId(),n.getId())+od.getValue(n.getId(),node.getId()) for n in ptn.getNodes() if n.getId() > node.getId()) > 0:
                    con = model.createExpression()
                    con_reverse = model.createExpression()
                    con.add(I_y_node[edge.getId()])
                    con_reverse.add(I_y_reverse_node[edge.getId()])
                    con.multiAdd(-1/sum(od.getValue(node.getId(),n.getId())+od.getValue(n.getId(),node.getId()) for n in ptn.getNodes() if n.getId() > node.getId()), flow_forward[node.getId()][edge.getId()])
                    con_reverse.multiAdd(-1/sum(od.getValue(node.getId(),n.getId())+od.getValue(n.getId(),node.getId()) for n in ptn.getNodes() if n.getId() > node.getId()), flow_backward[node.getId()][edge.getId()])
                    model.addConstraint(con, ConstraintSense.GREATER_EQUAL, 0,"I_y_con%d_%d"% (node.getId(),edge.getId()))
                    model.addConstraint(con_reverse, ConstraintSense.GREATER_EQUAL, 0,"I_y_reverse_con%d_%d"% (node.getId(),edge.getId()))
            I_y[node.getId()] = I_y_node
            I_y_reverse[node.getId()] = I_y_reverse_node
            model.addConstraint(vi3, ConstraintSense.GREATER_EQUAL, sum(1 for n in ptn.getNodes() if n.getId() > node.getId()), "detour_vi3%d"%(node.getId()))
        for edge in ptn.getEdges():
            vi4 = model.createExpression()
            for node in ptn.getNodes():
                vi4.add(I_y[node.getId()][edge.getId()])
                vi4.add(I_y_reverse[node.getId()][edge.getId()])
            model.addConstraint(vi4, ConstraintSense.GREATER_EQUAL, x[edge.getId()],"detour_vi4%d"%(edge.getId()))
        r = {}
        for s in ptn.getNodes():
            r[s.getId()] = {}
            dijkstra = Dijkstra(ptn,s,lambda y: y.getLength())
            for v in ptn.getNodes():
                if v.getId() == s.getId():
                    r[s.getId()][v.getId()] = model.addVariable(0, 0, VariableType.CONTINUOUS, 0, "r_%d_%d"%(s.getId(), v.getId()))
                else:
                    r[s.getId()][v.getId()] = model.addVariable(dijkstra.computeShortestPath(v), sum([edge.getLength() for edge in ptn.getEdges()]), VariableType.CONTINUOUS, 0, "r_%d_%d"%(s.getId(), v.getId()))
                    if od.getValue(s.getId(),v.getId()) > 0:
                        vi2 = model.createExpression()
                        for edge in ptn.getIncidentEdges(v):
                            if v.getId() == edge.getRightNode().getId():
                                vi2.multiAdd(1,I_y[s.getId()][edge.getId()])
                            else:
                                vi2.multiAdd(1,I_y_reverse[s.getId()][edge.getId()])
                        model.addConstraint(vi2, ConstraintSense.GREATER_EQUAL, 1, "detour_vi2%d_%d"% (s.getId(),v.getId()))
        M = sum([edge.getLength() for edge in ptn.getEdges()])
        for node in ptn.getNodes():
            for edge in ptn.getEdges():
                con1 = model.createExpression()
                con1.add(r[node.getId()][edge.getRightNode().getId()])
                con1.multiAdd(-1, r[node.getId()][edge.getLeftNode().getId()])
                con1.multiAdd(-M, I_y[node.getId()][edge.getId()])
                model.addConstraint(con1, ConstraintSense.GREATER_EQUAL, edge.getLength()-M,"I_y_bigM%d_%d"% (node.getId(),edge.getId()))
                con2 = model.createExpression()
                con2.add(r[node.getId()][edge.getLeftNode().getId()])
                con2.multiAdd(-1, r[node.getId()][edge.getRightNode().getId()])
                con2.multiAdd(-M, I_y_reverse[node.getId()][edge.getId()])
                model.addConstraint(con2, ConstraintSense.GREATER_EQUAL, edge.getLength()-M,"I_y_reverse_bigM%d_%d"% (node.getId(),edge.getId()))
        for node in ptn.getNodes():
            for node2 in ptn.getNodes():
                if node2.getId() > node.getId() and od.getValue(node.getId(),node2.getId())+od.getValue(node2.getId(),node.getId())>0:
                    con3 = model.createExpression()
                    con3.add(r[node.getId()][node2.getId()])
                    con3.multiAdd(-shortest_distances[node.getId()][node2.getId()], epsilon)
                    model.addConstraint(con3, ConstraintSense.LESS_EQUAL, shortest_distances[node.getId()][node2.getId()],"detour_con%d-%d"%(node.getId(),node.getId()))
        if config.getStringValue("load_generator_detour_factor") == "obj":
            epsilon_expr = model.createExpression()
            epsilon_expr.add(epsilon)
            model.setObjective(epsilon_expr,OptimizationSense.MINIMIZE)
        elif config.getStringValue("load_generator_detour_factor") == "cons":
            model.addConstraint(epsilon, ConstraintSense.LESS_EQUAL, config.getDoubleValue("load_generator_max_detour"),"maxdetour")
        else:
            logger.error("load_generator_detour_factor is set to %s, should be one of \"obj\", \"cons\" or \"none\"." %(config.getStringValue("load_generator_detour_factor")))

        return epsilon

def set_MIP_start(ptn, model, x, config):
    if config.getStringValue("load_generator_detour_factor") != "none":
        if config.getStringValue("load_generator_building_cost") == "cons":
            max_cost = config.getDoubleValue("load_generator_max_building_cost")
        else:
            max_cost = math.inf
        # Initialize
        epsilon = 20 # An arbitrary starting value
        x_bar = calculate_greedy_spanner(ptn,epsilon)
        x_bar_temp = x_bar.copy() # Initialize the temporary array.
        b_cost = calculate_building_costs(ptn,x_bar_temp)
        # Decrease epsilon until building cost exceeds limit or epsilon is sufficiently small
        # Note: this might not reach the upper bound for epsilon given in the config even if the problem is feasible
        while b_cost <= max_cost and epsilon > 1.1:
            x_bar = x_bar_temp.copy() # The solution from last iteration was feasible, update x_bar
            epsilon *= 0.9 # Decrease value of epsilon
            x_bar_temp = calculate_greedy_spanner(ptn,epsilon)
            b_cost = calculate_building_costs(ptn,x_bar_temp)
        for edge in ptn.getEdges():
            model.setStartValue(x[edge.getId()], x_bar[edge.getId()])
    else: # detour factor not used in the model
        # TODO: implement some heuristic
        # Maybe just MST or full graph, depending on the configuration?
        pass

if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()

    ################################
    #### Reading configuration #####
    ################################
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    capacity = config.getIntegerValue("gen_passengers_per_vehicle")
    uniform_upper_bounds = config.getBooleanValue("load_generator_fix_upper_frequency")
    global_upper_bound = -1
    if uniform_upper_bounds:
        global_upper_bound = config.getIntegerValue("load_generator_fixed_upper_frequency")
    gen_passengers_per_vehicle = config.getIntegerValue("gen_passengers_per_vehicle")
    logger.info("Finished reading configuration")
    ###############################
    ##### Reading input data ######
    ###############################
    logger.info("Begin reading input data")
    ptn = PTNReader.read(read_loads=not uniform_upper_bounds)
    if ptn.isDirected():
        logger.error("Directed graphs not currently supported for spanner MIPs")
    od = ODReader.read(MapOD())
    logger.info("Finished reading input data")
    parameters = SolverParameters(config, "load_generator_")
    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()
    parameters.setSolverParameters(model)
    logger.info("Begin execution of demand generation with spanners")

    # Check the number of objectives
    num_objectives = (config.getStringValue("load_generator_building_cost") == "obj") + (config.getStringValue("load_generator_travel_time") == "obj") + (config.getStringValue("load_generator_detour_factor") == "obj")
    if num_objectives == 0:
        logger.warning("No objective was given, finding a feasible solution.")
    elif num_objectives > 1:
        logger.error("Too many (%d) objectives given, multi-objective models not supported."%(num_objectives))

    # Reset loads and frequency bounds
    for edge in ptn.getEdges():
        edge.setLoad(0)
        edge.setLowerFrequencyBound(0)
        if uniform_upper_bounds:
            edge.setUpperFrequencyBound(global_upper_bound)

    ###########################
    ##### Initialization ######
    ###########################

    logger.info("Initialize decision variables")
    x = {}
    # Variables x for all edges: x[e]=1 if edge e is used
    for edge in ptn.getEdges():
        x[edge.getId()] = model.addVariable(0, 1, VariableType.BINARY, 0, "x%d"% (edge.getId()))

    add_building_cost(ptn, model, x, config)

    # General valid inequalities for x
    if config.getBooleanValue("load_generator_gen_cuts"):
        add_gen_cuts(ptn, model, x)

    # Flow constraints
    logger.info("Construct flow balancing constraints")
    (flow_forward, flow_backward) = create_flow_constraints(model,ptn,od,x,parameters)
    # Maximum detour constraints
    epsilon = create_detour_constraints(model,ptn,od,x,parameters)

    set_MIP_start(ptn, model, x, config)

    if parameters.writeLpFile():
        logger.debug("Write lp file")
        model.write("basis/spannerLoadGeneration.lp")
    logger.info("Finished compilation of the optimization model, solution starting")
    model.solve()
    logger.info("Model solved in %f seconds" %(model.getDoubleAttribute(DoubleAttribute.RUNTIME)))
    status = model.getStatus()
    logger.info("Status of the solution: %s"% (status))

    ###########################
    #### Check feasibility ####
    ###########################

    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:

        ###########################
        ##### Extract results #####
        ###########################

        logger.info(f"Status: {status}, solution time: {model.getDoubleAttribute(DoubleAttribute.RUNTIME)}, gap: {model.getDoubleAttribute(DoubleAttribute.MIP_GAP)}, value: {model.getDoubleAttribute(DoubleAttribute.OBJ_VAL)}")

        MST_cost = calculate_minimum_spanning_tree_cost(ptn)
        x_bar = {}
        for edge in ptn.getEdges():
            x_bar[edge.getId()] = model.getValue(x[edge.getId()])
        b_cost = calculate_building_costs(ptn,x_bar)
        logger.info("Building costs of the optimal solution: %f" %(b_cost))
        logger.info("Building costs of minimum spanning tree: %f" %(MST_cost))

        shortest_distances_full_graph = calculate_distances_from_graph(ptn)
        lw_load = 0
        b_costs = 0
        # Remove unused edges from PTN
        for edge in ptn.getEdges():
            if model.getValue(x[edge.getId()]) < 0.1:
                ptn.removeEdge(edge)

        forward_load = dict.fromkeys([edge.getId() for edge in ptn.getEdges()], 0)
        backward_load = dict.fromkeys([edge.getId() for edge in ptn.getEdges()], 0)
        #  Store load (number of people using an edge) and minimal frequency (ceil(load/capacity)) in edge.
        #  Remove edges with minimal frequency 0.
        for node in ptn.getNodes():
            dijkstra = Dijkstra(ptn,node,lambda y: y.getLength())
            dijkstra.computeShortestPaths()
            for node2 in ptn.getNodes():
                if node.getId() == node2.getId():
                    continue
                passengers = od.getValue(node.getId(),node2.getId())
                path = dijkstra.getPath(node2)

                if path is None:
                    raise RuntimeError(f'Graph is invalid: no path found between nodes {node.getId()} and {node2.getId()}.')

                edges = path.getEdges()
                start_node = node
                for edge in edges:
                    if edge.getLeftNode().getId() == start_node.getId():
                        forward_load[edge.getId()] = forward_load[edge.getId()] + passengers
                        start_node = edge.getRightNode()
                    elif edge.getRightNode().getId() == start_node.getId():
                        backward_load[edge.getId()] = backward_load[edge.getId()] + passengers
                        start_node = edge.getLeftNode()

        for edge in ptn.getEdges():
            b_costs += edge.getLength()
            load = max(forward_load[edge.getId()], backward_load[edge.getId()])
            lw_load += edge.getLength()*load
            if load > 0.001:
                edge.setLoad(load)
                edge.setLowerFrequencyBound(ceil(load/gen_passengers_per_vehicle))
            if uniform_upper_bounds:
                edge.setUpperFrequencyBound(global_upper_bound)

        shortest_distances_subgraph = calculate_distances_from_graph(ptn)
        eps_max = calculate_eps(shortest_distances_full_graph, shortest_distances_subgraph)
        logger.info("The graph is (1 + %f)-spanner: " %(eps_max))
        logger.info("Length weighted loads of the optimal solution: %f " %(round(lw_load,2)))

        logger.info("Finished execution of demand generation with spanners")
        logger.info("Begin writing output data")
        PTNWriter.write(ptn, write_stops=False, write_loads=True, write_links=config.getBooleanValue("load_generator_remove_unused_edges"))
        logger.info("Finished writing output data")
        model.dispose()
        solver.dispose()

    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            model.computeIIS("basis/spannerLoadGeneration.ilp")
        model.dispose()
        solver.dispose()
        SolverFoundNoFeasibleSolutionException()