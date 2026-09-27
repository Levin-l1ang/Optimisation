import logging
from pydoc import locate

from event_activity_network import PeriodicEventActivityNetwork

import preprocessing as pp
from core.solver.generic_solver_interface import SolverType
from helper import Parameters

logger_ = logging.getLogger(__name__)


def solve(ean: PeriodicEventActivityNetwork, parameters: Parameters):
    logger_.debug("Run: solve")
    pp.deleting_light_edges(ean, parameters)

    ean.calculate_span()
    ean.calculate_nx_graph()
    ean.calculate_nx_spanning_tree()
    ean.calculate_cycle_base()

    if parameters.getSolverType() == SolverType.GUROBI:
        class_name = "gurobi_solver"
    else:
        class_name = "core_solver"
    solver_class = locate(class_name)
    solver_class.naive_solve(ean, parameters)
