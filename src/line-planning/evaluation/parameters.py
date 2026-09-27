import logging

from core.exceptions.config_exceptions import ConfigInvalidValueException
from core.solver.generic_solver_interface import VariableType
from core.solver.solver_parameters import SolverParameters
from core.util.change_and_go_method import compute_wait_time
from core.util.config import Config
from helper import set_drive_weight_function

logger = logging.getLogger(__name__)


class Parameters(SolverParameters):
    def __init__(self, config: Config):
        super().__init__(config, "lc_")
        self.ean_model_weight_drive = config.getStringValue("ean_model_weight_drive")
        self.ean_model_weight_wait = config.getStringValue("ean_model_weight_wait")
        self.min_wait_time = config.getIntegerValue("ean_default_minimal_waiting_time")
        self.max_wait_time = config.getIntegerValue("ean_default_maximal_waiting_time")
        self.drive_weight_function = set_drive_weight_function(self.ean_model_weight_drive)
        self.wait_time = compute_wait_time(self.ean_model_weight_wait, self.min_wait_time, self.max_wait_time)
        self.include_departure_adaption_time = config.getBooleanValue("lc_c&g_include_departure_adaption_time")
        self.include_arrival_adaption_time = config.getBooleanValue("lc_c&g_include_arrival_adaption_time")
        self.ean_model_weight_change = config.getStringValue("ean_model_weight_change")

        if self.include_departure_adaption_time \
                and self.ean_model_weight_change.upper() == "FORMULA_2":
            raise ConfigInvalidValueException(
                ['lc_c&g_include_departure_adaption_time',
                 'ean_model_weight_change'])
        if self.include_arrival_adaption_time \
                and self.ean_model_weight_change.upper() == "FORMULA_2":
            raise ConfigInvalidValueException(
                ['lc_c&g_include_arrival_adaption_time',
                 'ean_model_weight_change'])
        if self.include_arrival_adaption_time != self.include_departure_adaption_time \
                and self.ean_model_weight_change.upper() == "FORMULA_1":
            raise ConfigInvalidValueException(
                ['lc_c&g_include_arrival_adaption_time',
                 'lc_c&g_include_departure_adaption_time',
                 'ean_model_weight_change'])

        self.ean_change_penalty = config.getIntegerValue("ean_change_penalty")
        self.min_transfer_time = config.getIntegerValue("ean_default_minimal_change_time")
        self.period_length = config.getIntegerValue("period_length")
        self.capacity = config.getIntegerValue("gen_passengers_per_vehicle")
        self.gen_conversion_length = config.getDoubleValue("gen_conversion_length")
        self.gen_conversion_coordinates = config.getDoubleValue("gen_conversion_coordinates")
        self.lc_eval_extended = config.getBooleanValue("lc_eval_extended")
        self.flow_type = VariableType.INTEGER if config.getBooleanValue(
                'lc_traveling_time_mip_integer_flows') \
            else VariableType.CONTINUOUS
