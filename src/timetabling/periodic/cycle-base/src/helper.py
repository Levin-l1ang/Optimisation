import logging

from core.solver.solver_parameters import SolverParameters
from core.util.config import Config

logger_ = logging.getLogger(__name__)


class Parameters(SolverParameters):
    def __init__(self, config: Config):
        super().__init__(config, "tim_")
        logger_.debug("Run: Parameters.__init__(...)")
        self.activities_file = config.getStringValue("default_activities_periodic_file")
        self.events_file = config.getStringValue("default_events_periodic_file")
        self.timetable_file = config.getStringValue("default_timetable_periodic_file")
        self.period_length = config.getIntegerValue("period_length")

        self.use_old_solution = config.getBooleanValue("tim_use_old_solution")

        self.solution_limit = config.getIntegerValue("tim_pesp_cb_solution_limit")
        self.best_bound_stop = config.getDoubleValue("tim_pesp_cb_best_bound_stop")
        self.mip_focus = config.getIntegerValue("tim_pesp_cb_mip_focus")
        self.passenger_cut = config.getIntegerValue("tim_pesp_cb_passenger_cut")


