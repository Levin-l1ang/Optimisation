import logging
import networkx as nx
import time
import math
from typing import List, Dict, Tuple

from core.util.networkx import convert_graph_to_networkx
from core.model.ptn import Stop, Link
from core.model.graph import Graph
from core.model.tariff import PriceMatrix, Zone, ZonePrices
from core.model.tariff_types import TariffObjectiveType, TariffZoneCountingType, TariffZoneSymmetryOption
from core.exceptions.solver_exceptions import SolverFoundNoFeasibleSolutionException
from core.solver.solver_parameters import SolverParameters
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, IntAttribute, Model, DoubleAttribute

logger = logging.getLogger(__name__)

def solve(enforce_all_zones: bool, connected_zones: bool, no_elongation: bool, no_stopover: bool, counting_type: TariffZoneCountingType,
          nodes: List[Stop], paths_between_nodes, adjacent_node_pairs: List[Tuple[Stop, Stop]], reference_prices: PriceMatrix,
          upper_bound_zones: int, weights: Dict[Tuple[int,int],float], taf_obj_type: TariffObjectiveType, symmetry_breaking: TariffZoneSymmetryOption,
          parameters: SolverParameters) -> Tuple[float, List[int|List[int]], ZonePrices, PriceMatrix]:
    """
    solves the zone tariff problem for given config values with the help of gurobi.
    :param enforce_all_zones: Boolean. If true, then every zone must contain at least one stop.
    :param connected_zones: Boolean. If true, then every zone must be connected.
    :param no_elongation: Boolean. If true, then no-elongation property is enforced.
    :param no_stopover: Boolean. If true, then no-stopover property is enforced.
    :param counting_type: defines the zone counting mode.
    :param nodes: List of nodes
    :param paths_between_nodes: Dict of dicts, where entry [v_id][w_id] is a v-w-path given as list of nodes (single counting) or list of edges (multiple counting)
    :param adjacent_node_pairs: List of directed/ordered node pairs that are adjacent (contains pairs in both directions)
    :param reference_prices: PriceMatrix, where [v_id][w_id] is the reference price for traveling from v to w
    :param upper_bound_zones: Maximum number of allowed zones
    :param weights: dict of weights used in the objective function
    :param taf_obj_type: defines the objective function
    :param symmetry_breaking: controls which option is used for symmetry breaking
    :param parameters: Solver parameter informations: number of threads, timelimit, MIP-gap and solver type
    :return: optimal objective value, computed zones given as list of pairs zone Id and List of stops, zone_prices given as list ordered by number of visited zones and full price matrix
    """

    logger.debug("Preprocessing")
    number_of_nodes = len(nodes)

    # maximum length of a path
    max_number_nodes_on_path = 0
    for node1 in nodes:
        for node2 in nodes:
            if node1!=node2:
                if len(paths_between_nodes[node1.getId()][node2.getId()]) > max_number_nodes_on_path:
                    max_number_nodes_on_path = len(paths_between_nodes[node1.getId()][node2.getId()])
    if counting_type == TariffZoneCountingType.MULTIPLE:
        max_number_nodes_on_path = max_number_nodes_on_path + 1

    # origin-destination pairs (of all nodes)
    node_pairs = [(node1, node2) for node1 in nodes for node2 in nodes if node1.getId() != node2.getId()]

    # maximum price in reference_prices
    max_price = 0
    for node1, node2, price in reference_prices.getAllEntries():
        if price > max_price:
            max_price = price
    big_m = max_price + 1

    if counting_type == TariffZoneCountingType.SINGLE:
        length_of_price_list = upper_bound_zones
    elif counting_type == TariffZoneCountingType.MULTIPLE:
        length_of_price_list = max_number_nodes_on_path



    logger.debug("Initialize model")

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Set model parameters")
    parameters.setSolverParameters(model)

    logger.debug("Add variables")
    var_assign_nodes_to_zones = dict()
    for node in nodes:
        for zone_id in range(upper_bound_zones):
            var_assign_nodes_to_zones[(node.getId(), zone_id)] = model.addVariable(var_type=VariableType.BINARY, name=f'x_{node.getId()}_{zone_id}')

    if connected_zones:
        var_sources = dict()
        var_flows = dict()
        for node1 in nodes:
            var_sources[node1.getId()] = model.addVariable(var_type=VariableType.BINARY, name=f's_{node1.getId()}')  # 1 if node is assigned to the supersource
            var_flows[('supersource', node1.getId())] = model.addVariable(lower_bound=0, upper_bound=number_of_nodes, var_type=VariableType.CONTINUOUS, name=f'f_{"source"}_{node1.getId()}') # works also with var_type=INTEGER
            for node2 in nodes:
                var_flows[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=0, upper_bound=number_of_nodes, var_type=VariableType.CONTINUOUS, name=f'f_{node1.getId()}_{node2.getId()}') # works also with var_type=INTEGER

    if counting_type == TariffZoneCountingType.SINGLE:
        var_zone_on_path = dict()
        for node1, node2 in node_pairs:
            for zone_id in range(upper_bound_zones):
                var_zone_on_path[(node1.getId(), node2.getId(), zone_id)] = model.addVariable(
                    var_type=VariableType.BINARY,
                    name=f'c_{node1.getId()}_{node2.getId()}_{zone_id}')  # 1 if path from v to w visits zone z
    elif counting_type == TariffZoneCountingType.MULTIPLE:
        var_nodes_in_different_zones = dict()
        for node1, node2 in adjacent_node_pairs:
            var_nodes_in_different_zones[(node1.getId(), node2.getId())] = model.addVariable(
                var_type=VariableType.BINARY,
                name=f'c_{node1.getId()}_{node2.getId()}')  # 1 if node v and w of edge {v,w} are in different zones

    var_number_visited_zones_on_path = dict()
    for node1, node2 in node_pairs:
        for number_visited_zones in range(1, length_of_price_list + 1):
            var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)] = model.addVariable(
                var_type=VariableType.BINARY, name=f'd_{node1.getId()}_{node2.getId()}_{number_visited_zones}')  # 1 if path from v to w visits k zones

    var_price_number_zones = dict()
    for number_visited_zones in range(1, length_of_price_list + 1):
        var_price_number_zones[number_visited_zones] = model.addVariable(lower_bound=0, upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f'p_{number_visited_zones}')  # price for traveling trough k zones

    var_price_between_nodes = dict()
    var_diff_prices_new_ref = dict()
    var_diff_prices_new_ref_pos = dict()
    var_diff_prices_new_ref_neg = dict()
    for node1, node2 in node_pairs:
        var_price_between_nodes[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=0, upper_bound=float('inf'),  var_type=VariableType.CONTINUOUS, name=f'pi_{node1.getId()}_{node2.getId()}')
        var_diff_prices_new_ref[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=-float('inf'), upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f'diff_prices_{node1.getId()}_{node2.getId()}')
        var_diff_prices_new_ref_pos[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=0, upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f'diff_prices_pos_{node1.getId()}_{node2.getId()}')
        var_diff_prices_new_ref_neg[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=0, upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f'diff_prices_neg_{node1.getId()}_{node2.getId()}')


    logger.debug("Add constraints")

    # assign each node to a zone
    for node in nodes:
        model.addConstraint(model.createExpression().quicksum(var_assign_nodes_to_zones[(node.getId(), zone_id)] for zone_id in range(upper_bound_zones)), ConstraintSense.EQUAL, 1, name=f"assign_nodes_{node.getId()}")

    if enforce_all_zones:
        create_constraints_every_zone_has_nodes(model, var_assign_nodes_to_zones, nodes, upper_bound_zones)

    if connected_zones:
        create_constraints_connected_zones(model, var_assign_nodes_to_zones, var_sources, var_flows, nodes,
                                        number_of_nodes, node_pairs, adjacent_node_pairs, upper_bound_zones)

    if no_elongation:
        create_constraints_no_elongation(model, var_price_number_zones, length_of_price_list)

    if no_stopover:
        create_constraints_no_stopover(model, var_price_number_zones, length_of_price_list, counting_type)

    if counting_type == TariffZoneCountingType.SINGLE:
        create_constraints_counting_zones_single_counting(model, var_assign_nodes_to_zones, var_zone_on_path,
                                                        var_number_visited_zones_on_path, number_of_nodes, node_pairs,
                                                        paths_between_nodes, upper_bound_zones)
    elif counting_type == TariffZoneCountingType.MULTIPLE:
        create_constraints_counting_zones_multiple_counting(model, var_assign_nodes_to_zones,
                                                            var_nodes_in_different_zones,
                                                            var_number_visited_zones_on_path, node_pairs,
                                                            paths_between_nodes, adjacent_node_pairs, upper_bound_zones,
                                                            max_number_nodes_on_path)

    # set price according to number of traversed zones
    for node1, node2 in node_pairs:
        for number_visited_zones in range(1, length_of_price_list + 1):
            model.addConstraint(var_price_between_nodes[(node1.getId(), node2.getId())], ConstraintSense.LESS_EQUAL, var_price_number_zones[number_visited_zones] + (
                1 - var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)]) * big_m, name=f"price_1_{node1.getId()}_{node2.getId()}_{number_visited_zones}")
            model.addConstraint(var_price_number_zones[number_visited_zones], ConstraintSense.LESS_EQUAL, var_price_between_nodes[(node1.getId(), node2.getId())] + (
                1 - var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)]) * big_m, name=f"price_2_{node1.getId()}_{node2.getId()}_{number_visited_zones}")

    # resolve absolute value in objective
    for node1, node2 in node_pairs:
        model.addConstraint(var_diff_prices_new_ref[(node1.getId(), node2.getId())], ConstraintSense.EQUAL, var_price_between_nodes[
            (node1.getId(), node2.getId())] - reference_prices.getValue(node1.getId(), node2.getId()), name=f"lin_abs_1_{node1.getId()}_{node2.getId()}")
        model.addConstraint(var_diff_prices_new_ref_pos[(node1.getId(), node2.getId())] - var_diff_prices_new_ref_neg[(node1.getId(), node2.getId())], ConstraintSense.EQUAL,
            var_diff_prices_new_ref[(node1.getId(), node2.getId())], name=f"lin_abs_2_{node1.getId()}_{node2.getId()}")

    if symmetry_breaking == TariffZoneSymmetryOption.A:
        # symmetry breaking -- option 2 -- seems to be useful
        # the i-th node is only allowed to be in the first i zones. In particular, the first node is in the first zone.
        for index, node in enumerate(nodes):
            for zone_id in range(index + 1, upper_bound_zones):
                model.addConstraint(var_assign_nodes_to_zones[node.getId(), zone_id], ConstraintSense.EQUAL, 0, name=f"symmetry_A_1_{node.getId()}_{zone_id}")

        # symmetry breaking -- option 3
        for index in range(1,len(nodes)):
            for zone_id in range(1,upper_bound_zones):
                model.addConstraint(var_assign_nodes_to_zones[nodes[index].getId(), zone_id], ConstraintSense.LESS_EQUAL,
                    model.createExpression().quicksum(var_assign_nodes_to_zones[nodes[i].getId(), zone_id - 1] for i in range(index)), name=f"symmetry_A_2_{index}_{zone_id}")

    elif symmetry_breaking == TariffZoneSymmetryOption.B:
        # # symmetry breaking -- option 1 -- seems to be way slower than without
        # zones are ordered by size
        for zone_id in range(upper_bound_zones - 1):
            model.addConstraint(model.createExpression().quicksum(var_assign_nodes_to_zones[(node.getId(), zone_id)] for node in nodes), ConstraintSense.GREATER_EQUAL,
                model.createExpression().quicksum(var_assign_nodes_to_zones[(node.getId(), zone_id + 1)] for node in nodes), name=f"symmetry_B_{zone_id}")

    elif symmetry_breaking == TariffZoneSymmetryOption.NONE:
        pass

    logger.debug("Set Objective Function")
    if taf_obj_type == TariffObjectiveType.SUM_ABSOLUTE_DEVIATION:
        model.setObjective(model.createExpression().quicksum(weights[(node1.getId(), node2.getId())] * (var_diff_prices_new_ref_pos[(node1.getId(), node2.getId())] + var_diff_prices_new_ref_neg[
            (node1.getId(), node2.getId())]) for node1, node2 in node_pairs), OptimizationSense.MINIMIZE)
    elif taf_obj_type == TariffObjectiveType.MAX_ABSOLUTE_DEVIATION:
        var_linearize_max = model.addVariable(lower_bound=0, var_type=VariableType.CONTINUOUS, name='linearization_var_for_max_in_objective')
        for node1, node2 in node_pairs:
            model.addConstraint(var_linearize_max, ConstraintSense.GREATER_EQUAL, weights[(node1.getId(), node2.getId())] * (var_diff_prices_new_ref_pos[
                (node1.getId(), node2.getId())] + var_diff_prices_new_ref_neg[(node1.getId(), node2.getId())]), name=f"lin_obj_{node1.getId()}_{node2.getId()}")
        model.setObjective(var_linearize_max, OptimizationSense.MINIMIZE)

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("tariff/tariff_zone.ilp")
        logger.debug("Finished writing lp file")


    # optimization
    logger.debug("Start optimization")
    start_time = time.time()
    model.solve()
    end_time = time.time()
    logger.info("Solver time: {:5.3f}s".format(end_time - start_time))

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")

        logger.debug("Extract solution")
        zones = []
        for zone_id in range(upper_bound_zones):
            stops = []
            for node in nodes:
                if model.getValue(var_assign_nodes_to_zones[(node.getId(), zone_id)]) >= 0.8:
                    stops.append(node.getId())
            zones.append([zone_id + 1, stops])

        zone_price = ZonePrices()
        for number_visited_zones in range(1, length_of_price_list + 1):
            zone_price.setPrice(number_visited_zones, round(model.getValue(var_price_number_zones[number_visited_zones]), 2))

        price_matrix = PriceMatrix()
        for origin in nodes:
            for destination in nodes:
                if origin.getId() != destination.getId():
                    price_matrix.setValue(origin.getId(), destination.getId(), round(model.getValue(var_price_between_nodes[(origin.getId(), destination.getId())]), 2))
                else:
                    price_matrix.setValue(origin.getId(), destination.getId(), 0)

        objective_value = model.getDoubleAttribute(DoubleAttribute.OBJ_VAL)
        model.dispose()
        solver.dispose()
        return objective_value, zones, zone_price, price_matrix
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS('tariff/zone_iismodel')
            logger.error("Finished computing IIS, written to tariff/zone_iismodel")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()



