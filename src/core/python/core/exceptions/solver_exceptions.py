from core.exceptions.exceptions import LinTimException
from core.solver.generic_solver_interface import SolverType


class SolverAttributeNotImplementedException(LinTimException):

    def __init__(self, type: SolverType, attribute_name: str):
        """Exception to throw if an attribute is not implemented for a solver type.

        :param type: the solver type
        :type type: SolverType
        :param attribute_name: name of the attribute
        :type attribute_name: str
        """
        super().__init__(f"Error S5: Attribute {attribute_name} is not implemented for {type.name} yet")


class SolverParamNotImplementedException(LinTimException):

    def __init__(self, type: SolverType, param_name: str):
        """Exception to throw if a parameter is not implemented for a solver type.

        :param type: the solver type
        :type type: SolverType
        :param param_name: name of the parameter
        :type param_name: str
        """
        super().__init__(f"Error S6: Parameter {param_name} is not implemented for {type.name} yet")


class SolverNotImplementedException(LinTimException):

    def __init__(self, sol_type: SolverType):
        """Exception to thow if a solver type is not implemented in the python core.

        :param sol_type: the solver type
        :type sol_type: SolverType
        """
        super().__init__(f"Error S4: Solver {sol_type.name} is not implemented in python core")


class SolverFoundNoFeasibleSolutionException(LinTimException):

    def __init__(self):
        """Exception to throw if no feasible solution was found by the solver.
        """
        super().__init__(f"Error S10: Solver found no feasible solution. Check solver output for further information")