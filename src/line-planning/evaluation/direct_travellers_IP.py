from core.model.graph import Graph
from core.model.ptn import Link, Stop
from core.model.lines import LinePool
from core.model.od import OD
from typing import Dict, List, Tuple

from core.solver.generic_solver_interface import VariableType, OptimizationSense, ConstraintSense, DoubleAttribute, \
    Solver, Status
from parameters import Parameters


def direct_travellers_IP(ptn: Graph[Stop, Link], lines: LinePool, od: OD,
                         edge_to_shortest_paths: Dict[Link, Dict[int, List[Tuple[int, int]]]],
                         preferable_line_ids, parameters: Parameters):
    solver = Solver.createSolver(parameters.getSolverType())
    m = solver.createModel()
    parameters.setSolverParameters(m)

    d = {}
    for origin in ptn.getNodes():
        d[origin.getId()] = {}
        for destination in ptn.getNodes():
            passengers = od.getValue(origin.getId(), destination.getId())
            if passengers == 0:
                continue
            d[origin.getId()][destination.getId()] = {}
            sum_for_od_pair = m.createExpression()
            for line_id in preferable_line_ids[(origin.getId(), destination.getId())]:
                d[origin.getId()][destination.getId()][line_id] = m.addVariable(0, passengers,
                                                                                     VariableType.INTEGER, 1,
                                                                                     name=f"d[{origin.getId()},{destination.getId()},{line_id}]")
                sum_for_od_pair.add(d[origin.getId()][destination.getId()][line_id])
            m.addConstraint(sum_for_od_pair, ConstraintSense.LESS_EQUAL, passengers,
                            f"demand[{origin.getId()},{destination.getId()}]")

    m.setSense(OptimizationSense.MAXIMIZE)

    for edge in ptn.getEdges():
        for line_id, od_pair_list in edge_to_shortest_paths[edge].items():
            sum_of_direct_travelers = m.createExpression()
            for origin_id, destination_id in od_pair_list:
                sum_of_direct_travelers.add(d[origin_id][destination_id][line_id])
            m.addConstraint(sum_of_direct_travelers, ConstraintSense.LESS_EQUAL,
                            parameters.capacity * lines.getLine(line_id).getFrequency(),
                            f"capacity[{edge.getId()},{line_id}]")
    if parameters.writeLpFile():
        m.write("line-planning/direct_eval.lp")
    m.solve()
    status = m.getStatus()
    if status == Status.INFEASIBLE:
        m.computeIIS("line-planning/direct-eval.ilp")

    return m.getDoubleAttribute(DoubleAttribute.OBJ_VAL)