def onlyPrices(given_zones: List[Zone], counting_type: TariffZoneCountingType, nodes: List[Stop], paths_between_nodes, no_elongation: bool, no_stopover: bool,
               reference_prices: PriceMatrix, weights: Dict[Tuple[int,int],float], taf_obj_type: TariffObjectiveType,
               parameters: SolverParameters) -> Tuple[float, ZonePrices, PriceMatrix]:
    """
    solves the zone tariff problem with given fixed zones for given config values with the help of gurobi.
    :param given_zones: List of Zones that are fixed.
    :param counting_type: defines the zone counting mode.
    :param nodes: List of nodes
    :param paths_between_nodes: Dict of dicts, where entry [v_id][w_id] is a v-w-path given as list of nodes (single counting) or list of edges (multiple counting)
    :param no_elongation: Boolean. If true, then no-elongation property is enforced.
    :param reference_prices: PriceMatrix, where [v_id][w_id] is the reference price for traveling from v to w
    :param weights: dict of weights used in the objective function
    :param taf_obj_type: defines the objective function
    :param parameters: Solver parameter informations: number of threads, timelimit, MIP-gap and solver type
    :return: optimal objective value, zone_prices given as list ordered by number of visited zones and full price matrix
    """

    logger.debug("Preprocessing")
    number_of_zones = len(given_zones)

    zone = dict()
    for node in nodes:
        for z in given_zones:
            if z.containsStop(node):
                zone[node] = z.getZoneId()
                break

    # maximum length of a path
    max_number_nodes_on_path = 0
    for node1 in nodes:
        for node2 in nodes:
            if node1!=node2:
                if len(paths_between_nodes[node1.getId()][node2.getId()]) > max_number_nodes_on_path:
                    max_number_nodes_on_path = len(paths_between_nodes[node1.getId()][node2.getId()])
    if counting_type == TariffZoneCountingType.MULTIPLE:
        max_number_nodes_on_path = max_number_nodes_on_path + 1

    if counting_type == TariffZoneCountingType.SINGLE:
        length_of_price_list = number_of_zones
    elif counting_type == TariffZoneCountingType.MULTIPLE:
        length_of_price_list = max_number_nodes_on_path

    # origin-destination pairs (of all nodes)
    node_pairs = [(node1, node2) for node1 in nodes for node2 in nodes if node1.getId() != node2.getId()]

    # maximum price in reference_prices
    max_price = 0
    for node1, node2, price in reference_prices.getAllEntries():
        if price > max_price:
            max_price = price
    big_m = max_price + 1

    # for each node pair determine number of zones visited on given path
    number_zones = {}
    for node1, node2 in node_pairs:
        number_zones[node1, node2] = 1
        if counting_type == TariffZoneCountingType.SINGLE:
            zone_set = set()
            for node in paths_between_nodes[node1.getId()][node2.getId()]:
                zone_set.add(zone[node])
            number_zones[node1, node2] = len(zone_set)
        elif counting_type == TariffZoneCountingType.MULTIPLE:
            for (start, end) in paths_between_nodes[node1.getId()][node2.getId()]:
                if zone[start] != zone[end]:
                    number_zones[node1, node2] += 1

    # determine d[u,v,k] = 1 iff the path from u to v uses exactly k zones
    d = {}
    for node1, node2 in node_pairs:
        d[node1, node2] = {k:0 for k in range(1, length_of_price_list + 1)}
        d[node1, node2][number_zones[node1, node2]] = 1
    for node in nodes:
        d[node, node] = {k:0 for k in range(1, length_of_price_list + 1)}

    logger.debug("Initialize model")

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Set model parameters")
    parameters.setSolverParameters(model)

    logger.debug("Add variables")
    var_price_number_zones = dict()
    for number_visited_zones in range(1, length_of_price_list+1):
        var_price_number_zones[number_visited_zones] = model.addVariable(lower_bound=0, upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f"p_{number_visited_zones}")
    var_price_between_nodes = dict()
    z = dict()
    for node1, node2 in node_pairs:
        var_price_between_nodes[node1,node2] = model.addVariable(lower_bound=0, upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f"pi_{node1.getId()}_{node2.getId()}")
        z[node1, node2] = model.addVariable(var_type = VariableType.CONTINUOUS, name=f"z_{node1.getId()}_{node2.getId()}")


    logger.debug("Add constraints")
    # set price according to number of visited zones
    for node1, node2 in node_pairs:
        for number_visited_zones in range(1,length_of_price_list+1):
            model.addConstraint(var_price_between_nodes[node1, node2], ConstraintSense.LESS_EQUAL, var_price_number_zones[number_visited_zones] + (1-d[node1, node2][number_visited_zones])*big_m,
                name=f"price_1_{node1.getId()}_{node2.getId()}_{number_visited_zones}")
            model.addConstraint(var_price_number_zones[number_visited_zones], ConstraintSense.LESS_EQUAL, var_price_between_nodes[node1, node2] + (1-d[node1, node2][number_visited_zones])*big_m,
                name=f"price_2_{node1.getId()}_{node2.getId()}_{number_visited_zones}")

    # resolve absolute value in objective
    for node1, node2 in node_pairs:
        model.addConstraint(var_price_between_nodes[node1,node2] - reference_prices.getValue(node1.getId(), node2.getId()), ConstraintSense.LESS_EQUAL, z[node1,node2],
            name=f"lin_obj_{node1.getId()}_{node2.getId()}")
        model.addConstraint(reference_prices.getValue(node1.getId(), node2.getId()) - var_price_between_nodes[node1,node2], ConstraintSense.LESS_EQUAL, z[node1,node2],
            name=f"lin_obj_{node1.getId()}_{node2.getId()}")

    if no_elongation:
        create_constraints_no_elongation(model, var_price_number_zones, length_of_price_list)

    if no_stopover:
        create_constraints_no_stopover(model, var_price_number_zones, length_of_price_list, counting_type)

    logger.debug("Set Objective Function")
    if taf_obj_type == TariffObjectiveType.SUM_ABSOLUTE_DEVIATION:
        model.setObjective(model.createExpression().quicksum(weights[(node1.getId(), node2.getId())] * z[node1, node2] for node1, node2 in node_pairs), OptimizationSense.MINIMIZE)
    elif taf_obj_type == TariffObjectiveType.MAX_ABSOLUTE_DEVIATION:
        var_linearize_max = model.addVariable(lower_bound=0, var_type=VariableType.CONTINUOUS, name='linearization_var_for_max_in_objective')
        for node1, node2 in node_pairs:
            model.addConstraint(var_linearize_max, ConstraintSense.GREATER_EQUAL, weights[(node1.getId(), node2.getId())] * z[node1, node2], name=f"lin_obj_{node1.getId()}_{node2.getId()}")
        model.setObjective(var_linearize_max, OptimizationSense.MINIMIZE)

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("tariff/tariff_only_prices.lp")
        logger.debug("Finished writing lp file")

    # optimization
    logger.debug("Start optimization")
    start_time = time.time()
    model.solve()
    end_time = time.time()
    logger.info("Solver time: {:5.3f}s".format(end_time - start_time))

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")

        logger.debug("Extract solution")
        zone_price = ZonePrices()
        for number_visited_zones in range(1,length_of_price_list+1):
            zone_price.setPrice(number_visited_zones, round(model.getValue(var_price_number_zones[number_visited_zones]), 2))

        price_matrix = PriceMatrix()
        for origin in nodes:
            for destination in nodes:
                if origin.getId() != destination.getId():
                    for number_visited_zones in range(1, length_of_price_list + 1):
                        if d[origin, destination][number_visited_zones] == 1:
                            price_matrix.setValue(origin.getId(), destination.getId(), round(model.getValue(var_price_number_zones[number_visited_zones]), 2))
                            break
                else:
                    price_matrix.setValue(origin.getId(), destination.getId(), 0)

        objective_value = model.getDoubleAttribute(DoubleAttribute.OBJ_VAL)
        model.dispose()
        solver.dispose()
        return objective_value, zone_price, price_matrix
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS('tariff/zone_iismodel')
            logger.error("Finished computing IIS, written to tariff/zone_iismodel")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()


