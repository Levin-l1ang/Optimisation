import logging
import math
from typing import Union

import pulp
from pulp import LpVariable, LpAffineExpression, LpConstraint, constants, LpProblem, LpSolver, CPLEX_CMD, XPRESS, SCIP, \
    GLPK, PULP_CBC_CMD, lpSum, HiGHS, MOSEK, COPT_DLL

from core.exceptions.exceptions import LinTimException
from core.exceptions.solver_exceptions import SolverAttributeNotImplementedException, SolverNotImplementedException, \
    SolverParamNotImplementedException
from core.solver.generic_solver_interface import Variable, VariableType, LinearExpression, Constraint, Model, \
    ConstraintSense, DoubleParam, IntParam, DoubleAttribute, Status, IntAttribute, OptimizationSense, SolverType, Solver


logger = logging.getLogger(__name__)


class PulpVariable(Variable):

    def __init__(self, var: LpVariable):
        self._var = var

    def getName(self) -> str:
        return self._var.getName()

    def getType(self) -> VariableType:
        if self._var.isFree():
            return VariableType.CONTINUOUS
        elif self._var.isBinary():
            return VariableType.BINARY
        elif self._var.isInteger():
            return VariableType.INTEGER
        else:
            raise LinTimException(f"Unknown variable type: {self._var.cat}")

    def getLowerBound(self) -> float:
        return self._var.getLb()

    def getUpperBound(self) -> float:
        upper_bound = self._var.getUb()
        if upper_bound is None:
            upper_bound = math.inf
        return upper_bound

    def __add__(self, other):
        other_expr = extract_pulp_expression(other)
        return PulpLinearExpression(self._var + other_expr)

    def __sub__(self, other):
        other_expr = extract_pulp_expression(other)
        return PulpLinearExpression(self._var - other_expr)

    def __rsub__(self, other):
        other_expr = extract_pulp_expression(other)
        return PulpLinearExpression(other_expr - self._var)

    def __mul__(self, other: float):
        if isinstance(other, (int, float)):
            return PulpLinearExpression(other * self._var)
        else:
            raise LinTimException("You can only multiply a variable or expression with a constant (int or float).")

    def __rmul__(self, other: float):
        if isinstance(other, (int, float)):
            return PulpLinearExpression(other * self._var)
        else:
            raise LinTimException("You can only multiply a variable or expression with a constant (int or float).")

    def __str__(self) -> str:
        return f"PuLp Variable: {self._var}"


class PulpLinearExpression(LinearExpression):

    def __init__(self, expr: LpAffineExpression):
        self._expr = expr

    def add(self, other: Union["LinearExpression", Variable]):
        other_expr = extract_pulp_expression(other)
        self._expr.addInPlace(other_expr)

    def multiAdd(self, multiple: float, other: Union["LinearExpression", Variable]):
        other_expr = extract_pulp_expression(other)
        self._expr.addInPlace(multiple * other_expr)

    def clear(self):
        self._expr = LpAffineExpression()

    def addConstant(self, value: float):
        self._expr.constant += value

    @staticmethod
    def quicksum(expressions):
        return PulpLinearExpression(lpSum(extract_pulp_expression(expr) for expr in expressions))

    def __add__(self, other):
        other_expr = extract_pulp_expression(other)
        return PulpLinearExpression(self._expr + other_expr)

    def __sub__(self, other):
        other_expr = extract_pulp_expression(other)
        return PulpLinearExpression(self._expr - other_expr)

    def __rsub__(self, other):
        other_expr = extract_pulp_expression(other)
        return PulpLinearExpression(other_expr - self._expr)

    def __mul__(self, other: float):
        if isinstance(other, (int, float)):
            return PulpLinearExpression(other * self._expr)
        else:
            raise LinTimException("You can only multiply a variable or expression with a constant (int or float).")

    def __rmul__(self, other: float):
        if isinstance(other, (int, float)):
            return PulpLinearExpression(other * self._expr)
        else:
            raise LinTimException("You can only multiply a variable or expression with a constant (int or float).")

    def __str__(self) -> str:
        return f"PuLp Expression: {self._expr}"

