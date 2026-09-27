import logging

from core.solver.generic_solver_interface import SolverType, Model, IntParam, DoubleParam
from core.util.config import Config

_logger = logging.getLogger(__name__)

class SolverParameters:

    def __init__(self, config: Config, prefix: str):
        self._thread_limit = config.getIntegerValue(f"{prefix}threads")
        self._timelimit = config.getIntegerValue(f"{prefix}timelimit")
        self._mip_gap = config.getDoubleValue(f"{prefix}mip_gap")
        self._write_lp_file = config.getBooleanValue(f"{prefix}write_lp_file")
        self._output_solver_messages = config.getLogLevel("console_log_level") == logging.DEBUG
        self._solver_type = config.getSolverType(f"{prefix}solver")

    def getTimelimit(self) -> int:
        return self._timelimit

    def getMipGap(self) -> float:
        return self._mip_gap

    def writeLpFile(self) -> bool:
        return self._write_lp_file

    def outputSolverMessages(self) -> bool:
        return self._output_solver_messages

    def getSolverType(self) -> SolverType:
        return self._solver_type

    def getThreadLimit(self) -> int:
        return self._thread_limit

    def setSolverParameters(self, model: Model) -> None:
        if self._timelimit > 0:
            _logger.debug(f"Set solver timelimit to {self._timelimit}")
            model.setIntParam(IntParam.TIMELIMIT, self._timelimit)
        if self._mip_gap > 0:
            _logger.debug(f"Set solver mip gap to {self._mip_gap}")
            model.setDoubleParam(DoubleParam.MIP_GAP, self._mip_gap)
        if self._thread_limit > 0:
            _logger.debug(f"Set solver thread limit to {self._thread_limit}")
            model.setIntParam(IntParam.THREADS, self._thread_limit)
        if self._output_solver_messages:
            model.setIntParam(IntParam.OUTPUT_LEVEL, logging.DEBUG)
        else:
            model.setIntParam(IntParam.OUTPUT_LEVEL, 0)