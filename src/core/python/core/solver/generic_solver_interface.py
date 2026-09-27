import logging
import math
from abc import ABCMeta, abstractmethod
from enum import Enum
from pydoc import locate
from typing import Union

from core.exceptions.exceptions import LinTimException


class VariableType(Enum):
    """
    Possible variable types
    """
    CONTINUOUS = "CONTINUOUS"
    INTEGER = "INTEGER"
    BINARY = "BINARY"


class Variable(metaclass=ABCMeta):
    """
    Abstract class for a generic variable. Create a variable by calling :func:`~core.solver.Model.addVariable`
    """

    @abstractmethod
    def getName(self) -> str:
        """
        Get the name of the variable
        @return: the name
        """
        raise NotImplementedError

    @abstractmethod
    def getType(self) -> VariableType:
        """
        Get the type of the variable
        @return: the variable type
        """
        raise NotImplementedError

    @abstractmethod
    def getLowerBound(self) -> float:
        """
        Get the lower bound of the variable
        @return: the lower bound
        """
        raise NotImplementedError

    @abstractmethod
    def getUpperBound(self) -> float:
        """
        Get the upper bound of the variable
        @return: the upper bound
        """
        raise NotImplementedError

    @abstractmethod
    def __add__(self, other):
        raise NotImplementedError

    def __radd__(self, other):
        return self.__add__(other)

    @abstractmethod
    def __sub__(self, other):
        raise NotImplementedError

    @abstractmethod
    def __rsub__(self, other):
        raise NotImplementedError

    @abstractmethod
    def __mul__(self, other: float):
        raise NotImplementedError

    @abstractmethod
    def __rmul__(self, other: float):
        raise NotImplementedError


class LinearExpression(metaclass=ABCMeta):
    """
    Class representing a linear expression of variables
    """

    @abstractmethod
    def add(self, other: Union["LinearExpression", Variable]) -> None:
        """
        Add the given object to this expression. The object may be another linear expression or a variable.
        @param other: the object to add
        """
        raise NotImplementedError

    @abstractmethod
    def multiAdd(self, multiple: float, other: Union["LinearExpression", Variable]) -> None:
        """
        Add a multiple of the given object to this expression. The object may be another linear expression or
        a variable.
        @param multiple: the value to multiply the other object with before adding
        @param other: the object to add
        """
        raise NotImplementedError

    @abstractmethod
    def clear(self):
        """
        Empty this linear expression
        """
        raise NotImplementedError

    @abstractmethod
    def addConstant(self, value: float):
        """
        Add a constant to this expression
        @param value: the constant to add
        """
        raise NotImplementedError

    @staticmethod
    @abstractmethod
    def quicksum(expressions):
        """
        Static method to create a linear expression quickly from a given list of Linear Expressions, variables and constants.
        @param expression: List of linear expressions, variables and constants which are added to create a new linear expression.
        """
        raise NotImplementedError

    @abstractmethod
    def __add__(self, other):
        raise NotImplementedError

    def __radd__(self, other):
        return self.__add__(other)

    @abstractmethod
    def __sub__(self, other):
        raise NotImplementedError

    @abstractmethod
    def __rsub__(self, other):
        raise NotImplementedError

    @abstractmethod
    def __mul__(self, other: float):
        raise NotImplementedError

    @abstractmethod
    def __rmul__(self, other: float):
        raise NotImplementedError


class ConstraintSense(Enum):
    """
    Possible senses for a constraint, i.e., whether the expression should be less or equal, equal, or greater or
    equal than the right hand side.
    """
    LESS_EQUAL = "LESS_EQUAL"
    EQUAL = "EQUAL"
    GREATER_EQUAL = "GREATER_EQUAL"


class Constraint(metaclass=ABCMeta):
    """
    Class containing the model for a generic constraint. Can be included in a model. Use
    :func:`~core.solver.Model.addConstraint` for the creation of a constraint
    """

    @abstractmethod
    def getName(self) -> str:
        """
        Get the name of the constraint
        @return: the name
        """
        raise NotImplementedError

    @abstractmethod
    def getSense(self) -> ConstraintSense:
        """
        Get the sense of the constraint
        @return: the sense
        """
        raise NotImplementedError

    @abstractmethod
    def getRhs(self) -> float:
        """
        Get the right hand side(rhs) of the constraint. Constraints will be reformulated s.t. all variables are on the
        left, therefore the rhs is a constant
        @return: the rhs of the constraint
        """
        raise NotImplementedError

    @abstractmethod
    def setRhs(self, rhs: float) -> None:
        """
        Set the right hand side (rhs) of the constraints. Constraints will be reformulated s.t. all variables are on
        the left, therefore the rhs is a constant
        @param rhs: the new rhs
        """
        raise NotImplementedError


