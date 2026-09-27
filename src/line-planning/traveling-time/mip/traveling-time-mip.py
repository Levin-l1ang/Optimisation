import logging
import sys
from collections import defaultdict
from collections.abc import Mapping, Sequence
from typing import Dict

from core.exceptions.algorithm_dijkstra import AlgorithmStoppingCriterionException
from core.exceptions.config_exceptions import ConfigNoFileNameGivenException
from core.exceptions.exceptions import LinTimException
from core.io.config import ConfigReader
from core.io.lines import LineReader, LineWriter
from core.io.od import ODReader
from core.io.ptn import PTNReader
from core.model.change_and_go import CGEdge, CGNode, CGArcType
from core.model.graph import Graph
from core.model.lines import Line
from core.model.od import OD
from core.model.ptn import Link
from core.solver.generic_solver_interface import Solver, VariableType, \
    ConstraintSense, OptimizationSense, Status, IntAttribute, Variable, \
    LinearExpression, DoubleAttribute
from core.util.change_and_go_method import build_cg_graph
from parameters import Parameters

logger = logging.getLogger(__name__)

class LinearCostModel:
    """This model assumes that the cost of serving a line with frequency f
    is f times the cost of the line, given in the pool cost file."""

    def __init__(self, od: OD, cgn: Graph[CGNode, CGEdge],
                 line_arcs: Mapping[Line, Sequence[CGEdge]],
                 parameters: Parameters, cgn_total_time_difference: float):
        logger.debug("Initializing solver and model")
        self.solver = Solver.createSolver(parameters.getSolverType())
        self.model = self.solver.createModel()
        od_pairs_by_origin = od.getODPairsByOriginID()
        self.passenger_flows : Dict[int, Dict[CGEdge, Variable]] = {}
        logger.debug("Adding variables")
        self.frequencies: Dict[Line, Variable] = {
            line: self.model.addVariable(
                0, float('inf'), parameters.frequency_type, 0,
                f"f_{line.getId()}")
            for line in line_pool.getLines()
        }
        self.passenger_flows = {
            origin: {
                arc: self.model.addVariable(
                    0, float('inf'), parameters.flow_type,
                    objective=arc.cost, name=f"x_{origin}^{arc.id}")
                for arc in cgn.getEdges()
            }
            for origin in od_pairs_by_origin.keys()
        }

        logger.debug("Adding constraints")
        # The number of passengers using and arc in the Change-&-Go network is
        # bounded by the frequency times the vehicle capacity.
        N = parameters.vehicle_capacity
        for line in line_pool.getLines():
            for arc in line_arcs[line]:
                passengers_on_arc = self.model.createExpression()
                for origin in od_pairs_by_origin.keys():
                    passengers_on_arc.add(self.passenger_flows[origin][arc])
                passengers_on_arc.multiAdd(-N, self.frequencies[line])
                self.model.addConstraint(passengers_on_arc,
                                         ConstraintSense.LESS_EQUAL,
                                         0, f"10_{(line.line_id, arc.id)}")

        # The passengers form a multi-commodity flow in the Change-&-Go network
        # respecting the given demand values.
        for origin, od_pairs in od_pairs_by_origin.items():
            for node in cgn.getNodes():
                incoming_flow = self.model.createExpression()
                for edge in cgn.getIncomingEdges(node):
                    incoming_flow.add(self.passenger_flows[origin][edge])
                outgoing_flow = self.model.createExpression()
                for edge in cgn.getOutgoingEdges(node):
                    outgoing_flow.add(self.passenger_flows[origin][edge])
                excess = incoming_flow - outgoing_flow
                rhs = 0
                if node.line_id == 0:
                    if node.stop_id == origin:
                        rhs = -sum(od_pair.value for od_pair in od_pairs)
                    else:
                        for od_pair in od_pairs:
                            if node.stop_id == od_pair.destination:
                                rhs = od_pair.value
                self.model.addConstraint(
                    excess, ConstraintSense.EQUAL, rhs,
                    f"11_{(origin, node.id)}")

        if parameters.use_loads:
            # The total frequencies of service along each edge in the PTN must
            # obey the given bounds.
            edge_frequencies: Dict[Link, LinearExpression] \
                = defaultdict(self.model.createExpression)
            for line in line_pool.getLines():
                for edge in line.getLinePath().getEdges():
                    edge_frequencies[edge].add(self.frequencies[line])
            for edge, edge_frequency in edge_frequencies.items():
                self.model.addConstraint(
                    edge_frequency, ConstraintSense.LESS_EQUAL,
                    edge.upper_frequency_bound, f"13_{edge.link_id}")
                self.model.addConstraint(
                    edge_frequency, ConstraintSense.GREATER_EQUAL,
                    edge.lower_frequency_bound, f"lfb_{edge.link_id}")

        cost = self.model.createExpression()
        for line in line_pool.getLines():
            cost.multiAdd(line.cost, self.frequencies[line])
        if parameters.minimize_time:
            self.model.addConstraint(cost, ConstraintSense.LESS_EQUAL,
                                     parameters.budget, "budget")
            self.model.setSense(OptimizationSense.MINIMIZE)
        else:
            self.model.getOriginalModel().update() # TODO: works only for Gurobi!
            traveling_time = self.model.getObjective()
            time_budget_cg = parameters.time_budget + cgn_total_time_difference
            self.model.addConstraint(traveling_time, ConstraintSense.LESS_EQUAL,
                                     time_budget_cg, 'traveling_time')
            self.model.setObjective(cost, OptimizationSense.MINIMIZE)

    def getOriginalModel(self):
        return self.model

    def solve(self):
        self.model.solve()

    def getStatus(self):
        return self.model.getStatus()

    def computeIIS(self, filename: str):
        self.model.computeIIS(filename)

    def write(self, filename: str):
        self.model.write(filename)

    def getValue(self, var: Variable):
        return self.model.getValue(var)

    def getDoubleAttribute(self, attr: DoubleAttribute):
        return self.model.getDoubleAttribute(attr)

    def getIntAttribute(self, attr: IntAttribute):
        return self.model.getIntAttribute(attr)

    def get_frequency(self, line: Line) -> float:
        return self.getValue(self.frequencies[line])

