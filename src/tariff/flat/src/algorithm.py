import time
import logging

from core.exceptions.solver_exceptions import SolverFoundNoFeasibleSolutionException
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, IntAttribute
from core.solver.solver_parameters import SolverParameters

logger = logging.getLogger(__name__)

def compute_flat_max_abs_weighted(reference_prices, weights, parameters: SolverParameters) -> float:

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Add parameters")
    parameters.setSolverParameters(model)

    logger.debug("Add variables")
    var_objective = model.addVariable(0, float('inf'), VariableType.CONTINUOUS)
    var_fix_price = model.addVariable(0, float('inf'), VariableType.CONTINUOUS)
    var_price_diff = model.addVariable(0, float('inf'), VariableType.CONTINUOUS)

    logger.debug("Add constraints")
    i=1
    for reference_price in reference_prices:
        model.addConstraint(reference_price - var_fix_price, ConstraintSense.LESS_EQUAL, var_price_diff, f"lin_1_{i}")
        model.addConstraint(var_fix_price - reference_price, ConstraintSense.LESS_EQUAL, var_price_diff, f"lin_2_{i}")
        i+=1

    i=0
    for weight in weights:
        model.addConstraint(var_objective, ConstraintSense.GREATER_EQUAL, weight*var_price_diff, f"obj_{i}")
        i=1

    logger.debug("Set objective")
    model.setObjective(var_objective, OptimizationSense.MINIMIZE)

    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("tariff/tariff_flat_max_weighted.lp")
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
        price = round(model.getValue(var_fix_price), 2)
        model.dispose()
        solver.dispose()
        return price
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            logger.error("Model is infeasible, compute IIS")
            model.computeIIS("tariff/tariff_flat_max_weighted_iismodel")
        model.dispose()
        solver.dispose()
        raise SolverFoundNoFeasibleSolutionException()