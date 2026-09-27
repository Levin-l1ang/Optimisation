import logging
from typing import Tuple, List
import time

import math

from core.model.graph import Graph
from core.model.ptn import Link, Stop
from core.model.tariff import PriceMatrix
from core.model.tariff_types import TariffObjectiveType
from core.model.routing import Routing
from core.exceptions.solver_exceptions import SolverFoundNoFeasibleSolutionException
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, IntAttribute
from core.solver.solver_parameters import SolverParameters

from price_matrix_helper import compute_price_matrix_network_distance, compute_price_matrix_beeline_distance

logger = logging.getLogger(__name__)


def compute_optimal_beeline_distance_tariff(reference_prices: PriceMatrix, ptn: Graph[Stop, Link], weights: List[float], taf_opt_type: TariffObjectiveType,
                                    parameters: SolverParameters) -> Tuple[float, float, PriceMatrix]:
    """
    Computes a fixed price and a price per kilometer for a beeline distance tariff model. Depending on the selected TariffObjectiveType
    either the sum or the maximum of the absolute deviations from the reference prices is minimized.
    :param reference_prices: PriceMatrix with the reference prices
    :param ptn: the ptn
    :param weights: List of weights for objective function
    :param taf_opt_type: TariffObjectiveType, either sum oder maxmimum of absolute deviations
    :param parameters: Solver parameter informations: number of threads, timelimit, MIP-gap and solver type
    :return: fixed costs, costs per kilometer and price matrix
    """
    # Compute input lists
    reference_price_list = []
    distance_list = []
    for origin in ptn.getNodes():
        for destination in ptn.getNodes():
            if origin.getId() != destination.getId():
                reference_price_list.append(reference_prices.getValue(origin.getId(), destination.getId()))
                distance_list.append(math.dist([origin.getXCoordinate(), origin.getYCoordinate()],
                                            [destination.getXCoordinate(), destination.getYCoordinate()]))

    # Use the chosen optimization model for computing the beeline tariff
    if taf_opt_type == TariffObjectiveType.SUM_ABSOLUTE_DEVIATION:
        [beeline_fix, beeline_factor] = optimize_sum_abs_model(reference_price_list, distance_list, weights, parameters)
    elif taf_opt_type == TariffObjectiveType.MAX_ABSOLUTE_DEVIATION:
        [beeline_fix, beeline_factor] = optimize_max_abs_model(reference_price_list, distance_list, weights, parameters)

    logger.info(f"fix costs beeline distance: {beeline_fix:.2f}")
    logger.info(f"factor costs beeline distance: {beeline_factor:.2f}")

    # Compute price matrix
    price_matrix = compute_price_matrix_beeline_distance(ptn, beeline_fix, beeline_factor)

    return beeline_fix, beeline_factor, price_matrix


def compute_optimal_network_distance_tariff(reference_prices: PriceMatrix, ptn: Graph[Stop, Link], routing: Routing, weights: List[float], taf_opt_type: TariffObjectiveType,
                                    parameters: SolverParameters) -> Tuple[float, float, PriceMatrix]:
    """
    Computes a fixed price and a price per kilometer for a network distance tariff model. Depending on the selected TariffObjectiveType
    either the sum or the maximum of the absolute deviations from the reference prices is minimized.
    :param reference_prices: PriceMatrix with the reference prices
    :param ptn: the ptn
    :param weights: list of weights in objcetive function
    :param taf_opt_type: TariffObjectiveType, either sum oder maxmimum of absolute deviations
    :param parameters: Solver parameter informations: number of threads, timelimit, MIP-gap and solver type
    :return: fixed costs, costs per kilometer and price matrix
    """
    # compute lengths of paths w.r.t. distance
    paths_lengths = routing.getPathLengths()

    # Compute input lists
    reference_price_list = []
    distance_list = []
    for origin in ptn.getNodes():
        for destination in ptn.getNodes():
            if origin.getId() != destination.getId():
                reference_price_list.append(reference_prices.getValue(origin.getId(), destination.getId()))
                distance_list.append(paths_lengths[origin][destination])

    # Use the chosen optimization model for computing the distance tariff
    if taf_opt_type == TariffObjectiveType.SUM_ABSOLUTE_DEVIATION:
        [distance_fix, distance_factor] = optimize_sum_abs_model(reference_price_list, distance_list, weights, parameters)
    elif taf_opt_type == TariffObjectiveType.MAX_ABSOLUTE_DEVIATION:
        [distance_fix, distance_factor] = optimize_max_abs_model(reference_price_list, distance_list, weights, parameters)

    logger.info(f"fix costs network distance: {distance_fix:.2f}")
    logger.info(f"factor costs network distance: {distance_factor:.2f}")

    # Compute Price matrix
    price_matrix = compute_price_matrix_network_distance(ptn, paths_lengths, distance_fix, distance_factor)

    return distance_fix, distance_factor, price_matrix


