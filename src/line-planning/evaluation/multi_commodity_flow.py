import logging
from typing import Tuple

from core.model.change_and_go import CGNode, CGEdge, CGArcType
from core.model.graph import Graph
from core.model.lines import LinePool
from core.model.ptn import Link, Stop
from core.model.od import OD
from core.solver.generic_solver_interface import Solver, ConstraintSense, OptimizationSense, Status, DoubleAttribute
from parameters import Parameters

logger = logging.getLogger(__name__)

def capacitated_multi_commodity_flow(
        cgn: Graph[CGNode, CGEdge], ptn: Graph[Stop, Link], od: OD,
        lines: LinePool, parameters: Parameters, cgn_path_difference: float) -> Tuple[float, float]:

    solver = Solver.createSolver(parameters.getSolverType())
    model = solver.createModel()
    parameters.setSolverParameters(model)
    logger.debug("Create flow variables")
    # Only create flow for stops with positive outgoing demand
    demand_by_origin = {
        origin: sum(od.getValue(origin.getId(), destination.getId())
                    for destination in ptn.getNodes())
        for origin in ptn.getNodes()}
    flow_variables = {}
    for origin in demand_by_origin.keys():
        if demand_by_origin[origin] == 0:
            continue
        flow_variables[origin] = {}
        for edge in cgn.getEdges():
            if edge.getType() is CGArcType.LINE:
                ub = lines.getLine(edge.getLeftNode().getLineId()).getFrequency() * parameters.capacity
            else:
                ub = float('inf')
            flow_variables[origin][edge] = model.addVariable(
                upper_bound=ub, var_type=parameters.flow_type,
                objective=edge.getCost(), name=f"flow_{origin.getId()}_{edge}")
    logger.debug("Create capacity constraints")
    for edge in cgn.getEdges():
        if edge.getType() is CGArcType.LINE:
            ub = lines.getLine(
                edge.getLeftNode().getLineId()).getFrequency() \
                 * parameters.capacity
            flow = model.createExpression()
            for origin in demand_by_origin.keys():
                if demand_by_origin[origin]:
                    flow.add(flow_variables[origin][edge])
            model.addConstraint(flow, ConstraintSense.LESS_EQUAL, ub,
                                f"capacity_{edge.getId()}")
    logger.debug("Create flow conservation constraints")
    # Flow conservation
    for node in cgn.getNodes():
        for origin in demand_by_origin.keys():
            if demand_by_origin[origin] == 0:
                continue
            flow = model.createExpression()
            for edge in cgn.getOutgoingEdges(node):
                flow.add(flow_variables[origin][edge])
            for edge in cgn.getIncomingEdges(node):
                flow.multiAdd(-1, flow_variables[origin][edge])
            # determine the right-hand side
            # Is this the starting od node?
            if node.isOrigin() and node.getStopId() == origin.getId():
                rhs = demand_by_origin[origin]
            # Or the end od node?
            elif node.isDestination():
                rhs = -1 * od.getValue(origin.getId(), node.getStopId())
            # Otherwise, just pass through
            else:
                rhs = 0
            model.addConstraint(flow, ConstraintSense.EQUAL, rhs, f"flow_conservation_{origin.getId()}_{node}")
    logger.debug("Solve model")
    model.setSense(OptimizationSense.MINIMIZE)
    if (parameters.writeLpFile()):
        model.write("line-planning/lp_eval_capacitated_flow.lp")
    model.solve()
    logger.debug("Read back solution")
    if model.getStatus() == Status.INFEASIBLE:
        model.computeIIS("line-planning/lp_eval_capacitated_flow.ilp")
    # We need to remove the constant term to obtain the travel time
    number_of_passengers = od.computeNumberOfPassengers()
    travel_time = model.getDoubleAttribute(DoubleAttribute.OBJ_VAL) - number_of_passengers * cgn_path_difference
    travel_time /= number_of_passengers
    # Find the number of transfers
    number_of_transfers = 0
    for edge in cgn.getEdges():
        if edge.getType() in [CGArcType.TRANSFER, CGArcType.BOARD]:
            number_of_transfers += sum(
                model.getValue(flow_variables_origin[edge])
                for orign, flow_variables_origin in flow_variables.items()
                if orign.stop_id != edge.getLeftNode().getStopId())
    return travel_time, number_of_transfers