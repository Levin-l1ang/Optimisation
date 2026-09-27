import logging

from pyomo.opt.base.solvers import SolverFactoryClass, SolverFactory

from core.solver.generic_solver_interface import SolverType

logger = logging.getLogger(__name__)


def get_solver(solver_type: SolverType) -> SolverFactoryClass:
    if solver_type == SolverType.GUROBI:
        return SolverFactory("gurobi_direct")
    elif solver_type == SolverType.CPLEX:
        return SolverFactory("cplex_direct")
    elif solver_type == SolverType.GLPK:
        return SolverFactory("glpk")
    elif solver_type == SolverType.XPRESS:
        return SolverFactory("xpress")
    else:
        logger.warning(f"Warning: {solver_type} is not fully supported of the pyomo interface. "
                       f"We're not sure everything will work correctly...")
    return SolverFactory(solver_type.name.lower())


def add_time_limit(solver: SolverFactoryClass, solver_type: SolverType, time_limit: int):
    if time_limit < 0:
        return
    if solver_type == SolverType.GUROBI:
        solver.options["TimeLimit"] = time_limit
    elif solver_type == SolverType.CPLEX:
        solver.options["timelimit"] = time_limit
    elif solver_type == SolverType.GLPK:
        solver.options["tmlim"] = time_limit
    elif solver_type == SolverType.XPRESS:
        # TODO: Find correct xpress command
        pass
    else:
        solver.options['timelimit'] = time_limit


def add_mip_gap(solver: SolverFactoryClass, solver_type: SolverType, mip_gap: float):
    if mip_gap < 0:
        return
    if solver_type == SolverType.GUROBI:
        solver.options["mipgap"] = mip_gap
    elif solver_type == SolverType.CPLEX:
        solver.options["mip_tolerances_mipgap"] = mip_gap
    elif solver_type == SolverType.GLPK:
        # TODO: Find correct glpk command
        pass
    elif solver_type == SolverType.XPRESS:
        # TODO: Find correct xpress command
        pass
    else:
        solver.options['mipgap'] = mip_gap


def limit_threads(solver: SolverFactoryClass, solver_type: SolverType, threads: int):
    if threads < 0:
        return
    if solver_type == SolverType.GUROBI:
        solver.options["threads"] = threads
        # TODO: Find correct gurobi command
        return
    elif solver_type == SolverType.CPLEX:
        solver.options["threads"] = threads
    elif solver_type == SolverType.GLPK:
        # TODO: Find correct glpk command
        pass
    elif solver_type == SolverType.XPRESS:
        # TODO: Find correct xpress command
        pass
    else:
        solver.options['threads'] = threads
