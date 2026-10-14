"""Sparse SciPy/HiGHS implementation of the group's path-based MILP."""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from time import perf_counter
from typing import Dict, List, Mapping, Optional, Sequence, Tuple

import numpy as np
from scipy.optimize import Bounds, LinearConstraint, milp
from scipy.sparse import coo_matrix

from .domain import Line, Trip


OD = Tuple[int, int]


@dataclass(frozen=True)
class PassengerModelData:
    lines: Mapping[int, Line]
    demands: Tuple[Mapping[OD, float], ...]
    trips: Mapping[OD, Tuple[Trip, ...]]

    def __post_init__(self) -> None:
        if not self.demands:
            raise ValueError("At least one demand period is required")
        support = set(self.demands[0])
        for period, demand in enumerate(self.demands):
            if set(demand) != support:
                raise ValueError(f"Demand support changes in period {period}")
            if any(value <= 0 for value in demand.values()):
                raise ValueError("Only positive demand should be passed to the model")
        missing = [pair for pair in support if not self.trips.get(pair)]
        if missing:
            raise ValueError(f"OD pairs without a passenger trip: {missing[:10]}")
        for pair, trips in self.trips.items():
            for trip in trips:
                if (trip.origin, trip.destination) != pair:
                    raise ValueError(f"Trip {trip.trip_id} stored under the wrong OD pair")


@dataclass(frozen=True)
class ModelConfig:
    frequencies: Mapping[int, Tuple[int, ...]]
    capacities: Mapping[int, float]
    operating_costs: Mapping[Tuple[int, int], float]
    budgets: Tuple[float, ...]
    similarity_limit: float
    time_limit: Optional[float] = 60.0
    mip_gap: float = 0.0

    def __post_init__(self) -> None:
        if self.similarity_limit < 0 or self.mip_gap < 0:
            raise ValueError("Similarity limit and MIP gap must be nonnegative")
        for line_id, options in self.frequencies.items():
            if not options or any(frequency <= 0 for frequency in options):
                raise ValueError(f"Line {line_id} needs positive frequency options")
            if line_id not in self.capacities or self.capacities[line_id] <= 0:
                raise ValueError(f"Line {line_id} needs positive capacity")
            for frequency in options:
                if (line_id, frequency) not in self.operating_costs:
                    raise ValueError(f"Missing operating cost for {(line_id, frequency)}")


@dataclass(frozen=True)
class ModelSize:
    binary_variables: int
    continuous_flow_variables: int
    continuous_delta_variables: int
    constraints: int
    nonzeros: int
    activation_constraints: int
    capacity_constraints: int

    @property
    def variables(self) -> int:
        return (
            self.binary_variables
            + self.continuous_flow_variables
            + self.continuous_delta_variables
        )


@dataclass(frozen=True)
class SolveResult:
    status: str
    objective: float
    runtime_seconds: float
    selected_frequencies: Tuple[Mapping[int, int], ...]
    passenger_flows: Tuple[Mapping[Tuple[OD, str], float], ...]
    mip_gap: Optional[float]
    best_bound: Optional[float]
    message: str
    model_size: ModelSize
    metadata: Mapping[str, object] = field(default_factory=dict)


class _Builder:
    def __init__(self) -> None:
        self.cost: List[float] = []
        self.lower: List[float] = []
        self.upper: List[float] = []
        self.integrality: List[int] = []
        self.rows: List[Dict[int, float]] = []
        self.row_lower: List[float] = []
        self.row_upper: List[float] = []

    def variable(
        self,
        cost: float = 0.0,
        lower: float = 0.0,
        upper: float = np.inf,
        binary: bool = False,
    ) -> int:
        index = len(self.cost)
        self.cost.append(float(cost))
        self.lower.append(float(lower))
        self.upper.append(1.0 if binary else float(upper))
        self.integrality.append(1 if binary else 0)
        return index

    def constraint(
        self,
        coefficients: Mapping[int, float],
        lower: float = -np.inf,
        upper: float = np.inf,
    ) -> None:
        row = {index: float(value) for index, value in coefficients.items() if value}
        self.rows.append(row)
        self.row_lower.append(float(lower))
        self.row_upper.append(float(upper))

    def solve(self, config: ModelConfig):
        row_indices: List[int] = []
        column_indices: List[int] = []
        values: List[float] = []
        for row, coefficients in enumerate(self.rows):
            for column, value in coefficients.items():
                row_indices.append(row)
                column_indices.append(column)
                values.append(value)
        matrix = coo_matrix(
            (values, (row_indices, column_indices)),
            shape=(len(self.rows), len(self.cost)),
        ).tocsr()
        options = {"disp": False, "mip_rel_gap": float(config.mip_gap)}
        if config.time_limit is not None:
            options["time_limit"] = float(config.time_limit)
        started = perf_counter()
        result = milp(
            c=np.asarray(self.cost),
            integrality=np.asarray(self.integrality),
            bounds=Bounds(np.asarray(self.lower), np.asarray(self.upper)),
            constraints=LinearConstraint(
                matrix, np.asarray(self.row_lower), np.asarray(self.row_upper)
            ),
            options=options,
        )
        return result, perf_counter() - started


