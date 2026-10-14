"""Solver back-ends for the sparse model builder: SciPy/HiGHS (default) and Gurobi.

``solve_gurobi`` consumes exactly the rows built by ``model._Builder`` and returns an
object with the same attributes SciPy's ``milp`` result has (status, x, fun, message,
mip_gap, mip_dual_bound), so the rest of the code is solver-agnostic.

Gurobi needs ``pip install gurobipy`` and a licence (free academic licence; the
pip-bundled restricted licence only handles models with up to 2000 variables and
2000 linear constraints, far below the sizes used here).  Only long-standing
gurobipy calls are used (addVar, LinExpr, addLConstr, Params, optimize, attributes).
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import List, Optional, Sequence

import numpy as np

# SciPy ``milp`` status codes understood by the callers
OPTIMAL, LIMIT, INFEASIBLE, UNBOUNDED, ERROR = 0, 1, 2, 3, 4


@dataclass
class SolverResult:
    status: int
    x: Optional[np.ndarray]
    fun: Optional[float]
    message: str
    mip_gap: Optional[float] = None
    mip_dual_bound: Optional[float] = None
    runtime: Optional[float] = None


def import_gurobi():
    try:
        import gurobipy as gp
    except ImportError as exc:  # pragma: no cover - depends on the environment
        raise RuntimeError(
            "solver='gurobi' needs the gurobipy package: pip install gurobipy "
            "(and a licence: https://www.gurobi.com/academia/academic-program-and-licenses/)") from exc
    return gp


def solve_gurobi(
    cost: Sequence[float],
    lower: Sequence[float],
    upper: Sequence[float],
    integrality: Sequence[int],
    rows: Sequence[dict],
    row_lower: Sequence[float],
    row_upper: Sequence[float],
    start: Optional[dict] = None,
    time_limit: Optional[float] = None,
    mip_gap: float = 0.0,
    threads: Optional[int] = None,
    output: bool = False,
    params: Optional[dict] = None,
) -> SolverResult:
    gp = import_gurobi()
    GRB = gp.GRB
    model = gp.Model("line_planning")
    model.Params.OutputFlag = 1 if output else 0
    if time_limit is not None:
        model.Params.TimeLimit = float(time_limit)
    model.Params.MIPGap = float(mip_gap)
    if threads:
        model.Params.Threads = int(threads)
    for name, value in (params or {}).items():  # explicit Gurobi tuning, recorded in the result
        setattr(model.Params, name, value)

    variables: List = []
    for c, lo, hi, is_int in zip(cost, lower, upper, integrality):
        hi = GRB.INFINITY if math.isinf(hi) else float(hi)
        variables.append(model.addVar(lb=float(lo), ub=hi, obj=float(c),
                                      vtype=GRB.BINARY if is_int else GRB.CONTINUOUS))
    model.ModelSense = GRB.MINIMIZE
    model.update()

    for row, lo, hi in zip(rows, row_lower, row_upper):
        if not row:
            continue
        expr = gp.LinExpr(list(row.values()), [variables[i] for i in row])
        if lo == hi:
            model.addLConstr(expr, GRB.EQUAL, float(lo))
            continue
        if not math.isinf(hi):
            model.addLConstr(expr, GRB.LESS_EQUAL, float(hi))
        if not math.isinf(lo):
            model.addLConstr(expr, GRB.GREATER_EQUAL, float(lo))

    if start:  # partial MIP start: only the listed variables (the line/frequency plan)
        for index, value in start.items():
            variables[index].Start = float(value)

    model.optimize()
    solutions = model.SolCount
    code = {GRB.OPTIMAL: OPTIMAL, GRB.INFEASIBLE: INFEASIBLE, GRB.UNBOUNDED: UNBOUNDED,
            GRB.INF_OR_UNBD: INFEASIBLE}.get(model.Status)
    if code is None:
        code = LIMIT if model.Status in (GRB.TIME_LIMIT, GRB.SOLUTION_LIMIT, GRB.INTERRUPTED,
                                         GRB.ITERATION_LIMIT, GRB.NODE_LIMIT) else ERROR
    x = None
    fun = None
    gap = bound = None
    if solutions > 0:
        x = np.asarray(model.getAttr("X", variables), dtype=float)
        fun = float(model.ObjVal)
        gap = float(model.MIPGap) if model.IsMIP else 0.0
    if model.IsMIP and code != INFEASIBLE:
        try:
            bound = float(model.ObjBound)
        except Exception:  # pragma: no cover
            bound = None
    result = SolverResult(code, x, fun, f"gurobi status {model.Status}", gap, bound, float(model.Runtime))
    model.dispose()
    return result