def onlyZones(given_prices: ZonePrices, enforce_all_zones: bool, connected_zones: bool, counting_type: TariffZoneCountingType, nodes: List[Stop],
              paths_between_nodes, adjacent_node_pairs, reference_prices: PriceMatrix, upper_bound_zones: int, weights: Dict[Tuple[int,int],float],
              taf_obj_type: TariffObjectiveType, symmetry_breaking: TariffZoneSymmetryOption,
              parameters: SolverParameters) -> Tuple[float, List[int|List[int]], PriceMatrix]:
    """
    solves the zone tariff problem with given fixed zone prices for given config values with the help of gurobi.
    :param given_prices: given zone prices.
    :param enforce_all_zones: Boolean. If true, every zone must contain at least one stop.
    :param connected_zones: Boolean. If true, then every zone must be connected.
    :param no_stopover: Boolean. If true, then no-stopover property is enforced.
    :param counting_type: defines the zone counting mode.
    :param nodes: List of nodes
    :param paths_between_nodes: Dict of dicts, where entry [v_id][w_id] is a v-w-path given as list of nodes (single counting) or list of edges (multiple counting)
    :param adjacent_node_pairs: List of directed/ordered node pairs that are adjacent (contains pairs in both directions)
    :param reference_prices: PriceMatrix, where [v_id][w_id] is the reference price for traveling from v to w
    :param weights: dict of weights used in the objective function
    :param taf_obj_type: defines the objective function
    :param symmetry_breaking: controls which option is used for symmetry breaking
    :param parameters: Solver parameter informations: number of threads, timelimit, MIP-gap and solver type
    :return: optimal objective value, computed zones given as list of pairs zone Id and List of stops and full price matrix
    """

    logger.debug("Preprocessing")
    number_of_nodes = len(nodes)

    # maximum length of a path
    max_number_nodes_on_path = 0
    for node1 in nodes:
        for node2 in nodes:
            if node1!=node2:
                if len(paths_between_nodes[node1.getId()][node2.getId()]) > max_number_nodes_on_path:
                    max_number_nodes_on_path = len(paths_between_nodes[node1.getId()][node2.getId()])
    if counting_type == TariffZoneCountingType.MULTIPLE:
        max_number_nodes_on_path = max_number_nodes_on_path + 1

    # origin-destination pairs (of all nodes)
    node_pairs = [(node1, node2) for node1 in nodes for node2 in nodes if node1.getId() != node2.getId()]

    # maximum price in reference_prices
    max_price = 0
    for node1, node2, price in reference_prices.getAllEntries():
        if price > max_price:
            max_price = price
    big_m = max_price + 1

    if counting_type == TariffZoneCountingType.SINGLE:
        length_of_price_list = upper_bound_zones
    elif counting_type == TariffZoneCountingType.MULTIPLE:
        length_of_price_list = max_number_nodes_on_path

    logger.debug("Initialize model")

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Set model parameters")
    parameters.setSolverParameters(model)

    logger.debug("Add variables")

    var_assign_nodes_to_zones = dict()
    for node in nodes:
        for zone_id in range(upper_bound_zones):
            var_assign_nodes_to_zones[(node.getId(), zone_id)] = model.addVariable(var_type=VariableType.BINARY, name=f'x_{node.getId()}_{zone_id}')

    if connected_zones:
        var_sources = dict()
        var_flows = dict()
        for node1 in nodes:
            var_sources[node1.getId()] = model.addVariable(var_type=VariableType.BINARY, name=f's_{node1.getId()}')  # 1 if node is assigned to the supersource
            var_flows[('supersource', node1.getId())] = model.addVariable(lower_bound=0, upper_bound=number_of_nodes, var_type=VariableType.CONTINUOUS, name=f'f_{"source"}_{node1.getId()}') # works also with var_type=VariableTypeEGER
            for node2 in nodes:
                var_flows[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=0, upper_bound=number_of_nodes, var_type=VariableType.CONTINUOUS, name=f'f_{node1.getId()}_{node2.getId()}') # works also with var_type=VariableTypeEGER

    if counting_type == TariffZoneCountingType.SINGLE:
        var_zone_on_path = dict()
        for node1, node2 in node_pairs:
            for zone_id in range(upper_bound_zones):
                var_zone_on_path[(node1.getId(), node2.getId(), zone_id)] = model.addVariable(
                    var_type=VariableType.BINARY,
                    name=f'c_{node1.getId()}_{node2.getId()}_{zone_id}')  # 1 if path from v to w visits zone z
    elif counting_type == TariffZoneCountingType.MULTIPLE:
        var_nodes_in_different_zones = dict()
        for node1, node2 in adjacent_node_pairs:
            var_nodes_in_different_zones[(node1.getId(), node2.getId())] = model.addVariable(
                var_type=VariableType.BINARY,
                name=f'c_{node1.getId()}_{node2.getId()}')  # 1 if node v and w of edge {v,w} are in different zones

    var_number_visited_zones_on_path = dict()
    for node1, node2 in node_pairs:
        for number_visited_zones in range(1, length_of_price_list + 1):
            var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)] = model.addVariable(
                var_type=VariableType.BINARY, name=f'd_{node1.getId()}_{node2.getId()}_{number_visited_zones}')  # 1 if path from v to w visits k zones

    var_price_between_nodes = dict()
    var_diff_prices_new_ref = dict()
    var_diff_prices_new_ref_pos = dict()
    var_diff_prices_new_ref_neg = dict()
    for node1, node2 in node_pairs:
        var_price_between_nodes[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=0, upper_bound=float('inf'),  var_type=VariableType.CONTINUOUS, name=f'pi_{node1.getId()}_{node2.getId()}')
        var_diff_prices_new_ref[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=-float('inf'), upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f'diff_prices_{node1.getId()}_{node2.getId()}')
        var_diff_prices_new_ref_pos[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=0, upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f'diff_prices_pos_{node1.getId()}_{node2.getId()}')
        var_diff_prices_new_ref_neg[(node1.getId(), node2.getId())] = model.addVariable(lower_bound=0, upper_bound=float('inf'), var_type=VariableType.CONTINUOUS, name=f'diff_prices_neg_{node1.getId()}_{node2.getId()}')


    # add constraints
    logger.debug("Add constraints")
    # assign each node to a zone
    for node in nodes:
        model.addConstraint(model.createExpression().quicksum(var_assign_nodes_to_zones[(node.getId(), zone_id)] for zone_id in range(upper_bound_zones)), ConstraintSense.EQUAL, 1, name=f"assign_nodes_{node.getId()}")

    if enforce_all_zones:
        create_constraints_every_zone_has_nodes(model, var_assign_nodes_to_zones, nodes, upper_bound_zones)

    if connected_zones:
        create_constraints_connected_zones(model, var_assign_nodes_to_zones, var_sources, var_flows, nodes,
                                        number_of_nodes, node_pairs, adjacent_node_pairs, upper_bound_zones)

    if counting_type == TariffZoneCountingType.SINGLE:
        create_constraints_counting_zones_single_counting(model, var_assign_nodes_to_zones, var_zone_on_path,
                                                        var_number_visited_zones_on_path, number_of_nodes, node_pairs,
                                                        paths_between_nodes, upper_bound_zones)
    elif counting_type == TariffZoneCountingType.MULTIPLE:
        create_constraints_counting_zones_multiple_counting(model, var_assign_nodes_to_zones,
                                                            var_nodes_in_different_zones,
                                                            var_number_visited_zones_on_path, node_pairs,
                                                            paths_between_nodes, adjacent_node_pairs, upper_bound_zones,
                                                            max_number_nodes_on_path)

    # set prices according to number of traversed zones
    for node1, node2 in node_pairs:
        for number_visited_zones in range(1, length_of_price_list + 1):
            model.addConstraint(var_price_between_nodes[(node1.getId(), node2.getId())], ConstraintSense.LESS_EQUAL, given_prices.getPrice(number_visited_zones) + (
                    1 - var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)]) * big_m, name=f"price_1_{node1.getId()}_{node2.getId()}_{number_visited_zones}")
            model.addConstraint(given_prices.getPrice(number_visited_zones), ConstraintSense.LESS_EQUAL, var_price_between_nodes[(node1.getId(), node2.getId())] + (
                    1 - var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)]) * big_m, name=f"price_2_{node1.getId()}_{node2.getId()}_{number_visited_zones}")

    # resolve absolute value in objective
    for node1, node2 in node_pairs:
        model.addConstraint(var_diff_prices_new_ref[(node1.getId(), node2.getId())], ConstraintSense.EQUAL, var_price_between_nodes[
                (node1.getId(), node2.getId())] - reference_prices.getValue(node1.getId(), node2.getId()), name=f"lin_abs_1_{node1.getId()}_{node2.getId()}")
        model.addConstraint(var_diff_prices_new_ref_pos[(node1.getId(), node2.getId())] - var_diff_prices_new_ref_neg[
            (node1.getId(), node2.getId())], ConstraintSense.EQUAL, var_diff_prices_new_ref[(node1.getId(), node2.getId())], name=f"lin_abs_1_{node1.getId()}_{node2.getId()}")


    if symmetry_breaking == TariffZoneSymmetryOption.A:
        # symmetry breaking -- option 2 -- seems to be useful
        # the i-th node is only allowed to be in the first i zones. In particular, the first node is in the first zone.
        for index, node in enumerate(nodes):
            for zone_id in range(index + 1, upper_bound_zones):
                model.addConstraint(var_assign_nodes_to_zones[node.getId(), zone_id], ConstraintSense.EQUAL, 0, name=f"symmetry_A_1_{node.getId()}_{zone_id}")

        # symmetry breaking -- option 3
        for index in range(1,len(nodes)):
            for zone_id in range(1,upper_bound_zones):
                model.addConstraint(var_assign_nodes_to_zones[nodes[index].getId(), zone_id], ConstraintSense.LESS_EQUAL, model.createExpression().quicksum(
                var_assign_nodes_to_zones[nodes[i].getId(), zone_id - 1] for i in range(index)), name=f"symmetry_A_2_{index}_{zone_id}")

    elif symmetry_breaking == TariffZoneSymmetryOption.B:
        # # symmetry breaking -- option 1 -- seems to be way slower than without
        # zones are ordered by size
        for zone_id in range(upper_bound_zones - 1):
            model.addConstraint(model.createExpression().quicksum(var_assign_nodes_to_zones[(node.getId(), zone_id)] for node in nodes), ConstraintSense.GREATER_EQUAL,
                                model.createExpression().quicksum(var_assign_nodes_to_zones[(node.getId(), zone_id + 1)] for node in nodes), name=f"symmetry_B_{zone_id}")
    elif symmetry_breaking == TariffZoneSymmetryOption.NONE:
        pass


    # initialize objective function
    logger.debug("Initialize objective function")
    if taf_obj_type == TariffObjectiveType.SUM_ABSOLUTE_DEVIATION:
        model.setObjective(model.createExpression().quicksum(weights[(node1.getId(), node2.getId())] * (
            var_diff_prices_new_ref_pos[(node1.getId(), node2.getId())] + var_diff_prices_new_ref_neg[
            (node1.getId(), node2.getId())]) for node1, node2 in node_pairs), OptimizationSense.MINIMIZE)
    elif taf_obj_type == TariffObjectiveType.MAX_ABSOLUTE_DEVIATION:
        var_linearize_max = model.addVariable(lower_bound=0, var_type=VariableType.CONTINUOUS, name='linearization_var_for_max_in_objective')
        for node1, node2 in node_pairs:
            model.addConstraint(var_linearize_max, ConstraintSense.GREATER_EQUAL, weights[(node1.getId(), node2.getId())] * (
            var_diff_prices_new_ref_pos[(node1.getId(), node2.getId())] + var_diff_prices_new_ref_neg[
            (node1.getId(), node2.getId())]), name=f"lin_obj_{node1.getId()}_{node2.getId()}")
        model.setObjective(var_linearize_max, OptimizationSense.MINIMIZE)

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("tariff/tariff_only_zones.ilp")
        logger.debug("Finished writing lp file")

    # optimization
    logger.debug("Start optimization")
    start_time = time.time()
    model.solve()
    end_time = time.time()
    logger.info("Solver time: {:5.3f}s".format(end_time - start_time))

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")

        logger.debug("Extract solution")
        zones = []
        for zone_id in range(upper_bound_zones):
            stops = []
            for node in nodes:
                if model.getValue(var_assign_nodes_to_zones[(node.getId(), zone_id)]) >= 0.8:
                    stops.append(node.getId())
            zones.append([zone_id + 1, stops])

        price_matrix = PriceMatrix()
        for origin in nodes:
            for destination in nodes:
                if origin.getId() != destination.getId():
                    price_matrix.setValue(origin.getId(), destination.getId(), round(model.getValue(var_price_between_nodes[(origin.getId(), destination.getId())]),2))
                else:
                    price_matrix.setValue(origin.getId(), destination.getId(), 0)

        objective_value = model.getDoubleAttribute(DoubleAttribute.OBJ_VAL)
        model.dispose()
        solver.dispose()
        return objective_value, zones, price_matrix
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS('tariff/zone_iismodel')
            logger.error("Finished computing IIS, written to tariff/zone_iismodel")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()



