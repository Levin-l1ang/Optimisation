"""Sparse SciPy/HiGHS implementation of the multi-period passenger MILP (handoff §5).

Variables per period t: z[t,l,f] (binary frequency choice), x[t,od,p] (flow on
frequency-specific trips), u[t,od] (unserved demand), and delta[t,l] between
adjacent periods (frequency change).

    min  sum_t sum_od sum_p c_p x  +  M * sum_t sum_od u
    s.t. sum_f z[t,l,f] <= 1
         sum_p x[t,od,p] + u[t,od] = D[t,od]                 (u absorbs OD without trips)
         x[t,od,p] <= min(D, C_l f) z[t,l,f]   for every service (l,f) of trip p
         sum_{p uses (l,f,arc)} x <= C_l f z[t,l,f]          (per directed connection)
         sum_{l,f} b[l,f] z[t,l,f] <= B_t
         sum_l delta[t,l] <= K,  delta[t,l] >= |F[t+1,l] - F[t,l]|  (adjacent periods)

``M`` (unserved_penalty) is a required configuration value: there is no default.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from time import perf_counter
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

import numpy as np
from scipy.optimize import Bounds, LinearConstraint, milp
from scipy.sparse import coo_matrix

from .domain import CandidateLine, Trip

OD = Tuple[str, str]


@dataclass(frozen=True)
class PassengerModelData:
    lines: Mapping[int, CandidateLine]
    demands: Tuple[Mapping[OD, float], ...]
    trips: Mapping[OD, Tuple[Trip, ...]]  # an OD with no entry / no trips is fully unserved

    def __post_init__(self) -> None:
        if not self.demands:
            raise ValueError("At least one demand period is required")
        support = set(self.demands[0])
        for period, demand in enumerate(self.demands):
            if set(demand) != support:
                raise ValueError(f"Demand support changes in period {period}")
            if any(v <= 0 for v in demand.values()):
                raise ValueError("Demands must be positive (keep every OD in every period)")
        for pair, trips in self.trips.items():
            for trip in trips:
                if (trip.origin, trip.destination) != pair:
                    raise ValueError(f"Trip {trip.trip_id} stored under the wrong OD pair")

    def trips_of(self, pair: OD) -> Tuple[Trip, ...]:
        return tuple(self.trips.get(pair, ()))

    def max_trip_cost(self) -> float:
        return max((t.generalized_time for ts in self.trips.values() for t in ts), default=0.0)


@dataclass(frozen=True)
class ModelConfig:
    frequencies: Mapping[int, Tuple[int, ...]]
    capacities: Mapping[int, float]  # per train and direction
    operating_costs: Mapping[Tuple[int, int], float]  # b_{l,f} = fixed + c^V_l f
    budgets: Tuple[float, ...]
    similarity_limit: float  # K
    unserved_penalty: float  # M, required
    time_limit: Optional[float] = 60.0
    mip_gap: float = 0.0
    # The primary experiment requires max_p c_p < M.  Sensitivity runs with a smaller
    # M must opt in explicitly; the violation is then recorded in the result.
    allow_trip_cost_above_penalty: bool = False
    # Back-end: "highs" (SciPy, default) or "gurobi".  ``initial_plan[t][line] = frequency``
    # (0 = closed) is a partial MIP start; only the Gurobi back-end uses it.
    solver: str = "highs"
    threads: Optional[int] = None
    initial_plan: Optional[Tuple[Mapping[int, int], ...]] = None
    # Extra Gurobi parameters, e.g. {"MIPFocus": 1}; empty = Gurobi defaults.
    gurobi_params: Mapping[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.solver not in ("highs", "gurobi"):
            raise ValueError("solver must be 'highs' or 'gurobi'")
        if self.unserved_penalty <= 0 or not math.isfinite(self.unserved_penalty):
            raise ValueError("unserved_penalty M must be a positive finite number")
        if self.similarity_limit < 0 or self.mip_gap < 0:
            raise ValueError("Similarity limit and MIP gap must be nonnegative")
        for line_id, options in self.frequencies.items():
            if not options or any(f <= 0 for f in options):
                raise ValueError(f"Line {line_id} needs positive frequency options")
            if self.capacities.get(line_id, 0) <= 0:
                raise ValueError(f"Line {line_id} needs positive capacity")
            for f in options:
                if (line_id, f) not in self.operating_costs:
                    raise ValueError(f"Missing operating cost for {(line_id, f)}")


@dataclass(frozen=True)
class ModelSize:
    binary_variables: int
    continuous_flow_variables: int
    continuous_unserved_variables: int
    continuous_delta_variables: int
    constraints: int
    nonzeros: int
    activation_constraints: int
    capacity_constraints: int

    @property
    def variables(self) -> int:
        return (self.binary_variables + self.continuous_flow_variables
                + self.continuous_unserved_variables + self.continuous_delta_variables)


@dataclass(frozen=True)
class SolveResult:
    status: str
    objective: float
    travel_cost: float
    unserved_cost: float
    runtime_seconds: float
    selected_frequencies: Tuple[Mapping[int, int], ...]
    passenger_flows: Tuple[Mapping[Tuple[OD, str], float], ...]
    unserved: Tuple[Mapping[OD, float], ...]
    service_rate: Tuple[float, ...]  # per period
    overall_service_rate: float
    mip_gap: Optional[float]
    best_bound: Optional[float]
    message: str
    model_size: ModelSize
    metadata: Mapping[str, object] = field(default_factory=dict)


def _validate(data: PassengerModelData, config: ModelConfig) -> None:
    line_ids = set(data.lines)
    if set(config.frequencies) != line_ids:
        raise ValueError("Frequency options must be supplied for every and only every line")
    if len(config.budgets) != len(data.demands):
        raise ValueError("One budget is required per time period")
    for trips in data.trips.values():
        for trip in trips:
            for line_id, f in trip.services:
                if line_id not in line_ids or f not in config.frequencies[line_id]:
                    raise ValueError(f"Trip {trip.trip_id} uses unavailable service {(line_id, f)}")
    worst = data.max_trip_cost()
    if worst >= config.unserved_penalty and not config.allow_trip_cost_above_penalty:
        raise ValueError(
            f"max trip cost {worst:.3f} >= M={config.unserved_penalty}: report and re-confirm the "
            "penalty interpretation instead of silently enlarging M "
            "(set allow_trip_cost_above_penalty=True only for explicit sensitivity runs)")


def estimate_model_size(data: PassengerModelData, config: ModelConfig) -> ModelSize:
    """Exact size of the model that :func:`solve` builds (without building it)."""
    _validate(data, config)
    periods, line_ids = len(data.demands), tuple(sorted(data.lines))
    pairs = tuple(data.demands[0])
    trips = [t for p in pairs for t in data.trips_of(p)]
    service_requirements = sum(len(t.services) for t in trips)
    keys = {(l, f, c) for t in trips for (l, f, c, _a, _b) in t.service_arcs}
    flows_per_key = sum(len(t.service_arcs) for t in trips)
    return _size(periods, line_ids, config, len(pairs), len(trips), service_requirements,
                 len(keys), flows_per_key)


def _size(periods, line_ids, config, n_od, n_trips, service_requirements, n_keys, arc_incidences):
    z_count = periods * sum(len(config.frequencies[l]) for l in line_ids)
    activation = periods * service_requirements
    capacity = periods * n_keys
    selection = periods * len(line_ids)
    similarity = max(0, periods - 1) * (1 + 2 * len(line_ids))
    sim_nz = max(0, periods - 1) * (len(line_ids) + 2 * sum(
        2 * len(config.frequencies[l]) + 1 for l in line_ids))
    constraints = selection + periods * n_od + activation + capacity + periods + similarity
    nonzeros = (z_count + periods * (n_trips + n_od) + 2 * activation
                + periods * (arc_incidences + n_keys) + z_count + sim_nz)
    return ModelSize(z_count, periods * n_trips, periods * n_od,
                     max(0, periods - 1) * len(line_ids), constraints, nonzeros,
                     activation, capacity)


class _Builder:
    def __init__(self) -> None:
        self.cost: List[float] = []
        self.lower: List[float] = []
        self.upper: List[float] = []
        self.integrality: List[int] = []
        self.rows: List[Dict[int, float]] = []
        self.row_lower: List[float] = []
        self.row_upper: List[float] = []
        self.start: Dict[int, float] = {}

    def set_plan_start(self, z, config) -> None:
        """Partial MIP start from ``config.initial_plan`` (frequency per line and period)."""
        if config.initial_plan is None:
            return
        for (t, l, f), index in z.items():
            self.start[index] = 1.0 if config.initial_plan[t].get(l, 0) == f else 0.0

    def variable(self, cost=0.0, lower=0.0, upper=np.inf, binary=False) -> int:
        self.cost.append(float(cost)); self.lower.append(float(lower))
        self.upper.append(1.0 if binary else float(upper))
        self.integrality.append(1 if binary else 0)
        return len(self.cost) - 1

    def constraint(self, coefficients: Mapping[int, float], lower=-np.inf, upper=np.inf) -> None:
        self.rows.append({i: float(v) for i, v in coefficients.items() if v})
        self.row_lower.append(float(lower)); self.row_upper.append(float(upper))

    def solve(self, config: ModelConfig):
        if config.solver == "gurobi":
            from .solvers import solve_gurobi
            started = perf_counter()
            res = solve_gurobi(self.cost, self.lower, self.upper, self.integrality, self.rows,
                               self.row_lower, self.row_upper, self.start, config.time_limit,
                               config.mip_gap, config.threads, False, dict(config.gurobi_params))
            return res, perf_counter() - started
        r, c, v = [], [], []
        for row, coefficients in enumerate(self.rows):
            for column, value in coefficients.items():
                r.append(row); c.append(column); v.append(value)
        matrix = coo_matrix((v, (r, c)), shape=(len(self.rows), len(self.cost))).tocsr()
        options = {"disp": False, "mip_rel_gap": float(config.mip_gap)}
        if config.time_limit is not None:
            options["time_limit"] = float(config.time_limit)
        started = perf_counter()
        result = milp(
            c=np.asarray(self.cost), integrality=np.asarray(self.integrality),
            bounds=Bounds(np.asarray(self.lower), np.asarray(self.upper)),
            constraints=LinearConstraint(matrix, np.asarray(self.row_lower), np.asarray(self.row_upper)),
            options=options)
        return result, perf_counter() - started


_STATUS = {0: "optimal", 1: "limit_reached", 2: "infeasible", 3: "unbounded", 4: "solver_error"}


def solve(data: PassengerModelData, config: ModelConfig) -> SolveResult:
    size = estimate_model_size(data, config)
    b = _Builder()
    periods, line_ids = len(data.demands), tuple(sorted(data.lines))
    M = config.unserved_penalty

    z = {(t, l, f): b.variable(binary=True)
         for t in range(periods) for l in line_ids for f in config.frequencies[l]}
    b.set_plan_start(z, config)
    x: Dict[Tuple[int, OD, int], int] = {}
    u: Dict[Tuple[int, OD], int] = {}
    for t, demand in enumerate(data.demands):
        for pair in sorted(demand):
            u[(t, pair)] = b.variable(cost=M, upper=demand[pair])
            for i, trip in enumerate(data.trips_of(pair)):
                x[(t, pair, i)] = b.variable(cost=trip.generalized_time, upper=demand[pair])
    max_f = {l: max(config.frequencies[l]) for l in line_ids}
    delta = {(t, l): b.variable(upper=2.0 * max_f[l]) for t in range(periods - 1) for l in line_ids}

    for t in range(periods):
        for l in line_ids:
            b.constraint({z[(t, l, f)]: 1.0 for f in config.frequencies[l]}, upper=1.0)

    for t, demand in enumerate(data.demands):
        for pair in sorted(demand):
            trips = data.trips_of(pair)
            coeffs = {x[(t, pair, i)]: 1.0 for i in range(len(trips))}
            coeffs[u[(t, pair)]] = 1.0
            b.constraint(coeffs, lower=demand[pair], upper=demand[pair])
            for i, trip in enumerate(trips):
                for l, f in trip.services:
                    cap = config.capacities[l] * f
                    b.constraint({x[(t, pair, i)]: 1.0, z[(t, l, f)]: -min(demand[pair], cap)}, upper=0.0)

    terms: Dict[Tuple[int, int, int, int], Dict[int, float]] = {}
    for t, demand in enumerate(data.demands):
        for pair in sorted(demand):
            for i, trip in enumerate(data.trips_of(pair)):
                for l, f, cid, _a, _b in trip.service_arcs:
                    terms.setdefault((t, l, f, cid), {})[x[(t, pair, i)]] = 1.0
    for (t, l, f, _cid), coeffs in sorted(terms.items()):
        row = dict(coeffs)
        row[z[(t, l, f)]] = -(config.capacities[l] * f)
        b.constraint(row, upper=0.0)

    for t in range(periods):
        b.constraint({z[(t, l, f)]: config.operating_costs[(l, f)]
                      for l in line_ids for f in config.frequencies[l]}, upper=config.budgets[t])

    for t in range(periods - 1):
        b.constraint({delta[(t, l)]: 1.0 for l in line_ids}, upper=config.similarity_limit)
        for l in line_ids:
            for sign in (1.0, -1.0):
                row = {delta[(t, l)]: -1.0}
                for f in config.frequencies[l]:
                    row[z[(t + 1, l, f)]] = sign * f
                    row[z[(t, l, f)]] = -sign * f
                b.constraint(row, upper=0.0)

    result, runtime = b.solve(config)
    status = _STATUS.get(int(result.status), "unknown")
    selected, flows, unserved, rates = [], [], [], []
    travel = penalty_cost = 0.0
    if result.x is not None:
        sol = result.x
        for t in range(periods):
            selected.append({l: sum(f * int(round(sol[z[(t, l, f)]])) for f in config.frequencies[l])
                             for l in line_ids})
            period_flows, period_unserved = {}, {}
            for pair in sorted(data.demands[t]):
                for i, trip in enumerate(data.trips_of(pair)):
                    value = float(sol[x[(t, pair, i)]])
                    if value > 1e-8:
                        period_flows[(pair, trip.trip_id)] = value
                        travel += value * trip.generalized_time
                value_u = float(sol[u[(t, pair)]])
                if value_u > 1e-8:
                    period_unserved[pair] = value_u
                penalty_cost += M * value_u
            flows.append(period_flows); unserved.append(period_unserved)
            total = sum(data.demands[t].values())
            rates.append(1.0 - sum(period_unserved.values()) / total)
    total_demand = sum(sum(d.values()) for d in data.demands)
    overall = (1.0 - sum(sum(p.values()) for p in unserved) / total_demand) if unserved else float("nan")
    gap = getattr(result, "mip_gap", None)
    bound = getattr(result, "mip_dual_bound", None)
    return SolveResult(
        status=status, objective=float(result.fun) if result.fun is not None else math.inf,
        travel_cost=travel, unserved_cost=penalty_cost, runtime_seconds=runtime,
        selected_frequencies=tuple(selected), passenger_flows=tuple(flows), unserved=tuple(unserved),
        service_rate=tuple(rates), overall_service_rate=overall,
        mip_gap=float(gap) if gap is not None else None,
        best_bound=float(bound) if bound is not None else None,
        message=str(result.message), model_size=size,
        metadata={
            "unserved_penalty_M": M, "similarity_limit_K": config.similarity_limit,
            "budgets": list(config.budgets), "periods": periods,
            "max_trip_cost": data.max_trip_cost(),
            "max_trip_cost_below_M": data.max_trip_cost() < M,
            "capacity_directional": True, "similarity_scope": "adjacent periods",
            "finite_candidate_pool": True,
        })