class PulpConstraint(Constraint):

    def __init__(self, constr: LpConstraint):
        self._constr = constr

    def getName(self) -> str:
        return self._constr.getName()

    def getSense(self) -> ConstraintSense:
        if self._constr.sense == constants.LpConstraintEQ:
            return ConstraintSense.EQUAL
        if self._constr.sense == constants.LpConstraintGE:
            return ConstraintSense.GREATER_EQUAL
        if self._constr.sense == constants.LpConstraintLE:
            return ConstraintSense.LESS_EQUAL
        raise LinTimException(f"Unknown constraint sense {self._constr.sense}")

    def getRhs(self) -> float:
        return self._constr.constant

    def setRhs(self, value: float) -> None:
        self._constr.constant = value


    def __str__(self) -> str:
        return f"PuLp Constraint: {self._constr}"


def raise_for_non_pulp_variable(var: Variable):
    if not isinstance(var, PulpVariable):
        raise LinTimException("Try to work with non pulp variable in pulp context")


def extract_pulp_expression(expression: Union[PulpVariable, PulpLinearExpression, int, float]) -> Union[LpAffineExpression, LpVariable, int, float]:
    if isinstance(expression, PulpLinearExpression):
        return expression._expr
    if isinstance(expression, PulpVariable):
        return expression._var
    if isinstance(expression, (int, float)):
        return expression
    raise LinTimException("Try to work with non pulp objects in pulp context")