# helper functions to create all constraints:
def create_constraints_every_zone_has_nodes(model: Model, var_assign_nodes_to_zones, nodes, upper_bound_zones):
    # assign at least one node to each zone
    for zone_id in range(upper_bound_zones):
        model.addConstraint(model.createExpression().quicksum(var_assign_nodes_to_zones[(node.getId(), zone_id)] for node in nodes), ConstraintSense.GREATER_EQUAL, 1, name=f"zone_non_empty_{zone_id}")


def create_constraints_connected_zones(model: Model, var_assign_nodes_to_zones, var_sources, var_flows, nodes, number_of_nodes,
                                       node_pairs, adjacent_node_pairs, upper_bound_zones):
    # flow from supersource only to nodes that are assigned to the supersource
    for node1 in nodes:
        model.addConstraint(var_flows[('supersource', node1.getId())], ConstraintSense.LESS_EQUAL, var_sources[node1.getId()] * number_of_nodes, name=f"connected_1_{node1.getId()}")

    # at most one node per zone is assigned to the supersource
    for node1, node2 in node_pairs:
        for zone_id in range(upper_bound_zones):
            model.addConstraint(
            var_sources[node1.getId()] + var_sources[node2.getId()] + var_assign_nodes_to_zones[(node1.getId(), zone_id)] +
            var_assign_nodes_to_zones[(node2.getId(), zone_id)], ConstraintSense.LESS_EQUAL, 3, name=f"conected_2_{node1.getId()}_{node2.getId()}_{zone_id}")

    for node1 in nodes:
        for node2 in nodes:
            if (node1, node2) not in adjacent_node_pairs:
                # only flow along edges
                model.addConstraint(var_flows[(node1.getId(), node2.getId())], ConstraintSense.EQUAL, 0, name=f"connected_3_{node1.getId()}_{node2.getId()}")  # especially var_flow[v,v] = 0
            else:
                # flow only along edges with both end nodes in the same zone
                for zone_id in range(upper_bound_zones):
                    model.addConstraint(var_flows[(node1.getId(), node2.getId())], ConstraintSense.LESS_EQUAL, (1 + var_assign_nodes_to_zones[(node1.getId(), zone_id)] - var_assign_nodes_to_zones[(node2.getId(), zone_id)]) * number_of_nodes, f"connected_3_{node1.getId()}_{node2.getId()}_{zone_id}")

    # flow conservation and each node needs to be reached by one unit of flow
    for node1 in nodes:
        model.addConstraint(model.createExpression().quicksum(var_flows[(left_node.getId(), node1.getId())] for left_node in nodes) + var_flows[
            ('supersource', node1.getId())], ConstraintSense.EQUAL, 1 + model.createExpression().quicksum(
            var_flows[(node1.getId(), right_node.getId())] for right_node in nodes), name=f"connected_4_{node1.getId()}")