class OptimizationSense(Enum):
    """
    The sense of an optimization problem
    """
    MAXIMIZE = "MAXIMIZE"
    MINIMIZE = "MINIMIZE"


class IntAttribute(Enum):
    """
    All possible integer attributes that can be queried from a model.
    - NUM_VARIABLES: The number of variables of the model
    - NUM_CONSTRAINTS: The number of constraints of the model
    - NUM_INT_VARIABLES: The number of integer variables of the model
    - NUM_BIN_VARIABLES: The number of binary variables of the model
    - NUM_SOLUTIONS: The number of solutions found. Note that this may throw an error when queried before the
        optimization
    """
    NUM_VARIABLES = "NUM_VARIABLES"
    NUM_CONSTRAINTS = "NUM_CONSTRAINTS"
    NUM_INT_VARIABLES = "NUM_INT_VARIABLES"
    NUM_BIN_VARIABLES = "NUM_BIN_VARIABLES"
    NUM_SOLUTIONS = "NUM_SOLUTIONS"


class DoubleAttribute(Enum):
    """
    All possible double attributes that can be queried from a model after calling :func:`~core.solver.Model.solve`
    - OBJ_VAL: The objective value of the current solution. Can only be called on solvers with status OPTIMAL or
        FEASIBLE and a feasible solution is present.
    - MIP_GAP: The mip gap of the best currently found solution. Note that this may throw an error when queried
        before the optimization  or if there is no feasible solution found
    - RUNTIME: The runtime needed to solve the problem. Note that this may throw an error when queried before the
        optimization
    """
    OBJ_VAL = "OBJ_VAL"
    MIP_GAP = "MIP_GAP"
    RUNTIME = "RUNTIME"


class DoubleParam(Enum):
    """
    All possible double parameters to set for a model. Can be set using :func:`~core.solver.Model.setDoubleParam`
    - MIP_GAP: The desired mip gap of the model, i.e., the solution process will be aborted if there is a solution
        found that has at least the given mip gap.
    """
    MIP_GAP = "MIP_GAP"


class IntParam(Enum):
    """
    All possible integer parameters to set for a model. Can be set using :func:`~core.solver.Model.setIntParam`
    - TIMELIMIT: The timelimit for the model. A negative value is interpreted as no timelimit at all, positive
        integers represent the number of seconds allowed for solving.
    - OUTPUT_LEVEL: The desired output level of the solver. Use the log levels of :class:`logging`
    - THREADS: The number of threads the solver is allowed to use. Use -1 for no restriction.
    """
    TIMELIMIT = "TIMELIMIT"
    OUTPUT_LEVEL = "OUTPUTLEVEL"
    THREADS = "THREADS"


class Status(Enum):
    """
    Possible status values for the model.
    - OPTIMAL: Represents that an optimal solution could be found
    - FEASIBLE: Represents that a feasible solution was found. Probably due to hitting the timelimit
    - INFEASIBLE: Represents that no feasible solution was found. Does not necessarily mean that the problem is
     infeasible
    """
    OPTIMAL = "OPTIMAL"
    INFEASIBLE = "INFEASIBLE"
    FEASIBLE = "FEASIBLE"


