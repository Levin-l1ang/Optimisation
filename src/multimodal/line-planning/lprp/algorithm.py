import logging
import time

from core.model.graph import Graph
from core.model.lines import LinePool, Line
from core.model.ridepooling import RidepoolingPool, RidepoolingArea
from core.model.infrastructure_network import InfraNode, InfraLink
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, \
    IntAttribute
from core.solver.solver_parameters import SolverParameters

logger = logging.getLogger(__name__)


def lines_on_infra_link(lpool: LinePool, links: list[InfraLink]) -> dict[InfraLink,list[Line]]:
    """
    Helper method to get a dictionary returning for each infrastructure link a list of lines using this link

    :param lpool: a line pool
    :type lpool: LinePool
    :param links: a list of infrastructure links
    :type links: list[InfraLink]
    :return: a dictionary storing for each link a list of lines using this link
    :rtype: dict[InfraLink,list[Line]]
    """

    lines_on_link = {}
    for link in links:
        lines_on_link[link] = []
    for line in lpool.getLines():
        for edge in line.getLinePath().getEdges():
            for link in edge.getUnderlyingInfrastructure().getEdges():
                lines_on_link[link].append(line)
    return lines_on_link

def areas_on_infra_link(rpool: RidepoolingPool, links: list[InfraLink]) -> dict[InfraLink, list[RidepoolingArea]]:
    """
    Helper method to get a dictionary returning for each infrastructure link a list of ridepooling areas using this link

    :param rpool: a ridepooling pool
    :type rpool: RidepoolingPool
    :param links: a list of infrastructure links
    :type links: list[InfraLink]
    :return: a dictionary storing for each link a list of ridepooling areas using this link
    :rtype: dict[InfraLink,list[Line]]
    """

    areas_on_link = {}
    for link in links:
        areas_on_link[link] = []
    for area in rpool.getAreas():
        for edge in area.getEdges():
            for link in edge.getUnderlyingInfrastructure().getEdges():
                areas_on_link[link].append(area)
    return areas_on_link


def solve_rideconcept_alpha(isn: Graph[InfraNode,InfraLink], line_pool: dict[str,LinePool], ride_pool: dict[str,RidepoolingPool], parameters: SolverParameters,
                      vehicle_capacity: dict[str,int], infra_capacity_weight: dict[str,float], line_modalities: list[str], rpool_modalities: list[str], fixed_concepts: list[str] = []) -> bool:

    # prepare distribution factors:
    alpha = {}
    for modality in rpool_modalities:
        alpha[modality] = {}
        for area in ride_pool[modality].getAreas():
            alpha[modality][area] = {}
            for link in isn.getEdges():
                alpha[modality][area][link] = 0
            for edge in area.getEdges():
                for link in edge.getUnderlyingInfrastructure().getEdges():
                    alpha[modality][area][link] += area.getVehicleFrequency(edge.getId())

    # prepare dictionnaries
    lines_on_link = {}
    for modality in line_modalities:
        lines_on_link[modality] = lines_on_infra_link(line_pool[modality], isn.getEdges())
    areas_on_link = {}
    for modality in rpool_modalities:
        areas_on_link[modality] = areas_on_infra_link(ride_pool[modality], isn.getEdges())


    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Add variables")
    frequencies = {}
    for modality in line_modalities:
        frequencies[modality] = {}
        for line in line_pool[modality].getLines():
            frequencies[modality][line] = model.addVariable(0, float('inf'), VariableType.INTEGER, line.getCost(), f"f_{modality}_{line.getId()}")
            if modality in fixed_concepts:
                model.addConstraint(frequencies[modality][line], ConstraintSense.EQUAL, line.getFrequency(), f"fixed_{modality}_{line.getId()}")

    number_vehicles = {}
    for modality in rpool_modalities:
        number_vehicles[modality] = {}
        for area in ride_pool[modality].getAreas():
            number_vehicles[modality][area] = model.addVariable(0, float('inf'), VariableType.INTEGER, ride_pool[modality].getCost(), f"v_{modality}_{area.getId()}")
            if modality in fixed_concepts:
                model.addConstraint(number_vehicles[modality][area], ConstraintSense.EQUAL, area.getNumberOfVehicles(), f"fixed_{modality}_{area.getId()}")


    logger.debug("Adding constraints")
    for link in isn.getEdges():
        model.addConstraint(
            model.createExpression().quicksum(vehicle_capacity[modality]*frequencies[modality][line] for modality in line_modalities for line in lines_on_link[modality][link]) +
            model.createExpression().quicksum(alpha[modality][area][link]*vehicle_capacity[modality]*number_vehicles[modality][area] for modality in rpool_modalities for area in areas_on_link[modality][link]),
            ConstraintSense.GREATER_EQUAL, link.getLoad(),f"l_{link.getId()}")
        model.addConstraint(
            model.createExpression().quicksum(infra_capacity_weight[modality]*frequencies[modality][line] for modality in line_modalities for line in lines_on_link[modality][link]) +
            model.createExpression().quicksum(alpha[modality][area][link]*infra_capacity_weight[modality]*number_vehicles[modality][area] for modality in rpool_modalities for area in areas_on_link[modality][link]),
            ConstraintSense.LESS_EQUAL, link.getCapacity(),f"u_{link.getId()}")


    logger.debug("Add parameters")
    parameters.setSolverParameters(model)
    model.setSense(OptimizationSense.MINIMIZE)
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/lprp-alpha.lp")
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
        for modality in line_modalities:
            for line in line_pool[modality].getLines():
                line.setFrequency(int(round(model.getValue(frequencies[modality][line]))))
        for modality in rpool_modalities:
            for area in ride_pool[modality].getAreas():
                area.setNumberOfVehicles(int(round(model.getValue(number_vehicles[modality][area]))))
        model.dispose()
        solver.dispose()
        return True
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            model.computeIIS("line-planning/lprp-alpha.ilp")
        model.dispose()
        solver.dispose()
        return False