def create_constraints_no_elongation(model: Model, var_price_number_zones, length_of_price_list):
    # ensure no-elongation property (monotonicity)
    for number_visited_zones in range(1, length_of_price_list):
        model.addConstraint(var_price_number_zones[number_visited_zones], ConstraintSense.LESS_EQUAL, var_price_number_zones[number_visited_zones + 1], name=f"no_elongation_{number_visited_zones}")


def create_constraints_no_stopover(model: Model, var_price_number_zones, length_of_price_list, counting_type):
    # ensure no-stopover property
    if counting_type == TariffZoneCountingType.SINGLE:
        for number_visited_zones in range(1, length_of_price_list + 1):
            for number_visited_zones_subpath_1 in range(1, number_visited_zones):
                for number_visited_zones_subpath_2 in range(number_visited_zones + 1 -number_visited_zones_subpath_1, number_visited_zones):
                    model.addConstraint(var_price_number_zones[number_visited_zones], ConstraintSense.LESS_EQUAL, var_price_number_zones[number_visited_zones_subpath_1] +
                        var_price_number_zones[number_visited_zones_subpath_2], name=f"nostopover_{number_visited_zones}_{number_visited_zones_subpath_1}_{number_visited_zones_subpath_2}")
    elif counting_type == TariffZoneCountingType.MULTIPLE:
        for number_visited_zones in range(3, length_of_price_list + 1):
            for number_visited_zones_subpath in range(2, round((number_visited_zones + 1) / 2)):
                model.addConstraint(var_price_number_zones[number_visited_zones], ConstraintSense.LESS_EQUAL, var_price_number_zones[number_visited_zones_subpath] +
                    var_price_number_zones[number_visited_zones - number_visited_zones_subpath + 1], name=f"nostopover_{number_visited_zones}_{number_visited_zones_subpath}")


