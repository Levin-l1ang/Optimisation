import logging
import sys
from collections import defaultdict
from collections.abc import Mapping, Sequence
from typing import Dict

from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.lines import LineReader, LineWriter
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.model.graph import Graph
from core.model.lines import LinePool
from core.model.ptn import Stop, Link
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, \
    IntAttribute
from core.solver.solver_parameters import SolverParameters

logger = logging.getLogger(__name__)


def solve_lineplanning_problem(ptn: Graph[Stop, Link], line_pool: LinePool, parameters: SolverParameters, vehicle_capacity: int, use_start_solution: bool = False,
                               objective: bool = True) -> bool:
    """ 
    Line planning model minimizes cost of line concept with a passenger flow in the PTN. If the PTN is undirected, it creates forward and backward arcs for the flow.

    :param ptn: the PTN
    :type ptn: Graph[Stop, Link]
    :param line_pool: the line pool
    :type line_pool: LinePool
    :param parameters: solver parameters
    :type parameters: SolverParameters
    :param vehicle_capacity: capacity of a vehicle
    :type vehicle_capacity: int
    :param use_start_solution: whether to set frequencies as a start solution for the optimization, defaults to False
    :type use_start_solution: bool, optional
    :param objective: whether the model should have an objective, otherwise objective is constant zero, defaults to True
    :type objective: bool, optional
    :return: whether a feasible solution was found
    :rtype: bool
    """
    
    # direct PTN if it is not already directed
    directed = ptn.isDirected()
    if not directed:
        max_id = 0
        for edge in ptn.getEdges():
            if edge.getId() > max_id:
                max_id = edge.getId()
        edges: list[Link] = []
        for edge in ptn.getEdges():
            forward = Link(link_id = edge.getId(),
                               left_stop=edge.getLeftNode(),
                               right_stop=edge.getRightNode(),
                               length = edge.getLength(),
                               lower_bound=edge.getLowerBound(),
                               upper_bound=edge.getUpperBound(),
                               directed = True)
            backward = Link(link_id = edge.getId()+max_id+1,
                            left_stop=edge.getRightNode(),
                            right_stop=edge.getLeftNode(),
                            length = edge.getLength(),
                            lower_bound=edge.getLowerBound(),
                            upper_bound=edge.getUpperBound(),
                            directed = True)
            edges.append(forward)
            edges.append(backward)
    else: 
        edges = ptn.getEdges()


    # initialize solver and model
    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()

    # VARIABLES
    logger.debug("Add variables")
    # FREQUENCIES
    frequencies = {}
    for line in line_pool.getLines():
        if objective:
            frequency = model.addVariable(0, float('inf'), VariableType.INTEGER, line.getCost(), f"f_{line.getId()}")
        else:
            frequency = model.addVariable(0, float('inf'), VariableType.INTEGER, 0, f"f_{line.getId()}")
        frequencies[line] = frequency
    # if config parameter lc_use_start_solution true, set frequencies from line concept as start solution
    if use_start_solution:
        for line in line_pool.getLines():
            f = int(line.getFrequency())
            model.setStartValue(frequencies[line], f)
    # PASSENGER FLOW
    od_pairs_by_origin = od.getODPairsByOriginID()
    flow_vars = {}
    for origin in od_pairs_by_origin.keys():
        flow_vars[origin] = {}
        for edge in edges:
            var = model.addVariable(0, float('inf'), VariableType.CONTINUOUS, 0, f"flow_{origin}_{edge.getId()}")
            flow_vars[origin][edge] = var
    

    # CONSTRAINTS
    logger.debug("Adding constraints")

    # aggregated flow constraints
    for origin, od_pairs in od_pairs_by_origin.items():
        for node in ptn.getNodes():
            incoming = model.createExpression()
            outgoing = model.createExpression()
            for edge in edges:
                if edge.getRightNode().getId() == node.getId():
                    incoming.add(flow_vars[origin][edge])
                if edge.getLeftNode().getId() == node.getId():
                    outgoing.add(flow_vars[origin][edge])
            excess = incoming - outgoing
            rhs = 0
            if node.getId() == origin:
                rhs = -sum([od_pair.getValue() for od_pair in od_pairs])
            else:
                for od_pair in od_pairs:
                    if node.getId() == od_pair.destination:
                        rhs = od_pair.getValue()
            model.addConstraint(excess, ConstraintSense.EQUAL, rhs, f"flow_{origin, node.getId()}")

    # frequency constraints
    for edge in edges:
        summe = model.createExpression()
        for origin in od_pairs_by_origin.keys():
            summe.add(flow_vars[origin][edge])
        freq_sum = model.createExpression()
        for line in line_pool.getLines():
            for line_edge in line.getLinePath().getEdges():
                if directed:
                    if line_edge.getId()==edge.getId():
                        freq_sum.multiAdd(vehicle_capacity,frequencies[line])
                else:
                    if line_edge.getId()==edge.getId() or line_edge.getId()+max_id+1==edge.getId():
                        freq_sum.multiAdd(vehicle_capacity,frequencies[line])
        model.addConstraint(summe, ConstraintSense.LESS_EQUAL, freq_sum, f"frequencies_edge_{edge.getId()}")
                
    
    # solver parameters
    logger.debug("Add parameters")
    parameters.setSolverParameters(model)
    model.setSense(OptimizationSense.MINIMIZE)

    # write solver stuff
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/costModelODrouting.lp")
        logger.debug("Finished writing lp file")
    
    # optimize
    logger.debug("Start optimization")
    model.solve()
    logger.debug("Finished optimization")

    # read and get solution
    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
        else:
            logger.debug("Feasible solution found")
        for line in line_pool.getLines():
            line.setFrequency(int(round(model.getValue(frequencies[line]))))
        model.dispose()
        solver.dispose()
        return True
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            model.computeIIS("line-planning/costModelODrouting.ilp")
        model.dispose()
        solver.dispose()
        return False


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    parameters = SolverParameters(config, "lc_")
    vehicle_capacity = config.getIntegerValue("gen_passengers_per_vehicle")
    lc_model = config.getStringValue("lc_model")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read(read_loads=True, config=config)
    use_solution = config.getBooleanValue("lc_cost_od_use_start_solution")
    if use_solution:
        line_pool = LineReader.read(ptn, read_frequencies=True)
    else:
        line_pool = LineReader.read(ptn, read_frequencies=False)
    od = ODReader.read(config=config)
    logger.info("Finished reading input data")

    logger.info("Begin execution of line planning cost model with passenger routing")
    feasible = solve_lineplanning_problem(ptn, line_pool, parameters, vehicle_capacity, use_solution)
    if not feasible:
        raise AlgorithmStoppingCriterionException("line planning cost model passenger routing")
    logger.info("Finished execution of line planning cost model with passenger routing")

    logger.info("Begin writing output data")
    LineWriter.write(line_pool, write_pool=False, write_costs=False, config=config)
    logger.info("Finished writing output data")