def optimize_sum_abs_model(ref: List[float], dist: List[float], weights: List[float], parameters: SolverParameters) -> Tuple[float, float]:
    """
    Compute a fixed price and a price per kilometer minimizing the sum of absolute deviations from reference prices.
    :param ref: list of reference prices per OD-pair
    :param dist: list of lengths of path per OD-pair
    :param weights: list of weights in the objective function
    :param parameters: Solver parameter informations: number of threads, timelimit, MIP-gap and solver type
    :return: base amount f, price per kilometer p (for a network distance or beeline distance tariff)
    """

    NUMBER_OF_OD_PAIRS = len(weights)

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Add parameters")
    parameters.setSolverParameters(model)

    logger.debug("Add variables")
    var_fix_price = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, 0, "fix_price")
    var_factor_price = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, 0, "factor_price")
    var_diff_prices_new_ref = {}
    for d in range(NUMBER_OF_OD_PAIRS):
        var_diff_prices_new_ref[d] = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, weights[d], f"diff_prices_new_ref_{d}")

    logger.debug("Add constraints")
    for d in range(NUMBER_OF_OD_PAIRS):
        model.addConstraint(var_factor_price * dist[d] + var_fix_price - ref[d], ConstraintSense.LESS_EQUAL, var_diff_prices_new_ref[d], f"linearize_abs_{d}_1")
        model.addConstraint((-1)*var_factor_price * dist[d] - var_fix_price + ref[d], ConstraintSense.LESS_EQUAL, var_diff_prices_new_ref[d], f"linearize_abs_{d}_2")

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("tariff/tariff_dist_based_sum.lp")
        logger.debug("Finished writing lp file")

    model.setSense(OptimizationSense.MINIMIZE)

    logger.debug("Start optimization")
    start_time = time.time()
    model.solve()
    end_time = time.time()
    logger.info("Solver time: {:5.3f}s".format(end_time - start_time))
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        fix = model.getValue(var_fix_price)
        factor = model.getValue(var_factor_price)
        model.dispose()
        solver.dispose()
        return fix, factor
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS("tariff/tariff_dist_based_sum_iismodel")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()


def optimize_max_abs_model(ref: List[float], dist: List[float], weights: List[float], parameters: SolverParameters) -> Tuple[float, float]:
    """
    Compute a fixed price and a price per kilometer minimizing the maximum of absolute deviations from reference prices.
    :param ref: list of reference prices per OD-pair
    :param dist: list of lengths of path per OD-pair
    :param weights: list of weights in the objective function
    :param parameters: Solver parameter informations: number of threads, timelimit, MIP-gap and solver type
    :return: base amount f, price per kilometer p (for a network distance or beeline distance tariff)
    """

    NUMBER_OF_OD_PAIRS = len(weights)

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Add variables")
    var_fix_price = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, 0, "fix_price")
    var_factor_price = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, 0, "factor_price")
    var_diff_prices_new_ref = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, 1, f"diff_prices_new_ref")

    logger.debug("Add constraints")
    for d in range(NUMBER_OF_OD_PAIRS):
        model.addConstraint(weights[d]*dist[d]*var_factor_price + weights[d]*var_fix_price - ref[d]*weights[d], ConstraintSense.LESS_EQUAL, var_diff_prices_new_ref, f"linearize_abs_{d}_1")
        model.addConstraint(ref[d]*weights[d] - weights[d]*dist[d]*var_factor_price - weights[d]*var_fix_price, ConstraintSense.LESS_EQUAL, var_diff_prices_new_ref, f"linearize_abs_{d}_2")

    logger.debug("Add parameters")
    parameters.setSolverParameters(model)
    model.setSense(OptimizationSense.MINIMIZE)
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("tariff/tariff_dist_based_max.lp")
        logger.debug("Finished writing lp file")

    logger.debug("Start optimization")
    start_time = time.time()
    model.solve()
    end_time = time.time()
    logger.info("Solver time: {:5.3f}s".format(end_time - start_time))
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        fix = model.getValue(var_fix_price)
        factor = model.getValue(var_factor_price)
        model.dispose()
        solver.dispose()
        return fix, factor
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS("tariff/tariff_dist_based_abs_iismodel")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()