def create_constraints_counting_zones_single_counting(model: Model, var_assign_nodes_to_zones, var_zone_on_path,
                                                      var_number_visited_zones_on_path, number_of_nodes, node_pairs,
                                                      paths_between_nodes, upper_bound_zones):
    # check whether a zone is traversed on a path
    for node1, node2 in node_pairs:
        for zone_id in range(upper_bound_zones):
            model.addConstraint(model.createExpression().quicksum(
                var_assign_nodes_to_zones[(node3.getId(), zone_id)] for node3 in paths_between_nodes[node1.getId()][node2.getId()]),
                ConstraintSense.LESS_EQUAL, var_zone_on_path[(node1.getId(), node2.getId(), zone_id)] * number_of_nodes, name=f"singlecount_1_{node1.getId()}_{node2.getId()}_{zone_id}")
            model.addConstraint(var_zone_on_path[(node1.getId(), node2.getId(), zone_id)], ConstraintSense.LESS_EQUAL,
                model.createExpression().quicksum(var_assign_nodes_to_zones[(node3.getId(), zone_id)] for node3 in paths_between_nodes[node1.getId()][node2.getId()]), name=f"singlecount_2_{node1.getId()}_{node2.getId()}_{zone_id}")

    # count how many zones are traversed for each OD pair
    for node1, node2 in node_pairs:
        model.addConstraint(model.createExpression().quicksum(var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)]
            for number_visited_zones in range(1, upper_bound_zones + 1)), ConstraintSense.EQUAL, 1, name=f"singlecount_3_{node1.getId()}_{node2.getId()}")
        model.addConstraint(model.createExpression().quicksum(var_zone_on_path[(node1.getId(), node2.getId(), zone_id)]
            for zone_id in range(upper_bound_zones)), ConstraintSense.EQUAL, model.createExpression().quicksum(number_visited_zones * var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)]
            for number_visited_zones in range(1, upper_bound_zones + 1)), name=f"singlecount_4_{node1.getId()}_{node2.getId()}")


