from core.util.config import Config
from parameters import ODActivatorParameters, PeriodicEanParameters, SolverParameters, LineFileNameParameters, \
    PTNParameters, PeriodicEanFileParameters, TimeSliceParameters, TimetableObjectiveParameters, ODFileParameters


class TimPassParameters(TimeSliceParameters, PeriodicEanFileParameters, PTNParameters, LineFileNameParameters,
                        ODActivatorParameters, SolverParameters, PeriodicEanParameters, TimetableObjectiveParameters,
                        ODFileParameters):

    def __init__(self, config: Config):
        # OD Activator parameters
        ODActivatorParameters.__init__(self, config, "tim_pass")
        SolverParameters.__init__(self, config, "tim_pass")
        PeriodicEanParameters.__init__(self, config)
        LineFileNameParameters.__init__(self, config)
        PTNParameters.__init__(self, config)
        PeriodicEanFileParameters.__init__(self, config)
        TimeSliceParameters.__init__(self, config)
        TimetableObjectiveParameters.__init__(self, config)
        ODFileParameters.__init__(self, config)
        # General instance data
        self.use_preprocessing = config.getBooleanValue("tim_pass_use_preprocessing")
        self.restrict_transfer_stations = config.getBooleanValue("tim_pass_restrict_transfer_stations")
        self.use_cycle_base_formulation = config.getBooleanValue('tim_pass_use_cycle_base')
        # Passenger Routing
        self.tim_pass_fix_passengers = config.getBooleanValue('tim_pass_add_fixed_passenger_paths')
        # filenames
        self.solver_statistic_filename = config.getStringValue("filename_solver_statistic_file")
