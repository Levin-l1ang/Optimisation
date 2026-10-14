"""Minimal stand-in for ``gurobipy`` used only to test the adapter in ``benchmark_2026.solvers``.

It implements exactly the calls the adapter makes (Model, addVar, LinExpr, addLConstr, Params,
optimize, X/ObjVal/ObjBound/MIPGap/Status/SolCount/Runtime/IsMIP, Var.Start) on top of SciPy/HiGHS.
It checks the *interface contract* (rows, bounds, senses, warm start), not Gurobi's numerics.
"""
import time
import types

import numpy as np
from scipy.optimize import Bounds, LinearConstraint, milp
from scipy.sparse import coo_matrix


class GRB:
    INFINITY = 1e100
    BINARY, CONTINUOUS = "B", "C"
    MINIMIZE = 1
    LESS_EQUAL, GREATER_EQUAL, EQUAL = "<", ">", "="
    OPTIMAL, INFEASIBLE, INF_OR_UNBD, UNBOUNDED, TIME_LIMIT = 2, 3, 4, 5, 9
    SOLUTION_LIMIT, INTERRUPTED, ITERATION_LIMIT, NODE_LIMIT = 10, 11, 7, 8


class Var:
    def __init__(self, index, lb, ub, obj, vtype):
        self.index, self.lb, self.ub, self.obj, self.vtype = index, lb, ub, obj, vtype
        self.Start = None


class LinExpr:
    def __init__(self, coeffs, variables):
        assert len(coeffs) == len(variables)
        self.coeffs, self.variables = list(coeffs), list(variables)


class _Params:
    OutputFlag = 0
    TimeLimit = None
    MIPGap = 0.0
    Threads = 0


class Model:
    instances = []

    def __init__(self, name=""):
        self.Params = _Params()
        self.vars, self.cons = [], []
        self.ModelSense = 1
        self.Status, self.SolCount = 0, 0
        self.IsMIP = False
        self.disposed = False
        Model.instances.append(self)

    def addVar(self, lb=0.0, ub=GRB.INFINITY, obj=0.0, vtype=GRB.CONTINUOUS):
        v = Var(len(self.vars), lb, ub, obj, vtype)
        self.vars.append(v)
        return v

    def update(self):
        pass

    def addLConstr(self, expr, sense, rhs):
        self.cons.append((expr, sense, rhs))

    def optimize(self):
        n = len(self.vars)
        r, c, v, lo, hi = [], [], [], [], []
        for k, (expr, sense, rhs) in enumerate(self.cons):
            for coef, var in zip(expr.coeffs, expr.variables):
                r.append(k); c.append(var.index); v.append(coef)
            lo.append(rhs if sense in (GRB.EQUAL, GRB.GREATER_EQUAL) else -np.inf)
            hi.append(rhs if sense in (GRB.EQUAL, GRB.LESS_EQUAL) else np.inf)
        A = coo_matrix((v, (r, c)), shape=(len(self.cons), n)).tocsr()
        ub = [np.inf if x >= GRB.INFINITY else x for x in (var.ub for var in self.vars)]
        self.IsMIP = any(var.vtype == GRB.BINARY for var in self.vars)
        started = time.time()
        res = milp(c=[var.obj for var in self.vars], integrality=[1 if var.vtype == GRB.BINARY else 0 for var in self.vars],
                   bounds=Bounds([var.lb for var in self.vars], ub),
                   constraints=LinearConstraint(A, lo, hi), options={"mip_rel_gap": self.Params.MIPGap})
        self.Runtime = time.time() - started
        self.Status = {0: GRB.OPTIMAL, 2: GRB.INFEASIBLE, 3: GRB.UNBOUNDED}.get(res.status, GRB.TIME_LIMIT)
        self._x = res.x
        self.SolCount = 0 if res.x is None else 1
        if res.x is not None:
            self.ObjVal = float(res.fun)
            bound = getattr(res, "mip_dual_bound", None)
            self.ObjBound = float(res.fun if bound is None else bound)
            self.MIPGap = float(getattr(res, "mip_gap", 0.0) or 0.0)

    def getAttr(self, name, variables):
        assert name == "X"
        return [self._x[v.index] for v in variables]

    def dispose(self):
        self.disposed = True


def install():
    """Return a module object that can be put in ``sys.modules['gurobipy']``."""
    module = types.ModuleType("gurobipy")
    module.Model, module.GRB, module.LinExpr = Model, GRB, LinExpr
    return module
