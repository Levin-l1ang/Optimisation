from core.exceptions.config_exceptions import ConfigInvalidValueException
from core.solver.generic_solver_interface import VariableType
from core.solver.solver_parameters import SolverParameters
from core.util.config import Config


class Parameters(SolverParameters):

    def __init__(self, config: Config):
        super().__init__(config, "lc_")
        minimize = config.getStringValue(
            'lc_traveling_time_mip_minimize').upper()
        self.minimize_time = minimize == "TIME"
        if self.minimize_time:
            self.budget = config.getDoubleValue('lc_budget')
        else:
            assert minimize == "COST"
            self.time_budget = config.getIntegerValue(
                'lc_traveling_time_mip_time_budget')
        self.use_loads = config.getBooleanValue(
            'lc_traveling_time_mip_use_loads')
        self.flow_type = VariableType.INTEGER if config.getBooleanValue(
            "lc_traveling_time_mip_integer_flows") \
            else VariableType.CONTINUOUS
        self.frequency_type = VariableType.INTEGER if config.getBooleanValue(
            "lc_traveling_time_mip_integer_frequencies") \
            else VariableType.CONTINUOUS

        self.driving_time_model = config.getStringValue(
            'ean_model_weight_drive')

        self.waiting_time_model = config.getStringValue(
            'ean_model_weight_wait')
        self.min_waiting_time = config.getIntegerValue(
            'ean_default_minimal_waiting_time')
        self.max_waiting_time = config.getIntegerValue(
            'ean_default_maximal_waiting_time')

        self.include_departure_adaption_time = config.getBooleanValue(
            'lc_c&g_include_departure_adaption_time')
        self.include_arrival_adaption_time = config.getBooleanValue(
            'lc_c&g_include_arrival_adaption_time')
        self.change_penalty = config.getIntegerValue('ean_change_penalty')
        self.min_transfer_time = config.getIntegerValue(
            'ean_default_minimal_change_time')

        self.vehicle_capacity = config.getIntegerValue(
            'gen_passengers_per_vehicle')
        self.period_length = config.getIntegerValue('period_length')
        self.vehicle_cost = config.getDoubleValue('lpool_costs_vehicles')
        self.cost_length = config.getDoubleValue('lpool_costs_length')
        self.edge_cost = config.getDoubleValue('lpool_costs_edges')
        self.turnover_time = config.getIntegerValue('vs_turn_over_time')

