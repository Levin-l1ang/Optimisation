import logging
from enum import Enum, auto

from core.exceptions.exceptions import LinTimException
from core.solver.generic_solver_interface import Model, Solver, IntParam, DoubleParam
from core.util.config import Config


class TimetableObjectiveParameters:
    def __init__(self, config: Config):
        self.factor_travel_time = config.getDoubleValue('int_factor_travel_time')
        self.factor_drive_time = config.getDoubleValue('int_factor_drive_time')
        self.factor_transfer_time = config.getDoubleValue('int_factor_transfer_time')
        self.factor_wait_time = config.getDoubleValue('int_factor_wait_time')
        self.transfer_penalty = config.getDoubleValue('ean_change_penalty')


class VSObjectiveParameters:
    def __init__(self, config: Config):
        self.factor_drive_time_unweighted = config.getDoubleValue('vs_eval_cost_factor_full_trips_duration')
        self.factor_line_length = config.getDoubleValue('vs_eval_cost_factor_full_trips_length')
        self.factor_turn_around_time = config.getDoubleValue('vs_eval_cost_factor_empty_trips_duration')
        self.factor_turn_around_distance = config.getDoubleValue('vs_eval_cost_factor_empty_trips_length')
        self.factor_vehicles = config.getDoubleValue('vs_vehicle_costs')


class PeriodicEanParameters:
    def __init__(self, config: Config):
        self.min_wait_time = config.getIntegerValue("ean_default_minimal_waiting_time")
        self.max_wait_time = config.getIntegerValue("ean_default_maximal_waiting_time")
        self.min_trans_time = config.getIntegerValue("ean_default_minimal_change_time")
        self.max_trans_time = config.getIntegerValue("ean_default_maximal_change_time")
        self.period_length = config.getIntegerValue('period_length')


class TimeSliceParameters:
    def __init__(self, config: Config):
        self.global_n_time_slices = config.getIntegerValue('int_time_slices')
        self.factor_penalty_time_slice = config.getDoubleValue('int_factor_penalty_time_slice')


class PeriodicEanFileParameters:
    def __init__(self, config: Config):
        self.periodic_activity_file_name = config.getStringValue('default_activities_periodic_file')
        self.periodic_event_file_name = config.getStringValue('default_events_periodic_file')
        self.periodic_timetable_filename = config.getStringValue('default_timetable_periodic_file')
        self.periodic_event_header = config.getStringValue('events_header_periodic')
        self.periodic_activity_header = config.getStringValue('activities_header_periodic')
        self.periodic_timetable_header = config.getStringValue('timetable_header_periodic')


class ODActivatorParameters:
    def __init__(self, config: Config, model_prefix: str):
        self.number_of_routed_od_pairs = config.getIntegerValue(model_prefix + '_number_of_routed_od_pairs')
        od_activator_method = config.getStringValue(model_prefix + '_choose_routed_od_pairs')
        self.od_activator = parse_od_activator(od_activator_method)


class PTNParameters:
    def __init__(self, config: Config):
        self.directed = not config.getBooleanValue('ptn_is_undirected')
        self.stop_file_name = config.getStringValue('default_stops_file')
        self.edge_file_name = config.getStringValue('default_edges_file')
        self.conversion_factor_length = config.getDoubleValue('gen_conversion_length')


class LineFileNameParameters:
    def __init__(self, config: Config):
        self.line_pool_file_name = config.getStringValue('default_pool_file')
        self.line_cost_file_name = config.getStringValue('default_pool_cost_file')
        self.line_concept_file_name = config.getStringValue('default_lines_file')
        self.line_concept_header = config.getStringValue('lines_header')


class VSParameters:
    def __init__(self, config: Config):
        self.ean_earliest_time = config.getIntegerValue('DM_earliest_time')
        self.p_max = config.getIntegerValue('int_number_of_periods')
        self.vs_depot_index = config.getIntegerValue('vs_depot_index')
        self.vs_turn_over_time = config.getIntegerValue('vs_turn_over_time')
        self.factor_ptn_edge_length = config.getDoubleValue("gen_conversion_length")
        self.vehicle_file_name = config.getStringValue('default_vehicle_schedule_file')
        self.activities_expanded_file_name = config.getStringValue('default_activities_expanded_file')
        self.event_expanded_file_name = config.getStringValue('default_events_expanded_file')
        self.timetable_expanded_file_name = config.getStringValue('default_timetable_expanded_file')
        self.trip_file_name = config.getStringValue('default_trips_file')
        self.end_events_file_name = config.getStringValue('default_expanded_end_events_of_trips_file')
        self.vehicle_schedule_header = config.getStringValue('vehicle_schedule_header')
        self.trip_header = config.getStringValue('trip_header')
        self.aperiodic_event_header = config.getStringValue('events_header')
        self.aperiodic_timetable_header = config.getStringValue('timetable_header')
        self.aperiodic_activity_header = config.getStringValue('activities_header')


class ODFileParameters:
    def __init__(self, config: Config):
        self.od_file_name = config.getStringValue('default_od_file')


class ODActivator(Enum):
    POTENTIAL = auto()
    LARGEST_WEIGHT = auto()
    SMALLEST_WEIGHT = auto()
    LARGEST_WEIGHT_WITH_TRANSFER = auto()
    LARGEST_DISTANCE = auto()
    DIFF = auto()
    RANDOM = auto()


def parse_od_activator(name: str) -> ODActivator:
    if name.upper() == "POTENTIAL":
        return ODActivator.POTENTIAL
    if name.upper() == "LARGEST_WEIGHT":
        return ODActivator.LARGEST_WEIGHT
    if name.upper() == "SMALLEST_WEIGHT":
        return ODActivator.SMALLEST_WEIGHT
    if name.upper() == "LARGEST_WEIGHT_WITH_TRANSFER":
        return ODActivator.LARGEST_WEIGHT_WITH_TRANSFER
    if name.upper() == "LARGEST_DISTANCE":
        return ODActivator.LARGEST_DISTANCE
    if name.upper() == "DIFF":
        return ODActivator.DIFF
    if name.upper() == "RANDOM":
        return ODActivator.RANDOM
    else:
        raise LinTimException(f"Unknown OD Activator {name}")


class SolverParameters:

    def __init__(self, config: Config, model_prefix: str):
        self.time_limit = config.getIntegerValue(model_prefix + '_timelimit')
        self.mip_gap = config.getDoubleValue(model_prefix + '_mip_gap')
        self.write_lp_output = config.getBooleanValue(model_prefix + '_write_lp_file')
        self.n_threads = config.getIntegerValue('int_threads')
        self.solver_type = config.getSolverType('int_solver')
        self.show_solver_output = config.getLogLevel("console_log_level") == logging.DEBUG
        self.objectives_file_name = config.getStringValue('filename_objectives_file')
        self.solver_statistic_file_name = config.getStringValue('filename_solver_statistic_file')

    def initialize_generic_model(self) -> Model:
        solver = Solver.createSolver(self.solver_type)
        model = solver.createModel()
        if self.time_limit != -1:
            model.setIntParam(IntParam.TIMELIMIT, self.time_limit)
        if self.mip_gap >= 0:
            model.setDoubleParam(DoubleParam.MIP_GAP, self.mip_gap)
        if self.n_threads > 0:
            model.setIntParam(IntParam.THREADS, self.n_threads)
        if not self.show_solver_output:
            model.setIntParam(IntParam.OUTPUT_LEVEL, logging.CRITICAL)
        else:
            model.setIntParam(IntParam.OUTPUT_LEVEL, logging.DEBUG)
        return model