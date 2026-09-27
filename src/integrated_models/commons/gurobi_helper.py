from gurobipy import Model

from parameters import SolverParameters


def initialize_gurobi_model(parameters: SolverParameters, name: str) -> Model:
    m = Model(name)
    if parameters.time_limit != -1:
        m.params.timeLimit = parameters.time_limit
    if parameters.mip_gap >= 0:
        m.params.MIPGap = parameters.mip_gap
    if parameters.n_threads > 0:
        m.params.threads = parameters.n_threads
    if not parameters.show_solver_output:
        m.params.LogToConsole = 0
    return m