class Model(metaclass=ABCMeta):
    """
    Class containing a generic optimization model. Use an instance of this class to build a model and then solve it
    :func:`~core.solver.Model.solve`. Use :func:`core.solver.Solver.createModel` to create an instance based on the
    solver to use.
    """

    @abstractmethod
    def getOriginalModel(self):
        """
        Return the underlying optimization model. This can be used to manipulate properties that are not covered
        by this generic interface.
        @return: the original model object, dependent on the solver used to initiate this model
        """
        raise NotImplementedError

    @abstractmethod
    def addVariable(self, lower_bound: float = 0, upper_bound: float = math.inf,
                    var_type: VariableType = VariableType.CONTINUOUS, objective: float = 0, name: str = "") -> Variable:
        """
        Create a new variable and add it to the model.
        @param lower_bound: the lower bound of the variable
        @param upper_bound: the upper bound of the variable
        @param var_type: the type of the variable
        @param objective: the objective of the variable. Dependent on the solver, this may be set later on using
            :func:`~core.solver.Model.setObjective` as well.
        @param name:  the name of the variable.
        @return: the newly created variable
        """
        raise NotImplementedError

    @abstractmethod
    def addConstraint(self, lhs: Union[LinearExpression, Variable, float], sense: ConstraintSense,
                      rhs: Union[LinearExpression, Variable, float], name: str = "") -> Constraint:
        """
        Create a new constraint and add it to the model.
        @param lhs: the left hand side of the constraint, may be an expression, a variable or a number.
        @param sense: the sense of the constraint
        @param rhs: the right hand side of the constraint
        @param name: the name of the constraint, may be an expression, a variable or a number.
        @return: the newly created constraint
        """
        raise NotImplementedError

    @abstractmethod
    def getVariableByName(self, name: str) -> Variable:
        """
        Get a variable by its name.
        @param name: the name of the variable
        @return: the variable
        """
        raise NotImplementedError

    @abstractmethod
    def setStartValue(self, variable: Variable, value: float):
        """
        Set a starting value for the given variable.
        @param variable: the variable to set a starting value for
        @param value: the starting value to set
        """
        raise NotImplementedError

    @abstractmethod
    def setObjective(self, objective: Union[LinearExpression, Variable], sense: OptimizationSense):
        """
        Set the given objective with the given sense for the model.
        @param objective: the objective to set
        @param sense: the sense of the objective
        """
        raise NotImplementedError

    @abstractmethod
    def getObjective(self) -> LinearExpression:
        """
        Get the current objective function of the model
        @return: the objective
        """
        raise NotImplementedError

    @abstractmethod
    def createExpression(self) -> LinearExpression:
        """
        Create a new expression
        @return a new, empty expression
        """
        raise NotImplementedError

    @abstractmethod
    def getSense(self) -> OptimizationSense:
        """
        Get the sense of the objective function, i.e., :data:`~core.solver.OptimizationSense.MINIMIZE` or
        :data:`~core.solver.OptimizationSense.MAXIMIZE`
        """
        raise NotImplementedError

    @abstractmethod
    def write(self, filename: str):
        """
        Write the model to the given file.
        @param filename: the file to write to
        """
        raise NotImplementedError

    @abstractmethod
    def setSense(self, sense: OptimizationSense) -> None:
        """
        Set the optimization sense of this model.
        @param sense: the new sense
        """
        raise NotImplementedError

    @abstractmethod
    def getIntAttribute(self, attribute: IntAttribute) -> int:
        """
        Get the value of an int attribute for the model. For a description of the possible attributes, see
        :class:`~core.solver.IntAttribute`.
        @param attribute: the attribute to query
        @return: the value of the attribute
        """
        raise NotImplementedError

    @abstractmethod
    def getStatus(self) -> Status:
        """
        Get the status of the optimization problem. Note that this needs to be called after solving the problem.
        For possible values, see :class:`~core.solver.Status`.
        @return: the current status
        """
        raise NotImplementedError

    @abstractmethod
    def solve(self) -> None:
        """
        Solve the current model with the given settings
        """
        raise NotImplementedError

    @abstractmethod
    def computeIIS(self, filename: str) -> None:
        """
        Compute an IIS for the model and store it in the given file. The file
        ending depends on the used solver.
        @param filename: the file name to use, without a file ending.
        """
        raise NotImplementedError

    @abstractmethod
    def getValue(self, variable: Variable):
        """
        Get the value of the given variable. Can only be queried if there is a feasible solution, i.e., if
        :func:`~core.model.Solver.getStatus` is :data:`~core.model.Status.OPTIMAL` or
        :data:`~core.model.Status.FEASIBLE`. Otherwise the method will raise an exception.
        @param variable: the variable to query
        @return: the current value of the variable
        """
        raise NotImplementedError

    @abstractmethod
    def getDoubleAttribute(self, attribute: DoubleAttribute) -> float:
        """
        Get the value of the given attribute. For a list of attributes, see :class:`~core.model.DoubleAttribute`.
        @param attribute: the attribute to query.
        @return: the value of the given attribute
        """
        raise NotImplementedError

    def getObjectiveValue(self) -> float:
        """
        Returns the objective value of the current solution. Can only be called on solvers with status OPTIMAL or
        FEASIBLE and a feasible solution is present.
        """
        return self.getDoubleAttribute(DoubleAttribute.OBJ_VAL)

    @abstractmethod
    def setIntParam(self, param: IntParam, value: int) -> None:
        """
        Set the given int parameter. For a list of possible parameters, see :class:`~core.model.IntParam`.
        @param param: the parameter to set
        @param value: the value to set the parameter to.
        """
        raise NotImplementedError

    @abstractmethod
    def setDoubleParam(self, param: DoubleParam, value: float):
        """
        Set the given double parameter. For a list of possible parameters, see :class:`~core.model.DoubleParam`.
        @param param: the parameter to set
        @param value: the value to set the parameter to.
        """
        raise NotImplementedError

    @abstractmethod
    def dispose(self) -> None:
        """
        Dispose of the model and all associated resources. Using the model after this may result in errors.
        """
        raise NotImplementedError

    @abstractmethod
    def update(self) -> None:
        """
        Update the model. Currently only needed when working with Gurobi. For other solvers, this method just does nothing.
        """
        raise NotImplementedError


