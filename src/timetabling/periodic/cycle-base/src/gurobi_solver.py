import logging

import gurobipy as gp
from gurobipy import GRB

from core.solver.impl.gurobi_interface import GurobiFilter, setGurobiSolverParameters
from event_activity_network import PeriodicEventActivityNetwork

from helper import Parameters

logger_ = logging.getLogger(__name__)

# workaround to avoid double logging output
grbfilter = GurobiFilter()
grblogger = logging.getLogger('gurobipy')
if grblogger is not None:
    grblogger.addFilter(grbfilter)
    grblogger = grblogger.getChild('gurobipy')
    if grblogger is not None:
        grblogger.addFilter(grbfilter)


def naive_solve(ean: PeriodicEventActivityNetwork, parameters: Parameters):
    logger_.debug("Run: naive_solve with gurobi")

    tensions = {}
    modulo_parameter = {}
    m = gp.Model('naive_solve')

    setGurobiSolverParameters(m, parameters)

    # set additional parameters
    if parameters.solution_limit > 0:
        m.setParam("SolutionLimit", parameters.solution_limit)
    if parameters.best_bound_stop > 0:
        m.setParam("BestBdStop", parameters.best_bound_stop)
    if parameters.mip_focus > 0:
        m.setParam("MIPFocus", parameters.mip_focus)

    # add variables for each edge
    for edge in ean.graph.getEdges():
        tensions[edge] = m.addVar(lb=edge.getLowerBound(), ub=edge.getUpperBound(),
                                  vtype=GRB.INTEGER, name='tension_{}'.format(edge.getId()))

    # add cycle constraints
    for edge, incidence_vector in ean.network_matrix_dict.items():
        modulo_parameter[edge] = m.addVar(lb=-GRB.INFINITY, ub=GRB.INFINITY, vtype=GRB.INTEGER,
                                          name='mod_par_' + str(edge.getId()))

        con_lhs = gp.LinExpr()
        for e, val in incidence_vector.items():
            if val == 1:
                con_lhs += tensions[e]
            elif val == -1:
                con_lhs -= tensions[e]
            else:
                logger_.error("Wrong values in network_matrix_dict.")
                raise ValueError

        con_rhs = gp.LinExpr(parameters.period_length * modulo_parameter[edge])
        m.addLConstr(lhs=con_lhs, sense=GRB.EQUAL, rhs=con_rhs, name='cycle_{}'.format(edge.getId()))

    # add objective
    obj = gp.LinExpr()
    for edge in ean.graph.getEdges():
        obj += edge.getNumberOfPassengers() * tensions[edge]

    m.setObjective(obj, GRB.MINIMIZE)

    # set starting solution if wanted
    if parameters.use_old_solution:
        m.update()
        for key, val in tensions.items():
            start_value = (key.getRightNode().getTime() - key.getLeftNode().getTime()) % parameters.period_length
            while start_value < val.lb:
                start_value += parameters.period_length
            while start_value > val.ub:
                start_value -= parameters.period_length

            val.start = start_value

    if parameters.writeLpFile():
        logger_.debug("Write lp file")
        m.write("timetabling/Cycle-Base.lp")
    # optimize
    logger_.info("Start optimizing.")
    m.optimize()
    logger_.info("End optimizing.")

    runtime = m.getAttr("Runtime")
    logger_.info("Pure optimization runtime (in sec): {}".format(round(runtime, 4)))

    if m.status == GRB.INFEASIBLE:
        logger_.error("Model infeasible!")
        m.computeIIS()
        m.write("timetabling/Cycle-Base.ilp")
        exit(1)
    elif m.status == GRB.TIME_LIMIT:
        logger_.info("Time limit reached.")
    elif m.status == GRB.SOLUTION_LIMIT:
        logger_.info("Solution limit reached.")
    elif m.status == GRB.USER_OBJ_LIMIT:
        logger_.info("Objective bound: {}".format(m.getAttr("ObjBound")))
        logger_.info("Objective value: {}".format(m.getAttr("ObjVal")))
        logger_.info("Best bound value reached!")

    if m.SolCount == 0:
        logger_.error("No feasible solution found.")
        exit(1)

    for edge, val in tensions.items():
        if abs(round(val.x) - val.x > 1e-7):
            logger_.warning("For edge {}: rounded {} to {}.".format(edge.getId(), val.x, round(val.x)))
        ean.tensions[edge] = round(val.x)
