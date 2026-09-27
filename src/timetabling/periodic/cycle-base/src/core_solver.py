import logging

from core.solver.generic_solver_interface import Model, Solver, VariableType, ConstraintSense, IntAttribute, \
    DoubleAttribute, Status
from event_activity_network import PeriodicEventActivityNetwork

from helper import Parameters

logger_ = logging.getLogger(__name__)


def naive_solve(ean: PeriodicEventActivityNetwork, parameters: Parameters):
    logger_.debug("Run: naive_solve with core")

    tensions = {}
    modulo_parameter = {}
    solver = Solver.createSolver(parameters.getSolverType())
    m = solver.createModel()
    parameters.setSolverParameters(m)

    # add variables for each edge
    for edge in ean.graph.getEdges():
        tensions[edge] = m.addVariable(edge.getLowerBound(), edge.getUpperBound(), VariableType.INTEGER,
                                       edge.getNumberOfPassengers(), f"tension{edge.getId()}")

    # add cycle constraints
    for edge, incidence_vector in ean.network_matrix_dict.items():
        modulo_parameter[edge] = m.addVariable(-float('inf'), float('inf'), VariableType.INTEGER, 0,
                                               f"mod_par_{edge.getId()}")

        con_lhs = m.createExpression()
        for e, val in incidence_vector.items():
            if val == 1:
                con_lhs += tensions[e]
            elif val == -1:
                con_lhs -= tensions[e]
            else:
                logger_.error("Wrong values in network_matrix_dict.")
                raise ValueError

        con_rhs = m.createExpression()
        con_rhs.multiAdd(parameters.period_length, modulo_parameter[edge])
        m.addConstraint(con_lhs, ConstraintSense.EQUAL, con_rhs, f"cycle_{edge.getId()}")

    # set starting solution if wanted
    if parameters.use_old_solution:
        for key, val in tensions.items():
            start_value = (key.getRightNode().getTime() - key.getLeftNode().getTime()) % parameters.period_length
            while start_value < val.getLowerBound():
                start_value += parameters.period_length
            while start_value > val.getUpperBound():
                start_value -= parameters.period_length
            m.setStartValue(val, start_value)

    if parameters.writeLpFile():
        logger_.debug("Write lp file")
        m.write("timetabling/tim-cb.lp")

    # optimize
    logger_.info("Start optimizing.")
    m.solve()
    logger_.info("End optimizing.")

    runtime = m.getDoubleAttribute(DoubleAttribute.RUNTIME)
    logger_.info("Pure optimization runtime (in sec): {}".format(round(runtime, 4)))

    status = m.getStatus()

    if m.getIntAttribute(IntAttribute.NUM_SOLUTIONS) > 0:
        if status == Status.OPTIMAL:
            logger_.debug("Optimal solution found")
        else:
            logger_.debug("Feasible solution found")
        for edge, val in tensions.items():
            ean.tensions[edge] = round(m.getValue(val))
    else:
        logger_.error("No feasible solution found.")
        if status == Status.INFEASIBLE:
            logger_.debug("Try computing IIS")
            m.computeIIS("timetabling/tim-cb.ilp")
        exit(1)