def _validate_config(data: PassengerModelData, config: ModelConfig) -> None:
    line_ids = set(data.lines)
    if set(config.frequencies) != line_ids:
        raise ValueError("Frequency options must be supplied for every and only every line")
    if len(config.budgets) != len(data.demands):
        raise ValueError("One budget is required per time period")
    for pair, trips in data.trips.items():
        for trip in trips:
            for line_id, frequency in trip.services:
                if line_id not in line_ids or frequency not in config.frequencies[line_id]:
                    raise ValueError(
                        f"Trip {trip.trip_id} uses unavailable service {(line_id, frequency)}"
                    )


def estimate_model_size(data: PassengerModelData, config: ModelConfig) -> ModelSize:
    _validate_config(data, config)
    periods = len(data.demands)
    line_ids = tuple(sorted(data.lines))
    trip_count = sum(len(data.trips[pair]) for pair in data.demands[0])
    service_requirements = sum(
        len(trip.services)
        for pair in data.demands[0]
        for trip in data.trips[pair]
    )
    capacity_keys = {
        service_arc
        for pair in data.demands[0]
        for trip in data.trips[pair]
        for service_arc in trip.service_arcs
    }
    z_count = periods * sum(len(config.frequencies[line_id]) for line_id in line_ids)
    x_count = periods * trip_count
    delta_count = max(0, periods - 1) * len(line_ids)
    selection = periods * len(line_ids)
    demand = periods * len(data.demands[0])
    activation = periods * service_requirements
    capacity = periods * len(capacity_keys)
    budget = periods
    similarity = max(0, periods - 1) * (1 + 2 * len(line_ids))
    constraints = selection + demand + activation + capacity + budget + similarity

    selection_nz = z_count
    demand_nz = x_count
    activation_nz = activation * 2
    capacity_flow_nz = periods * sum(
        len(
            {
                (pair, trip.trip_id)
                for pair in data.demands[0]
                for trip in data.trips[pair]
                if key in trip.service_arcs
            }
        )
        for key in capacity_keys
    )
    capacity_nz = capacity_flow_nz + capacity
    budget_nz = z_count
    similarity_nz = max(0, periods - 1) * (
        len(line_ids) + 2 * sum(2 * len(config.frequencies[line]) + 1 for line in line_ids)
    )
    return ModelSize(
        binary_variables=z_count,
        continuous_flow_variables=x_count,
        continuous_delta_variables=delta_count,
        constraints=constraints,
        nonzeros=(
            selection_nz
            + demand_nz
            + activation_nz
            + capacity_nz
            + budget_nz
            + similarity_nz
        ),
        activation_constraints=activation,
        capacity_constraints=capacity,
    )


_STATUS = {
    0: "optimal",
    1: "limit_reached",
    2: "infeasible",
    3: "unbounded",
    4: "solver_error",
}


