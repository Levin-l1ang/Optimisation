from core.util.config import Config
from parameters import PTNParameters, TimetableObjectiveParameters, PeriodicEanParameters, TimeSliceParameters, \
    SolverParameters, LineFileNameParameters, PeriodicEanFileParameters, ODActivatorParameters, ODFileParameters, \
    VSObjectiveParameters, VSParameters


class LinTimPassVehParameters(PeriodicEanFileParameters, LineFileNameParameters, SolverParameters, TimeSliceParameters,
                              PTNParameters, TimetableObjectiveParameters, PeriodicEanParameters, ODActivatorParameters,
                              ODFileParameters, VSObjectiveParameters, VSParameters):
    def __init__(self, config: Config):
        PTNParameters.__init__(self, config)
        TimetableObjectiveParameters.__init__(self, config)
        PeriodicEanParameters.__init__(self, config)
        TimeSliceParameters.__init__(self, config)
        SolverParameters.__init__(self, config, "lin_tim_pass_veh")
        ODActivatorParameters.__init__(self, config, "lin_tim_pass_veh")
        LineFileNameParameters.__init__(self, config)
        PeriodicEanFileParameters.__init__(self, config)
        ODFileParameters.__init__(self, config)
        VSObjectiveParameters.__init__(self, config)
        VSParameters.__init__(self, config)
        self.vs_allow_empty_trips = config.getBooleanValue('lin_tim_pass_veh_allow_empty_trips')
        # General instance data
        self.vs_speed = config.getIntegerValue('gen_vehicle_speed')
        self.use_preprocessing = config.getBooleanValue('lin_tim_pass_veh_use_preprocessing')
        self.add_fix_passenger_paths = config.getBooleanValue('lin_tim_pass_veh_add_fixed_passenger_paths')
        # Line planning
        self.check_lower_frequencies = config.getBooleanValue('int_check_lower_frequencies')
        self.check_upper_frequencies = config.getBooleanValue('int_check_upper_frequencies')
        self.use_system_frequency = config.getBooleanValue('int_restrict_to_system_frequency')
        self.system_frequency = config.getIntegerValue('int_system_frequency')
        # Passenger routing
        self.number_of_routed_od_pairs = config.getIntegerValue('lin_tim_pass_veh_number_of_routed_od_pairs')
        # File names
        self.load_file_name = config.getStringValue('default_loads_file')
        self.solver_statistic_filename = config.getStringValue("filename_solver_statistic_file")
