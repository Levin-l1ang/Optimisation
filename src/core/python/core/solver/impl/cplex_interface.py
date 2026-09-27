# from typing import Union
#
# from docplex.mp.constants import ComparisonType
# from docplex.mp.constr import LinearConstraint
# from docplex.mp.linear import Var, LinearExpr
#
# from core.exceptions.exceptions import LinTimException
# from core.solver import generic_solver_interface
# from core.solver.generic_solver_interface import Solver, Variable, VariableType, LinearExpression, Constraint, \
#     ConstraintSense, DoubleParam, IntParam, DoubleAttribute, Status, IntAttribute, OptimizationSense

# TODO: Implement cplex solver. Currently, cplex is covered by PuLP. Later one we can use the commented base class
# to work out a direct cplex interface to improve performance. Note that the currently commented code may be
# outdated w.r.t. the current generic interface version


# def raise_for_non_cplex_variable(var: Variable):
#     if not isinstance(var, CplexVariable):
#         raise LinTimException("Try to work with non cplex variable in cplex context")
#
#
# def raise_for_non_cplex_expression(expr: LinearExpression):
#     if not isinstance(expr, CplexLinearExpression):
#         raise LinTimException("Try to work with non cplex expression in cplex context")
#
# class CplexVariable(Variable):
#
#     def __init__(self, var: Var, name: str):
#         self._var = var
#         self._name = name
#
#     def getName(self) -> str:
#         return self._name
#
#     def getType(self) -> VariableType:
#         if self._var.is_integer():
#             return VariableType.INTEGER
#         if self._var.is_binary():
#             return VariableType.BINARY
#         if self._var.is_continuous():
#             return VariableType.CONTINUOUS
#         else:
#             raise LinTimException(f"Unknown variable type {self._var.vartype}")
#
#     def getLowerBound(self) -> float:
#         return self._var.lb
#
#     def getUpperBound(self) -> float:
#         return self._var.ub
#
#
# class CplexLinearExpression(LinearExpression):
#
#     def __init__(self, expr: LinearExpr):
#         self._expr = expr
#
#     def add(self, other: Union[LinearExpression, Variable]):
#         raise_for_non_cplex_expression(other)
#         self._expr.add(other._expr)
#
#     def multiAdd(self, multiple: float, other: Union[LinearExpression, Variable]):
#         raise_for_non_cplex_expression(other)
#         self._expr.add(other._expr.times(multiple))
#
#     def clear(self):
#         variables = list(self._expr.iter_variables())
#         for var in variables:
#             self._expr.remove_term(var)
#
#     def addConstant(self, value: float):
#         self._expr.add(value)
#
#
# class CplexConstraint(Constraint):
#
#     def __init(self, constr: LinearConstraint, name: str):
#         self._constr = constr
#         self._name = name
#
#     def getName(self) -> str:
#         return self._name
#
#     def getSense(self) -> ConstraintSense:
#         sense = self._constr.sense
#         if sense == ComparisonType.LE:
#             return ConstraintSense.LESS_EQUAL
#         if sense == ComparisonType.EQ:
#             return ConstraintSense.EQUAL
#         if sense == ComparisonType.GE:
#             return ConstraintSense.GREATER_EQUAL
#         raise LinTimException(f"Unknown constraint sense {sense}")
#
#     def getRhs(self) -> float:
#         rhs = self._constr.right_expr
#         if not isinstance(rhs, float):
#             raise LinTimException("Constraint with non-float rhs")
#         return rhs
#
#     def getLhs(self) -> LinearExpression:
#         return CplexLinearExpression(self._constr.left_expr)
#
#
# class CplexModel(generic_solver_interface.Model):
#
#     def get_original_model(self):
#         pass
#
#     def addVariable(self, lower_bound: float = 0, upper_bound: float = math.inf,
#                     var_type: VariableType = VariableType.CONTINUOUS, objective: float = 0, name: str = "") -> Variable:
#         pass
#
#     def addConstraint(self, expression: Union[LinearExpression, Variable, float], sense: ConstraintSense,
#                       rhs: Union[LinearExpression, Variable, float], name: str) -> Constraint:
#         pass
#
#     def getVariableByName(self, name: str) -> Variable:
#         pass
#
#     def setStartValue(self, variable: Variable, value: float):
#         pass
#
#     def setObjective(self, objective: LinearExpression, sense: OptimizationSense):
#         pass
#
#     def getObjective(self) -> LinearExpression:
#         pass
#
#     def createExpression(self) -> LinearExpression:
#         pass
#
#     def getSense(self) -> OptimizationSense:
#         pass
#
#     def write(self, filename: str):
#         pass
#
#     def setSense(self, sense: OptimizationSense):
#         pass
#
#     def getIntAttribute(self, attribute: IntAttribute) -> int:
#         pass
#
#     def getStatus(self) -> Status:
#         pass
#
#     def solve(self):
#         pass
#
#     def computeIIS(self, filename: str):
#         pass
#
#     def getValue(self, variable: Variable):
#         pass
#
#     def getDoubleAttribute(self, attribute: DoubleAttribute) -> float:
#         pass
#
#     def setIntParam(self, param: IntParam, value: int):
#         pass
#
#     def setDoubleParam(self, param: DoubleParam, value: float):
#         pass
#
#
# class CplexSolver(Solver):
#
#     def __init__(self):
#         print("Initialized cplex solver")
from core.solver.generic_solver_interface import Solver


class CplexSolver(Solver):

    def __init__(self):
        raise NotImplementedError("Cplex is not yet implemented in the python core")
