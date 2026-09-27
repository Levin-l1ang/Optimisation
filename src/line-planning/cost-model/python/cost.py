import logging
import sys

from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.io.config import ConfigReader
from core.io.lines import LineReader, LineWriter
from core.io.ptn import PTNReader
from core.model.graph import Graph
from core.model.lines import LinePool
from core.model.ptn import Stop, Link
from core.solver.generic_solver_interface import Solver, VariableType, ConstraintSense, OptimizationSense, Status, \
    IntAttribute
from core.solver.solver_parameters import SolverParameters

logger = logging.getLogger(__name__)


def solve_lineplanning_problem(ptn: Graph[Stop, Link], line_pool: LinePool, parameters: SolverParameters,
                               objective: bool = True) -> bool:
    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()
    logger.debug("Add variables")
    frequencies = {}
    for line in line_pool.getLines():
        if objective:
            frequency = model.addVariable(0, float('inf'), VariableType.INTEGER, line.getCost(), f"f_{line.getId()}")
        else:
            frequency = model.addVariable(0, float('inf'), VariableType.INTEGER, 0, f"f_{line.getId()}")
        frequencies[line] = frequency
    logger.debug("Adding constraints")
    for link in ptn.getEdges():
        sum_freq_per_line = model.createExpression()
        for line in line_pool.getLines():
            if link in line.getLinePath().getEdges():
                sum_freq_per_line.add(frequencies[line])
        model.addConstraint(sum_freq_per_line, ConstraintSense.LESS_EQUAL, link.getUpperFrequencyBound(),
                            f"u_{link.getId()}")
        model.addConstraint(sum_freq_per_line, ConstraintSense.GREATER_EQUAL, link.getLowerFrequencyBound(),
                            f"l_{link.getId()}")
    logger.debug("Add parameters")
    parameters.setSolverParameters(model)
    model.setSense(OptimizationSense.MINIMIZE)
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/costModel.lp")
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
        for line in line_pool.getLines():
            line.setFrequency(int(round(model.getValue(frequencies[line]))))
        model.dispose()
        solver.dispose()
        return True
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            model.computeIIS("line-planning/costModel.ilp")
        model.dispose()
        solver.dispose()
        return False


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    parameters = SolverParameters(config, "lc_")
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read(read_loads=True)
    line_pool = LineReader.read(ptn, read_frequencies=False)
    logger.info("Finished reading input data")

    logger.info("Begin execution of line planning cost model")
    feasible = solve_lineplanning_problem(ptn, line_pool, parameters)
    if not feasible:
        raise AlgorithmStoppingCriterionException("cost model line planning")
    logger.info("Finished execution of line planning cost model")

    logger.info("Begin writing output data")
    LineWriter.write(line_pool, write_pool=False, write_costs=False)
    logger.info("Finished writing output data")
