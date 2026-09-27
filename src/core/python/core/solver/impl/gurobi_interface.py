from typing import Union

import gurobipy, logging, math

from core.exceptions.exceptions import LinTimException
from core.exceptions.solver_exceptions import SolverAttributeNotImplementedException, SolverParamNotImplementedException
from core.solver import generic_solver_interface
from core.solver.generic_solver_interface import Solver, Variable, VariableType, LinearExpression, Constraint, \
    ConstraintSense, DoubleParam, IntParam, DoubleAttribute, Status, IntAttribute, OptimizationSense, SolverType, Model

from gurobipy import *

from core.solver.solver_parameters import SolverParameters, _logger


class GurobiVariable(Variable):

    def __init__(self, var: Var):
        self._var = var

    def getName(self) -> str:
        return self._var.varname

    def getType(self) -> VariableType:
        v_type = self._var.vtype
        if v_type == GRB.CONTINUOUS:
            return VariableType.CONTINUOUS
        if v_type == GRB.BINARY:
            return VariableType.BINARY
        if v_type == GRB.INTEGER:
            return VariableType.INTEGER
        else:
            raise LinTimException(f"Unknown variable type {v_type}")

    def getLowerBound(self) -> float:
        return self._var.lb

    def getUpperBound(self) -> float:
        return self._var.up

    def __add__(self, other):
        other_expr = extract_gurobi_expression(other)
        return GurobiLinearExpression(LinExpr(other_expr + self._var))

    def __sub__(self, other):
        other_expr = extract_gurobi_expression(other)
        return GurobiLinearExpression(LinExpr(self._var - other_expr))

    def __rsub__(self, other):
        other_expr = extract_gurobi_expression(other)
        return GurobiLinearExpression(LinExpr(other_expr - self._var))

    def __mul__(self, other: float):
        if isinstance(other, (int, float)):
            return GurobiLinearExpression(LinExpr(other * self._var))
        else:
            raise LinTimException(f"Try to multiply {other} with gurobi variable, this is not supported")

    def __rmul__(self, other: float):
        if isinstance(other, (int, float)):
            return GurobiLinearExpression(LinExpr(other * self._var))
        else:
            raise LinTimException(f"Try to multiply {other} with gurobi variable, this is not supported")


class GurobiLinearExpression(LinearExpression):
    def __init__(self, expr: LinExpr):
        self._expr = expr

    def add(self, other: Union["LinearExpression", Variable]):
        self.multiAdd(1, other)

    def multiAdd(self, multiple: float, other: Union["LinearExpression", Variable]):
        other_expr = extract_gurobi_expression(other)
        self._expr.add(other_expr, multiple)

    def clear(self):
        self._expr.clear()

    def addConstant(self, value: Union[int, float]):
        self._expr.addConstant(value)

    @staticmethod
    def quicksum(expressions):
        return GurobiLinearExpression(gurobipy.quicksum(extract_gurobi_expression(expr) for expr in expressions))

    def __add__(self, other):
        other_expr = extract_gurobi_expression(other)
        return GurobiLinearExpression(LinExpr(other_expr + self._expr))

    def __sub__(self, other):
        other_expr = extract_gurobi_expression(other)
        return GurobiLinearExpression(LinExpr(self._expr - other_expr))

    def __rsub__(self, other):
        other_expr = extract_gurobi_expression(other)
        return GurobiLinearExpression(LinExpr(other_expr - self._expr))

    def __mul__(self, other):
        if isinstance(other, (int, float)):
            return GurobiLinearExpression(LinExpr(other * self._expr))
        else:
            raise LinTimException(f"Try to multiply {other} with gurobi linear expression, this is not supported")

    def __rmul__(self, other: float):
        if isinstance(other, (int, float)):
            return GurobiLinearExpression(LinExpr(other * self._expr))
        else:
            raise LinTimException(f"Try to multiply {other} with gurobi linear expression, this is not supported")


class GurobiConstraint(Constraint):

    def __init__(self, constr: Constr):
        self._constr = constr

    def getName(self) -> str:
        return self._constr.constrname

    def getSense(self) -> ConstraintSense:
        sense = self._constr.sense
        if sense == GRB.GREATER_EQUAL:
            return ConstraintSense.GREATER_EQUAL
        if sense == GRB.LESS_EQUAL:
            return ConstraintSense.LESS_EQUAL
        if sense == GRB.EQUAL:
            return ConstraintSense.EQUAL
        raise LinTimException(f"Unknown constraint sense {sense}")

    def getRhs(self) -> float:
        return self._constr.rhs

    def setRhs(self, value: float) -> None:
        self._constr.rhs = value