class SolverType(Enum):
    """
    Enum for used solver types in LinTim
    """
    XPRESS = "XPRESS"
    GUROBI = "GUROBI"
    CPLEX = "CPLEX"
    GLPK = "GLPK"
    SCIP = "SCIP"
    CBC = "CBC"
    HIGHS = "HIGHS"
    MOSEK = "MOSEK"
    COPT = "COPT"


def parseSolverType(solver_name: str) -> SolverType:
    """
    Parse all the possible solver types. Note that there may be solver that are not implemented in the python core
    so initializing a corresponding solver may fail
    @param solver_name: the solver name
    @return: the parsed solver type
    """
    if solver_name.upper() == "XPRESS":
        return SolverType.XPRESS
    elif solver_name.upper() == "GUROBI":
        return SolverType.GUROBI
    elif solver_name.upper() == "CPLEX":
        return SolverType.CPLEX
    elif solver_name.upper() == "GLPK":
        return SolverType.GLPK
    elif solver_name.upper() == "SCIP":
        return SolverType.SCIP
    elif solver_name.upper() == "CBC":
        return SolverType.CBC
    elif solver_name.upper() == "HIGHS":
        return SolverType.HIGHS
    elif solver_name.upper() == "MOSEK":
        return SolverType.MOSEK
    elif solver_name.upper() == "COPT":
        return SolverType.COPT
    else:
        raise LinTimException(f"Solver {solver_name} is not known")


class Solver(metaclass=ABCMeta):
    """
    Abstract class representing a solver. For actual implementations, see :mod:`~core.model.solver.impl`. To get an
    instance of a solver, use :func:`~core.model.Solver.createSolver`. Afterwards, you can create a model using
    :func:`~core.model.Solver.createModel`.
    """
    logger = logging.getLogger(__name__)

    @staticmethod
    def createSolver(solver_type: SolverType) -> "Solver":
        """
        Create a new solver with the given type. If the solver is not implemented or can not be initialized on the
        system, this method may throw!
        @param solver_type: the solver type to initialize
        @return: the solver
        """
        class_string = Solver._get_solver_name_from_type(solver_type)
        solver_class = locate(class_string)
        solver = solver_class()
        try:
            solver.solver_type = solver_type
        except AttributeError:
            # We may not have a solver_type attribute, but we cannot check type here due to circular imports
            pass
        return solver

    @staticmethod
    def _get_solver_name_from_type(solver_type: SolverType):
        if solver_type == SolverType.GUROBI:
            return "core.solver.impl.gurobi_interface.GurobiSolver"
        else:
            return "core.solver.impl.pulp_interface.PulpSolver"

    @abstractmethod
    def createModel(self) -> Model:
        """
        Create a model for the current solver. The type of the returned model is dependent on the used solver.
        @return: the newly created model.
        """
        raise NotImplementedError

    @abstractmethod
    def dispose(self) -> None:
        """
        Dispose the solver and all associated resources. This should free any resources used by the solver, e.g.,
        the solver license. Note that using the solver after that may result in errors. You should dispose all models
        created with this solver first using :func:`~core.solver.Model.dispose`.
        """
        raise NotImplementedError
