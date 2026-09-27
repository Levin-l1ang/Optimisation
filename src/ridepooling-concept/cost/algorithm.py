import logging
import math

from core.model.graph import Graph
from core.model.lines import LinePool
from core.model.ridepooling import RidepoolingPool
from core.model.ptn import Stop, Link
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, \
    IntAttribute
from core.solver.solver_parameters import SolverParameters

logger = logging.getLogger(__name__)


def solve_rideconcept_alpha(ptn: Graph[Stop, Link],  ride_pool: RidepoolingPool, parameters: SolverParameters,
                      capacity_ridepool: int) -> bool:
    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Add variables")

    number_vehicles = {}
    for area in ride_pool.getAreas():
        number_vehicles[area] = model.addVariable(0, float('inf'), VariableType.INTEGER, ride_pool.getCost(), f"v_{area.getId()}")


    logger.debug("Add constraints")
    for link in ptn.getEdges():
        sum_per_link = model.createExpression()
        for area in ride_pool.getAreas():
            if link in area.getEdges():
                sum_per_link.multiAdd(capacity_ridepool*area.getVehicleFrequency(link.getId()), number_vehicles[area])
        model.addConstraint(sum_per_link, ConstraintSense.LESS_EQUAL, capacity_ridepool*link.getUpperFrequencyBound(),
                            f"u_{link.getId()}")
        model.addConstraint(sum_per_link, ConstraintSense.GREATER_EQUAL, link.getLoad(),
                            f"l_{link.getId()}")

    logger.debug("Add parameters")
    parameters.setSolverParameters(model)
    model.setSense(OptimizationSense.MINIMIZE)
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/rideconcept-alpha.lp")
        logger.debug("Finished writing lp file")

    logger.debug("Start optimization")
    model.solve()
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        for area in ride_pool.getAreas():
            area.setNumberOfVehicles(int(round(model.getValue(number_vehicles[area]))))
        model.dispose()
        solver.dispose()
        return True
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            model.computeIIS("line-planning/rideconcept-alpha.ilp")
        model.dispose()
        solver.dispose()
        return False



def solve_rideconcept_beta(ptn: Graph[Stop, Link], ride_pool: RidepoolingPool, parameters: SolverParameters,
                      capacity_ridepool: int, period: int) -> bool:
    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Add variables")

    number_vehicles = {}
    for area in ride_pool.getAreas():
        number_vehicles[area] = model.addVariable(0, float('inf'), VariableType.INTEGER, ride_pool.getCost(), f"v_{area.getId()}")

    beta = {}
    for area in ride_pool.getAreas():
        beta[area] = {}
        for edge in area.getEdges():
            beta[area][edge] = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, 0, f"beta_{area.getId()}_{edge.getId()}")


    logger.debug("Add constraints")
    for link in ptn.getEdges():
        sum_per_link = model.createExpression()
        for area in ride_pool.getAreas():
            if link in area.getEdges():
                sum_per_link.multiAdd(capacity_ridepool, beta[area][link])
        model.addConstraint(sum_per_link, ConstraintSense.LESS_EQUAL, capacity_ridepool*link.getUpperFrequencyBound(),
                            f"u_{link.getId()}")
        model.addConstraint(sum_per_link, ConstraintSense.GREATER_EQUAL, link.getLoad(),
                            f"l_{link.getId()}")

    for area in ride_pool.getAreas():
        sum_per_area = model.createExpression()
        for edge in area.getEdges():
            sum_per_area.multiAdd(edge.getLowerBound()*area.getStretchFactor(edge.getId()), beta[area][edge])
        model.addConstraint(sum_per_area, ConstraintSense.LESS_EQUAL, period*number_vehicles[area], f"sum_beta_{area.getId()}")


    logger.debug("Add parameters")
    parameters.setSolverParameters(model)
    model.setSense(OptimizationSense.MINIMIZE)
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/rideconcept-beta.lp")
        logger.debug("Finished writing lp file")

    logger.debug("Start optimization")
    model.solve()
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        for area in ride_pool.getAreas():
            area.setNumberOfVehicles(int(round(model.getValue(number_vehicles[area]))))
            if area.getNumberOfVehicles() == 0:
                for edge in area.getEdges():
                    ride_pool.getArea(area.getId()).setDistribution(edge.getId(), 0)
            else:
                for edge in area.getEdges():
                    ride_pool.getArea(area.getId()).setDistribution(edge.getId(), model.getValue(beta[area][edge])/area.getNumberOfVehicles())
        model.dispose()
        solver.dispose()
        return True
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            model.computeIIS("line-planning/rideconcept-beta.ilp")
        model.dispose()
        solver.dispose()
        return False