def raise_for_non_gurobi_variable(var: Variable):
    if not isinstance(var, GurobiVariable):
        raise LinTimException("Try to work with non gurobi variable in gurobi context")


def extract_gurobi_expression(expression: Union[GurobiLinearExpression, GurobiVariable, int, float]) -> Union[LinExpr, Var, int, float]:
    if isinstance(expression, GurobiLinearExpression):
        return expression._expr
    elif isinstance(expression, GurobiVariable):
        return expression._var
    elif isinstance(expression, (int, float)):
        return expression
    else:
        raise LinTimException("Try to work with non gurobi objects in gurobi context")


class GurobiFilter(logging.Filter):
    """
    Filter to remove gurobi output, since otherwise the output will be on stdout and in the logger. Solution is based on
    an answer provided at https://stackoverflow.com/a/58547894 by Irv
    """
    def __init__(self, name="GurobiFilter"):
        super().__init__(name)

    def filter(self, record):
        return False


class GurobiModel(generic_solver_interface.Model):

    def __init__(self, model: Model):
        self._model = model
        grbfilter = GurobiFilter()

        grblogger = logging.getLogger('gurobipy')
        if grblogger is not None:
            grblogger.addFilter(grbfilter)
            grblogger = grblogger.getChild('gurobipy')
            if grblogger is not None:
                grblogger.addFilter(grbfilter)

    def getOriginalModel(self):
        return self._model

    def addVariable(self, lower_bound: float = 0, upper_bound: float = math.inf,
                    var_type: VariableType = VariableType.CONTINUOUS, objective: float = 0, name: str = "") -> Variable:
        var_type = GurobiModel.transform_var_type(var_type)
        var = self._model.addVar(lower_bound, upper_bound, objective, var_type, name)
        return GurobiVariable(var)

    @staticmethod
    def transform_var_type(type: VariableType) -> str:
        if type == VariableType.CONTINUOUS:
            return GRB.CONTINUOUS
        if type == VariableType.INTEGER:
            return GRB.INTEGER
        if type == VariableType.BINARY:
            return GRB.BINARY
        raise LinTimException(f"Unknown variable type {type}")

    def addConstraint(self, lhs: Union[LinearExpression, Variable, float], sense: ConstraintSense,
                      rhs: Union[LinearExpression, Variable, float], name: str = "") -> Constraint:
        con_sense = GurobiModel.transform_constraint_sense(sense)
        lhs_expr = extract_gurobi_expression(lhs)
        rhs_expr = extract_gurobi_expression(rhs)
        constr = self._model.addLConstr(lhs_expr, con_sense, rhs_expr, name)
        return GurobiConstraint(constr)

    @staticmethod
    def transform_constraint_sense(sense: ConstraintSense) -> str:
        if sense == ConstraintSense.GREATER_EQUAL:
            return GRB.GREATER_EQUAL
        if sense == ConstraintSense.LESS_EQUAL:
            return GRB.LESS_EQUAL
        if sense == ConstraintSense.EQUAL:
            return GRB.EQUAL
        else:
            raise LinTimException(f"Unknown constraint sense {sense}")

    def getVariableByName(self, name: str) -> Variable:
        return GurobiVariable(self._model.getVarByName(name))

    def setStartValue(self, variable: GurobiVariable, value: float):
        raise_for_non_gurobi_variable(variable)
        variable._var.start = value

    def setObjective(self, objective: Union[GurobiLinearExpression, GurobiVariable],
                     sense: OptimizationSense):
        expr = extract_gurobi_expression(objective)
        obj_sense = GurobiModel.transform_objective_sense(sense)
        self._model.setObjective(expr, obj_sense)

    @staticmethod
    def transform_objective_sense(sense: OptimizationSense):
        if sense == OptimizationSense.MAXIMIZE:
            return GRB.MAXIMIZE
        if sense == OptimizationSense.MINIMIZE:
            return GRB.MINIMIZE
        raise LinTimException(f"Unknown optimization sense {sense}")

    def getObjective(self) -> LinearExpression:
        objective = self._model.getObjective()
        if not isinstance(objective, LinExpr):
            raise LinTimException("We only support linear objectives")
        return GurobiLinearExpression(objective)

    def createExpression(self) -> GurobiLinearExpression:
        return GurobiLinearExpression(LinExpr())

    def getSense(self) -> OptimizationSense:
        sense = self._model.modelsense
        if sense == GRB.MINIMIZE:
            return OptimizationSense.MINIMIZE
        else:
            return OptimizationSense.MAXIMIZE

    def write(self, filename: str):
        self._model.write(filename)

    def setSense(self, sense: OptimizationSense):
        obj_sense = GurobiModel.transform_objective_sense(sense)
        self._model.modelsense = obj_sense

    def getStatus(self) -> Status:
        return self.transform_gurobi_status(self._model.status)

    def transform_gurobi_status(self, status: int) -> Status:
        if status == GRB.OPTIMAL:
            return Status.OPTIMAL
        if self._model.solcount > 0:
            return Status.FEASIBLE
        return Status.INFEASIBLE

    def solve(self):
        self._model.optimize()

    def computeIIS(self, filename: str):
        self._model.computeIIS()
        self._model.write(filename)

    def getValue(self, variable: GurobiVariable):
        raise_for_non_gurobi_variable(variable)
        return variable._var.x

    def getIntAttribute(self, attribute: IntAttribute) -> int:
        self._model.update()
        if attribute == IntAttribute.NUM_VARIABLES:
            return self._model.numvars
        if attribute == IntAttribute.NUM_CONSTRAINTS:
            return self._model.numconstrs
        if attribute == IntAttribute.NUM_BIN_VARIABLES:
            return self._model.numbinvars
        if attribute == IntAttribute.NUM_INT_VARIABLES:
            return self._model.numintvars
        if attribute == IntAttribute.NUM_SOLUTIONS:
            return self._model.solcount
        raise SolverAttributeNotImplementedException(SolverType.GUROBI, attribute.name)

    def getDoubleAttribute(self, attribute: DoubleAttribute):
        if attribute == DoubleAttribute.MIP_GAP:
            try:
                return self._model.mipgap
            except (AttributeError, gurobipy.GurobiError):
                # Not available for LP-models or after trivial presolve
                return 0.0
        if attribute == DoubleAttribute.OBJ_VAL:
            return self._model.ObjVal
        if attribute == DoubleAttribute.RUNTIME:
            return self._model.runtime
        raise SolverAttributeNotImplementedException(SolverType.GUROBI, attribute.name)

    def setIntParam(self, param: IntParam, value: int):
        if param == IntParam.TIMELIMIT:
            if value >= 0:
                self._model.Params.timelimit = value
        elif param == IntParam.OUTPUT_LEVEL:
            if value == logging.DEBUG:
                self._model.Params.logtoconsole = 1
                self._model.Params.logfile = "SolverGurobi.log"
            else:
                self._model.Params.logtoconsole = 0
        elif param == IntParam.THREADS:
            if value > 0:
                self._model.Params.threads = value
        else:
            raise SolverParamNotImplementedException(SolverType.GUROBI, param.name)

    def setDoubleParam(self, param: DoubleParam, value: float):
        if param == DoubleParam.MIP_GAP:
            if value >= 0:
                self._model.Params.mipgap = value
        else:
            raise SolverParamNotImplementedException(SolverType.GUROBI, param.name)

    def dispose(self) -> None:
        self._model.dispose()

    def update(self) -> None:
        self._model.update()


class GurobiSolver(Solver):

    def __init__(self):
        self._env = Env()

    def createModel(self) -> generic_solver_interface.Model:
        return GurobiModel(Model(env=self._env))

    def dispose(self) -> None:
        self._env.dispose()


def setGurobiSolverParameters(model: gurobipy.Model, parameters: SolverParameters):
    if parameters.getTimelimit() > 0:
        _logger.debug(f"Set Gurobi timelimit to {parameters.getTimelimit()}")
        model.Params.timelimit = parameters.getTimelimit()
    if parameters.getThreadLimit() > 0:
        _logger.debug(f"Set Gurobi thread limit to {parameters.getThreadLimit()}")
        model.Params.threads = parameters.getThreadLimit()
    if parameters.getMipGap() > 0:
        _logger.debug(f"Set Gurobi mip gap to {parameters.getMipGap()}")
        model.Params.mipgap = parameters.getMipGap()
    if parameters.outputSolverMessages():
        model.Params.logtoconsole = 1
        model.Params.logfile = "SolverGurobi.log"
    else:
        model.Params.logtoconsole = 0