def create_constraints_counting_zones_multiple_counting(model: Model, var_assign_nodes_to_zones, var_nodes_in_different_zones,
                                                        var_number_visited_zones_on_path, node_pairs,
                                                        paths_between_nodes, adjacent_node_pairs, upper_bound_zones,
                                                        max_number_nodes_on_path):

    # direction of edge does not matter for counting
    for node1, node2 in adjacent_node_pairs:
        model.addConstraint(var_nodes_in_different_zones[(node1.getId(), node2.getId())], ConstraintSense.EQUAL, var_nodes_in_different_zones[(node2.getId(), node1.getId())], name=f"multcount_1_{node1.getId()}_{node2.getId()}" )

    # check whether end nodes of edges are in different zones
    for zone_id in range(upper_bound_zones):
        for node1, node2 in adjacent_node_pairs:
            model.addConstraint(var_assign_nodes_to_zones[(node1.getId(), zone_id)] - var_assign_nodes_to_zones[(node2.getId()), zone_id], ConstraintSense.LESS_EQUAL,
                var_nodes_in_different_zones[(node1.getId(), node2.getId())], name=f"multcount_2_{zone_id}_{node1.getId()}_{node2.getId()}")
            model.addConstraint(var_nodes_in_different_zones[(node1.getId(), node2.getId())], ConstraintSense.LESS_EQUAL, 2 - var_assign_nodes_to_zones[(node1.getId(), zone_id)] - var_assign_nodes_to_zones[(node2.getId(), zone_id)],
                name=f"multcount_3_{zone_id}_{node1.getId()}_{node2.getId()}")

    # count how many zones are traversed for each OD pair
    for node1, node2 in node_pairs:
        model.addConstraint(model.createExpression().quicksum(var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)] for number_visited_zones in range(1, max_number_nodes_on_path + 1)),
            ConstraintSense.EQUAL, 1, name=f"multcount_4_{node1.getId()}_{node2.getId()}")
        model.addConstraint(model.createExpression().quicksum(var_nodes_in_different_zones[(node3.getId(), node4.getId())] for node3, node4 in paths_between_nodes[node1.getId()][node2.getId()]) + 1,
            ConstraintSense.EQUAL, model.createExpression().quicksum(number_visited_zones * var_number_visited_zones_on_path[(node1.getId(), node2.getId(), number_visited_zones)]
            for number_visited_zones in range(1, max_number_nodes_on_path + 1)), name=f"multcount_5_{node1.getId()}_{node2.getId()}")