def solve_rideconcept_beta(isn: Graph[InfraNode,InfraLink], line_pool: dict[str,LinePool], ride_pool: dict[str,RidepoolingPool], parameters: SolverParameters,
                      vehicle_capacity: dict[str,int], infra_capacity_weight: dict[str,float], line_modalities: list[str], rpool_modalities: list[str], period: int, fixed_concepts: list[str] = []) -> bool:

    # prepare dictionnaries
    lines_on_link = {}
    for modality in line_modalities:
        lines_on_link[modality] = lines_on_infra_link(line_pool[modality], isn.getEdges())
    areas_on_link = {}
    for modality in rpool_modalities:
        areas_on_link[modality] = areas_on_infra_link(ride_pool[modality], isn.getEdges())

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    logger.debug("Add variables")
    frequencies = {}
    for modality in line_modalities:
        frequencies[modality] = {}
        for line in line_pool[modality].getLines():
            frequencies[modality][line] = model.addVariable(0, float('inf'), VariableType.INTEGER, line.getCost(), f"f_{modality}_{line.getId()}")
            if modality in fixed_concepts:
                model.addConstraint(frequencies[modality][line], ConstraintSense.EQUAL, line.getFrequency(), f"fixed_{modality}_{line.getId()}")

    number_vehicles = {}
    for modality in rpool_modalities:
        number_vehicles[modality] = {}
        for area in ride_pool[modality].getAreas():
            number_vehicles[modality][area] = model.addVariable(0, float('inf'), VariableType.INTEGER, ride_pool[modality].getCost(), f"v_{modality}_{area.getId()}")
            if modality in fixed_concepts:
                model.addConstraint(number_vehicles[modality][area], ConstraintSense.EQUAL, area.getNumberOfVehicles(), f"fixed_{modality}_{area.getId()}")

    beta = {}
    for modality in rpool_modalities:
        beta[modality] = {}
        for area in ride_pool[modality].getAreas():
            beta[modality][area] = {}
            for edge in area.getEdges():
                beta[modality][area][edge] = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, 0, f"beta_{modality}_{area.getId()}_{edge.getId()}")

    # prepare sum of betas:
    # Sum all edges of one modality, that go over a given infrastructure link
    beta_sum = {}
    for modality in rpool_modalities:
        beta_sum[modality] = {}
        for area in ride_pool[modality].getAreas():
            beta_sum[modality][area] = {}
            for link in isn.getEdges():
                beta_sum[modality][area][link] = model.createExpression()
            for edge in area.getEdges():
                for link in edge.getUnderlyingInfrastructure().getEdges():
                    beta_sum[modality][area][link] += beta[modality][area][edge]


    logger.debug("Adding constraints")

    for link in isn.getEdges():
        model.addConstraint(
            model.createExpression().quicksum(vehicle_capacity[modality]*frequencies[modality][line] for modality in line_modalities for line in lines_on_link[modality][link]) +
            model.createExpression().quicksum(beta_sum[modality][area][link]*vehicle_capacity[modality] for modality in rpool_modalities for area in areas_on_link[modality][link]),
            ConstraintSense.GREATER_EQUAL, link.getLoad(),f"l_{link.getId()}")
        model.addConstraint(
            model.createExpression().quicksum(infra_capacity_weight[modality]*frequencies[modality][line] for modality in line_modalities for line in lines_on_link[modality][link]) +
            model.createExpression().quicksum(beta_sum[modality][area][link]*infra_capacity_weight[modality] for modality in rpool_modalities for area in areas_on_link[modality][link]),
            ConstraintSense.LESS_EQUAL, link.getCapacity(),f"u_{link.getId()}")

    for modality in rpool_modalities:
        for area in ride_pool[modality].getAreas():
            model.addConstraint(model.createExpression().quicksum(edge.getLowerBound()*area.getStretchFactor(edge.getId())*beta[modality][area][edge] for edge in area.getEdges()), ConstraintSense.LESS_EQUAL, period*number_vehicles[modality][area], f"sum_area_time_{modality}_{area.getId()}")


    logger.debug("Add parameters")
    parameters.setSolverParameters(model)
    model.setSense(OptimizationSense.MINIMIZE)
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/lprp-beta.lp")
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
        for modality in line_modalities:
            for line in line_pool[modality].getLines():
                line.setFrequency(int(round(model.getValue(frequencies[modality][line]))))
        for modality in rpool_modalities:
            for area in ride_pool[modality].getAreas():
                area.setNumberOfVehicles(int(round(model.getValue(number_vehicles[modality][area]))))
                if area.getNumberOfVehicles() == 0:
                    for edge in area.getEdges():
                        area.setDistribution(edge.getId(), 0)
                else:
                    for edge in area.getEdges():
                        area.setDistribution(edge.getId(), model.getValue(beta[modality][area][edge])/area.getNumberOfVehicles())
        model.dispose()
        solver.dispose()
        return True
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            model.computeIIS("line-planning/lprp-beta.ilp")
        model.dispose()
        solver.dispose()
        return False