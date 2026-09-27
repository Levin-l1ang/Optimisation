from core.util.config import Config
from parameters import PTNParameters, PeriodicEanParameters, VSParameters, SolverParameters, LineFileNameParameters, \
    PeriodicEanFileParameters, TimetableObjectiveParameters


class LinVehToTimParameters(PTNParameters, PeriodicEanParameters, VSParameters, SolverParameters,
                            LineFileNameParameters, PeriodicEanFileParameters, TimetableObjectiveParameters):

    def __init__(self, config: Config):
        PTNParameters.__init__(self, config)
        PeriodicEanParameters.__init__(self, config)
        VSParameters.__init__(self, config)
        LineFileNameParameters.__init__(self, config)
        SolverParameters.__init__(self, config, "lin_veh_to_tim")
        PeriodicEanFileParameters.__init__(self, config)
        TimetableObjectiveParameters.__init__(self, config)
        self.vs_speed = config.getIntegerValue('gen_vehicle_speed')
        self.set_starting_timetable = config.getBooleanValue('int_set_starting_timetable')
        self.em_earliest_time = config.getIntegerValue('DM_earliest_time_EM')
        self.em_latest_time = config.getIntegerValue('DM_latest_time_EM')
