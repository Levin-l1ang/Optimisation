import logging

from core.util.config import Config
from parameters import TimetableObjectiveParameters, PeriodicEanParameters, PTNParameters, VSParameters, \
    VSObjectiveParameters, SolverParameters, LineFileNameParameters, PeriodicEanFileParameters

logger = logging.getLogger(__name__)


class TimVehParameters(PeriodicEanFileParameters, LineFileNameParameters, VSObjectiveParameters, VSParameters,
                       PTNParameters, TimetableObjectiveParameters, SolverParameters, PeriodicEanParameters):
    def __init__(self, config: Config):
        TimetableObjectiveParameters.__init__(self, config)
        SolverParameters.__init__(self, config, "tim_veh")
        PTNParameters.__init__(self, config)
        PeriodicEanParameters.__init__(self, config)
        VSParameters.__init__(self, config)
        VSObjectiveParameters.__init__(self, config)
        LineFileNameParameters.__init__(self, config)
        PeriodicEanFileParameters.__init__(self, config)
        # Vehicle scheduling
        self.vs_allow_empty_trips = config.getBooleanValue('tim_veh_allow_empty_trips')
        self.use_lower_bound = config.getBooleanValue('tim_veh_use_lower_bound')
        self.set_starting_timetable = config.getBooleanValue('int_set_starting_timetable')