def solve(data: PassengerModelData, config: ModelConfig) -> SolveResult:
    """Build and solve the path-based multi-period passenger MILP."""

    size = estimate_model_size(data, config)
    builder = _Builder()
    periods = len(data.demands)
    line_ids = tuple(sorted(data.lines))

    z: Dict[Tuple[int, int, int], int] = {}
    for period in range(periods):
        for line_id in line_ids:
            for frequency in config.frequencies[line_id]:
                z[(period, line_id, frequency)] = builder.variable(binary=True)

    x: Dict[Tuple[int, OD, int], int] = {}
    for period, demand in enumerate(data.demands):
        for pair in sorted(demand):
            for trip_index, trip in enumerate(data.trips[pair]):
                x[(period, pair, trip_index)] = builder.variable(
                    cost=trip.generalized_time, upper=demand[pair]
                )

    delta: Dict[Tuple[int, int], int] = {}
    max_frequency = {
        line_id: max(config.frequencies[line_id]) for line_id in line_ids
    }
    for period in range(periods - 1):
        for line_id in line_ids:
            delta[(period, line_id)] = builder.variable(
                upper=2.0 * max_frequency[line_id]
            )

    # At most one positive frequency for every line and period.
    for period in range(periods):
        for line_id in line_ids:
            builder.constraint(
                {
                    z[(period, line_id, frequency)]: 1.0
                    for frequency in config.frequencies[line_id]
                },
                upper=1.0,
            )

    # Demand conservation and trip/service activation.
    for period, demand in enumerate(data.demands):
        for pair in sorted(demand):
            trips = data.trips[pair]
            builder.constraint(
                {
                    x[(period, pair, trip_index)]: 1.0
                    for trip_index in range(len(trips))
                },
                lower=demand[pair],
                upper=demand[pair],
            )
            for trip_index, trip in enumerate(trips):
                for line_id, frequency in trip.services:
                    builder.constraint(
                        {
                            x[(period, pair, trip_index)]: 1.0,
                            z[(period, line_id, frequency)]: -demand[pair],
                        },
                        upper=0.0,
                    )

    # Segment capacity, separately by service, frequency, and direction.
    capacity_terms: Dict[
        Tuple[int, int, int, int, int, int], Dict[int, float]
    ] = {}
    for period in range(periods):
        for pair in sorted(data.demands[period]):
            for trip_index, trip in enumerate(data.trips[pair]):
                variable = x[(period, pair, trip_index)]
                for line_id, frequency, edge_id, left, right in trip.service_arcs:
                    key = (period, line_id, frequency, edge_id, left, right)
                    capacity_terms.setdefault(key, {})[variable] = 1.0
    for (period, line_id, frequency, _edge_id, _left, _right), terms in sorted(
        capacity_terms.items()
    ):
        coefficients = dict(terms)
        coefficients[z[(period, line_id, frequency)]] = -(
            config.capacities[line_id] * frequency
        )
        builder.constraint(coefficients, upper=0.0)

    # Period budgets.
    for period in range(periods):
        builder.constraint(
            {
                z[(period, line_id, frequency)]: config.operating_costs[
                    (line_id, frequency)
                ]
                for line_id in line_ids
                for frequency in config.frequencies[line_id]
            },
            upper=config.budgets[period],
        )

    # Adjacent-period absolute frequency differences.
    for period in range(periods - 1):
        builder.constraint(
            {delta[(period, line_id)]: 1.0 for line_id in line_ids},
            upper=config.similarity_limit,
        )
        for line_id in line_ids:
            first = {
                z[(period + 1, line_id, frequency)]: float(frequency)
                for frequency in config.frequencies[line_id]
            }
            for frequency in config.frequencies[line_id]:
                first[z[(period, line_id, frequency)]] = -float(frequency)
            first[delta[(period, line_id)]] = -1.0
            builder.constraint(first, upper=0.0)

            second = {
                z[(period, line_id, frequency)]: float(frequency)
                for frequency in config.frequencies[line_id]
            }
            for frequency in config.frequencies[line_id]:
                second[z[(period + 1, line_id, frequency)]] = -float(frequency)
            second[delta[(period, line_id)]] = -1.0
            builder.constraint(second, upper=0.0)

    solver_result, runtime = builder.solve(config)
    status = _STATUS.get(int(solver_result.status), "unknown")
    selected: List[Mapping[int, int]] = []
    flows: List[Mapping[Tuple[OD, str], float]] = []
    if solver_result.x is not None:
        for period in range(periods):
            period_frequencies: Dict[int, int] = {}
            for line_id in line_ids:
                frequency = sum(
                    option * int(round(solver_result.x[z[(period, line_id, option)]]))
                    for option in config.frequencies[line_id]
                )
                period_frequencies[line_id] = frequency
            selected.append(period_frequencies)
            period_flows: Dict[Tuple[OD, str], float] = {}
            for pair in sorted(data.demands[period]):
                for trip_index, trip in enumerate(data.trips[pair]):
                    value = float(solver_result.x[x[(period, pair, trip_index)]])
                    if value > 1e-8:
                        period_flows[(pair, trip.trip_id)] = value
            flows.append(period_flows)

    gap = getattr(solver_result, "mip_gap", None)
    bound = getattr(solver_result, "mip_dual_bound", None)
    return SolveResult(
        status=status,
        objective=(float(solver_result.fun) if solver_result.fun is not None else math.inf),
        runtime_seconds=runtime,
        selected_frequencies=tuple(selected),
        passenger_flows=tuple(flows),
        mip_gap=float(gap) if gap is not None else None,
        best_bound=float(bound) if bound is not None else None,
        message=str(solver_result.message),
        model_size=size,
        metadata={
            "finite_candidate_pool": True,
            "capacity_directional": True,
            "similarity_scope": "adjacent periods",
        },
    )