if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise ConfigNoFileNameGivenException()
    logger.info("Start reading configuration")
    config = ConfigReader.read(sys.argv[1])
    parameters = Parameters(config)
    logger.info("Finished reading configuration")

    logger.info("Begin reading input data")
    ptn = PTNReader.read(read_loads=parameters.use_loads)
    line_pool = LineReader.read(ptn, read_frequencies=False)
    od = ODReader.read()
    if parameters.flow_type == VariableType.INTEGER and not all(
            pair.value.is_integer() for pair in od.getODPairs()):
        raise LinTimException(
            "Non-integral demands cannot be satisfied by integral flow.")
    logger.info("Finished reading input data")

    logger.info("Begin execution (solve line planning traveling time model)")

    logger.debug("Building Change-&-Go network")
    cgn = build_cg_graph(
        line_concept=line_pool.getLines(),
        directed=ptn.isDirected(),
        model_drive=parameters.driving_time_model,
        model_wait=parameters.waiting_time_model,
        min_wait_time=parameters.min_waiting_time,
        max_wait_time=parameters.max_waiting_time,
        create_transfer_arcs=False,
        change_penalty=parameters.change_penalty,
        min_change_time=parameters.min_transfer_time,
        period_length=parameters.period_length
    )
    cg_time_difference = parameters.change_penalty
    if not parameters.include_departure_adaption_time:
        cg_time_difference += parameters.min_transfer_time / 2
    if not parameters.include_arrival_adaption_time:
        cg_time_difference += parameters.min_transfer_time / 2
    cg_total_time_difference = od.computeNumberOfPassengers() * cg_time_difference

    line_arcs = defaultdict(list)
    for a in cgn.getEdges():
        if a.getType() == CGArcType.LINE:
            line_arcs[line_pool.getLine(a.getLeftNode().getLineId())].append(a)

    model = LinearCostModel(od, cgn, line_arcs, parameters,
                            cg_total_time_difference)

    logger.debug("Setting solver parameters")
    parameters.setSolverParameters(model.getOriginalModel())
    if parameters.writeLpFile():
        logger.debug("Writing lp file")
        model.write("line-planning/travelingTimeModel.lp")
        logger.debug("Finished writing lp file")

    logger.debug("Start optimization")
    model.solve()
    logger.debug("Finished optimization")

    status = model.getStatus()
    if model.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger.debug("Optimal solution found")
            logger.info(f"Optimal MIP objective: {model.getDoubleAttribute(DoubleAttribute.OBJ_VAL)}")
            if parameters.minimize_time:
                logger.info(f"Actual objective: {model.getDoubleAttribute(DoubleAttribute.OBJ_VAL) - cg_total_time_difference}")
        else:
            logger.debug("Feasible solution found")
        for line in line_pool.getLines():
            line.setFrequency(int(model.get_frequency(line)))
    else:
        logger.debug("No feasible solution found")
        if status == Status.INFEASIBLE:
            model.computeIIS("line-planning/travelingTimeModel.ilp")
        raise AlgorithmStoppingCriterionException(
            "traveling time model for line planning")
    logger.info("Finished execution (solve of line planning traveling time "
                "model)")

    logger.info("Begin writing output data")
    LineWriter.write(line_pool, write_pool=False, write_costs=False)
    logger.info("Finished writing output data")