class PulpModel(Model):

    def __init__(self, solver_type: SolverType):
        self._model = LpProblem()
        self._objective = LpAffineExpression()
        self._sense = OptimizationSense.MINIMIZE
        if not solver_type:
            raise LinTimException("Did not get a solver type for PulpModel, this is necessary to continue")
        self._solver_type = solver_type
        self._parameters = {}
        self._warm_start = False
        self.vars_counter = 0
        self.constrs_counter = 0

    def dispose(self) -> None:
        del self._model
        del self._objective

    def getOriginalModel(self):
        return self._model

    def addVariable(self, lower_bound: float = 0, upper_bound: float = math.inf,
                    var_type: VariableType = VariableType.CONTINUOUS, objective: float = 0, name: str = "") -> Variable:
        if var_type == VariableType.CONTINUOUS:
            var_type = constants.LpContinuous
        elif var_type == VariableType.INTEGER:
            var_type = constants.LpInteger
        elif var_type == VariableType.BINARY:
            var_type = constants.LpBinary
        else:
            raise LinTimException(f"Unknown var type {var_type}")
        if upper_bound == math.inf:
            upper_bound = None
        if lower_bound == -math.inf:
            lower_bound = None
        # The PuLP solvers, in particular SCIP, requires unique var and constr names! In order to have this, we add the prefixes var_i_ and constraint_i_ to all names.
        if not name:
            name = f"var_{self.vars_counter + 1}"
            self.vars_counter += 1
        else:
            name = f"var_{self.vars_counter + 1}_" +name.translate(str.maketrans('-+/*<>%=|: ', '___________'))
            self.vars_counter += 1
        new_var = LpVariable(name, lower_bound, upper_bound, var_type)
        self._model.addVariable(new_var)
        if objective != 0:
            self._objective.addterm(new_var, objective)
            self._model.setObjective(self._objective)
        return PulpVariable(new_var)


    def addConstraint(self, lhs: Union[LinearExpression, Variable, float], sense: ConstraintSense,
                      rhs: Union[LinearExpression, Variable, float], name: str = "") -> Constraint:
        if sense == ConstraintSense.EQUAL:
            sense = constants.LpConstraintEQ
        elif sense == ConstraintSense.LESS_EQUAL:
            sense = constants.LpConstraintLE
        elif sense == ConstraintSense.GREATER_EQUAL:
            sense = constants.LpConstraintGE
        else:
            raise LinTimException(f"Unknown constraint sense {sense}")
        lhs_expr = extract_pulp_expression(lhs)
        rhs_expr = extract_pulp_expression(rhs)
        # The PuLP solvers, in particular SCIP, requires unique var and constr names! In order to have this, we add the prefixes var_i_ and constraint_i_ to all names.
        if not name:
            name = f"constraint_{self.constrs_counter + 1}"
            self.constrs_counter += 1
        else:
            name = f"constraint_{self.constrs_counter + 1}_" + name.translate(str.maketrans('-+/*<>%=|: ', '___________'))
            self.constrs_counter += 1
        constr = LpConstraint(lhs_expr - rhs_expr, sense, name)
        self._model.addConstraint(constr)
        return PulpConstraint(constr)

    def getVariableByName(self, name: str) -> Variable:
        return PulpVariable(self._model.variablesDict()[name])

    def setStartValue(self, variable: PulpVariable, value: float):
        raise_for_non_pulp_variable(variable)
        variable._var.setInitialValue(value)
        self._warm_start = True

    def setObjective(self, objective: Union[PulpLinearExpression, PulpVariable], sense: OptimizationSense):
        expr = extract_pulp_expression(objective)
        if isinstance(expr, (LpAffineExpression, LpVariable)):
            self._objective = expr
        else: # constant is set as objective
            self._objective.constant = expr
        self._model.setObjective(self._objective)
        self.setSense(sense)

    def getObjective(self) -> LinearExpression:
        return PulpLinearExpression(self._objective)

    def createExpression(self) -> LinearExpression:
        return PulpLinearExpression(LpAffineExpression())

    def getSense(self) -> OptimizationSense:
        sense = self._model.getSense()
        if sense == constants.LpMinimize:
            return OptimizationSense.MINIMIZE
        if sense == constants.LpMaximize:
            return OptimizationSense.MAXIMIZE
        raise LinTimException(f"Unknown optimization sense {sense}")

    def write(self, filename: str):
        self._model.writeLP(filename)

    def setSense(self, sense: OptimizationSense):
        if sense == OptimizationSense.MAXIMIZE:
            sense = constants.LpMaximize
        elif sense == OptimizationSense.MINIMIZE:
            sense = constants.LpMinimize
        else:
            raise LinTimException(f"Unknown optimization sense {sense}")
        self._model.sense = sense

    def getStatus(self) -> Status:
        status = self._model.sol_status
        if status == constants.LpSolutionIntegerFeasible:
            return Status.FEASIBLE
        if status == constants.LpSolutionOptimal:
            return Status.OPTIMAL
        if status == constants.LpSolutionNoSolutionFound or status == constants.LpSolutionInfeasible:
            # Some solvers (e.g. XPRESS, MOSEK) return sol_status=-1 even when a feasible
            # solution exists (e.g. after a time limit: "PRIMAL_FEASIBLE").
            # Fall back to checking whether the objective actually has a value.
            model_status = self._model.status
            if (model_status != constants.LpStatusInfeasible
                    and model_status != constants.LpStatusUnbounded
                    and pulp.value(self._model.objective) is not None):
                logger.debug(f"sol_status={status} but objective value is present, treating as FEASIBLE")
                return Status.FEASIBLE
            return Status.INFEASIBLE
        raise LinTimException(f"Unknown pulp solution status {status}")

    def solve(self):
        self._model.setObjective(self._objective)
        solver = self._initialize_solver()

        # Workaround: PuLP's variablesDict() only covers variables appearing in
        # objective/constraints. SCIP can output additional variables in the .sol file
        # (e.g., after a restart with global fixings). Patch assignVarsVals to handle
        # unknown variable names gracefully instead of raising KeyError.
        model = self._model
        original_assign = model.assignVarsVals

        def _safe_assign_vars_vals(values):
            # Build complete name->var dict from ALL registered variables
            all_vars = {v.name: v for v in model.variables()}
            for name, value in values.items():
                if name != "__dummy":
                    if name in all_vars:
                        all_vars[name].varValue = value
                    else:
                        logger.debug(f"Skipping variable not found in PuLP model: {name}")

        model.assignVarsVals = _safe_assign_vars_vals
        try:
            model.solve(solver)
        finally:
            model.assignVarsVals = original_assign
            del solver

    def _initialize_solver(self) -> LpSolver:
        # Initialize the four possible parameters
        threads = None
        timelimit = None
        msg = False
        mip_gap = None
        for name, value in self._parameters.items():
            if name == IntParam.THREADS:
                if value > 0:
                    threads = value
            elif name == IntParam.OUTPUT_LEVEL:
                if value == logging.DEBUG:
                    msg = True
            elif name == IntParam.TIMELIMIT:
                if value > 0:
                    timelimit = value
            elif name == DoubleParam.MIP_GAP:
                if value > 0:
                    mip_gap = value
            else:
                raise SolverParamNotImplementedException(self._solver_type, name)
        if self._solver_type == SolverType.CPLEX:
            return CPLEX_CMD(timeLimit=timelimit, msg=msg, threads=threads, gapRel=mip_gap, warmStart=self._warm_start)
        elif self._solver_type == SolverType.XPRESS:
            options = []
            if threads:
                options.append(f"THREADS={threads}")
            return XPRESS(msg=msg, timeLimit=timelimit, gapRel=mip_gap, options=options, warmStart=self._warm_start)
        elif self._solver_type == SolverType.SCIP:
            options = []
            if threads:
                logger.warning("Setting a thread limit is not supported by the PuLP SCIP interface, skip")
            if timelimit:
                options.append(f"limits/time = {timelimit}")
            if self._warm_start:
                logger.warning("Warm start is not supported by the PuLP SCIP interface, skip")
            return SCIP(msg=msg, gapRel=mip_gap, options=options)
        elif self._solver_type == SolverType.GLPK:
            if threads:
                logger.warning("Setting a thread limit is not supported by the PuLP GLPK interface, skip")
            if self._warm_start:
                logger.warning("Warm start is not supported by the PuLP GLPK interface, skip")
            options = []
            if mip_gap:
                options.append("--mipgap")
                options.append(str(mip_gap))
            return GLPK(msg=msg, timeLimit=timelimit, options=options)
        elif self._solver_type == SolverType.CBC:
            return PULP_CBC_CMD(msg=msg, timeLimit=timelimit, gapRel=mip_gap,
                                warmStart=self._warm_start)
        elif self._solver_type == SolverType.HIGHS:
            if self._warm_start:
                logger.warning("Warm start is not supported by the PuLP HiGHS interface, skip")
            return HiGHS(msg=msg, timeLimit=timelimit, gapRel=mip_gap, threads=threads)
        elif self._solver_type == SolverType.MOSEK:
            options = {}
            if threads:
                options["MSK_IPAR_NUM_THREADS"] = threads
            if mip_gap:
                options["MSK_DPAR_MIO_TOL_REL_GAP"] = mip_gap
            if self._warm_start:
                logger.warning("Warm start is not supported by the PuLP MOSEK interface, skip")
            return MOSEK(msg=msg, timeLimit=timelimit, options=options)
        elif self._solver_type == SolverType.COPT:
            kwargs = {"msg": msg}
            if timelimit is not None:
                kwargs["timeLimit"] = timelimit
            if mip_gap is not None:
                kwargs["RelGap"] = mip_gap
            if threads is not None:
                kwargs["Threads"] = threads
            if self._warm_start:
                kwargs["warmStart"] = True
            return COPT_DLL(**kwargs)
        else:
            raise SolverNotImplementedException(self._solver_type)

    def computeIIS(self, filename: str):
        raise NotImplementedError("Computing an IIS is not implemented for Pulp solver interface")

    def getValue(self, variable: PulpVariable):
        raise_for_non_pulp_variable(variable)
        return variable._var.valueOrDefault()

    def getIntAttribute(self, attribute: IntAttribute) -> int:
        if attribute == IntAttribute.NUM_VARIABLES:
            return self._model.numVariables()
        if attribute == IntAttribute.NUM_INT_VARIABLES:
            return len([var for var in self._model.variables() if var.isInteger()])
        if attribute == IntAttribute.NUM_BIN_VARIABLES:
            return len([var for var in self._model.variables() if var.isBinary()])
        if attribute == IntAttribute.NUM_CONSTRAINTS:
            return self._model.numConstraints()
        if attribute == IntAttribute.NUM_SOLUTIONS:
            logger.warning("IntAttribute.NUM_SOLUTIONS is not implemented for PuLP interface, determine based on solution status")
            if self.getStatus() == Status.FEASIBLE or self.getStatus() == Status.OPTIMAL:
                return 1
            else:
                return 0
        raise SolverAttributeNotImplementedException(self._solver_type, attribute.name)

    def getDoubleAttribute(self, attribute: DoubleAttribute) -> float:
        if attribute == DoubleAttribute.OBJ_VAL:
            return pulp.value(self._model.objective)
        if attribute == DoubleAttribute.RUNTIME:
            return self._model.solutionTime
        if attribute == DoubleAttribute.MIP_GAP:
            logger.warning("DoubleAttribute MIP_GAP is not implemented for PuLP interface, give error value")
            return -1
        raise SolverAttributeNotImplementedException(self._solver_type, attribute.name)

    def setIntParam(self, param: IntParam, value: int):
        self._parameters[param] = value

    def setDoubleParam(self, param: DoubleParam, value: float):
        self._parameters[param] = value

    def update(self):
        pass


class PulpSolver(Solver):

    def __init__(self, solver_type: SolverType = None):
        self.solver_type = solver_type

    def createModel(self) -> Model:
        return PulpModel(self.solver_type)

    def dispose(self) -> None:
        # Nothing to do here
        pass