def test_no_elongation(prices: ZonePrices) -> bool:
    """
    Tests, if the no elongation property is satisfied for the given zone price list
    :param prices: given zone prices
    :return: Boolean, if the no elongation property is satisfied or not
    """
    current_price = 0
    max_zones = prices.getMaxZones()
    for nr_zones in range(1, max_zones+1):
        if prices.getPrice(nr_zones) < current_price:
            return False
        else:
            current_price = prices.getPrice(nr_zones)
    return True


def test_no_stopover(prices: ZonePrices, counting_type: TariffZoneCountingType) -> bool:
    """
    Tests, if the no stopover property is satisfied for the given zone price list
    :param prices: given zone prices
    :param counting_type: How to count the zones on a path
    :param upper_bound_zones: How many zones are there at most
    :param number_of_nodes: How many nodes are there in the PTN
    :return: Boolean, if the no stopover property is satisfied or not
    """
    max_zones = prices.getMaxZones()
    if counting_type == TariffZoneCountingType.MULTIPLE:
        for k in range(1, max_zones+1):
            for i in range(1, math.floor((k+1)/2)+1):
                if prices.getPrice(k) > prices.getPrice(i) + prices.getPrice(k-i+1):
                    return False
        for i in range(1, max_zones+1):
            for j in range(max_zones+1-i, max_zones+1):
                if prices.getPrice(max_zones) > prices.getPrice(i) + prices.getPrice(j):
                    return False
        return True
    elif counting_type == TariffZoneCountingType.SINGLE:
        for k in range(1, max_zones+1):
            for i in range(1, k+1):
                for j in range(k+1-i, k+1):
                    if prices.getPrice(k) > prices.getPrice(i) + prices.getPrice(j):
                        return False
        return True


def test_connected_zones(zones: List[Zone], ptn: Graph[Link, Stop]) -> bool:
    """
    Tests, if the zones are connected.
    :param zones: List of given zones
    :param ptn: underlying ptn
    :return: Boolean value if all zones are connected or not
    """
    G = convert_graph_to_networkx(ptn, False, Link.getLength)
    for zone in zones:
        if len(zone.getStops())>0:
            subgraph = nx.induced_subgraph(G, [s.getId() for s in zone.getStops()])
            if not nx.is_connected(subgraph):
                return False
    return True
