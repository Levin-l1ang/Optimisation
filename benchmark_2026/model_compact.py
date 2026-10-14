"""Line-level reformulation of the passenger MILP (exactly the same optimum).

The frequency-specific-trip model of :mod:`benchmark_2026.model` (the
specification, handoff §5) expands every itinerary over the Cartesian product of
the frequency options of its legs.  Waiting time ``P/(2 f)`` is *separable per
leg* and each line has at most one active frequency per period, so the identical
optimum is obtained without that expansion:

    x[t,I]      flow on itinerary I (no frequency index)
    w[t,l,f]    boarding flow on line l while frequency f is active
    min  sum_I (ride_I + tau*transfers_I) x_I + sum_{l,f} P/(2f) w[l,f] + M sum u
    s.t. sum_p...: sum_I x[t,I] + u = D                                   (per OD)
         x[t,I] <= D * sum_f z[t,l,f]                      for every leg of I
         sum_f w[t,l,f] = sum_{I, leg j on l} x[t,I]       (every boarding waits)
         w[t,l,f] <= Wmax_l * z[t,l,f]
         sum_{I uses (l,c)} x[t,I] <= sum_f C_l f z[t,l,f]  per directed connection c
         selection / budget / adjacent-period similarity as in the main model.

Equivalence (aggregate the old solution / place x_I on the unique trip whose
frequencies match the active ones) is checked numerically in the test-suite.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Dict, Tuple

from typing import Mapping

from .domain import CandidateLine, Itinerary
from .model import ModelConfig, ModelSize, OD, SolveResult, _Builder, _STATUS


@dataclass(frozen=True)
class CompactData:
    lines: Mapping[int, CandidateLine]
    demands: Tuple[Mapping[OD, float], ...]
    itineraries: Mapping[OD, Tuple[Itinerary, ...]]  # an OD with none is fully unserved
    transfer_penalty: float
    period_length: float = 60.0

    def __post_init__(self) -> None:
        if not self.demands:
            raise ValueError("At least one demand period is required")
        support = set(self.demands[0])
        for demand in self.demands:
            if set(demand) != support or any(v <= 0 for v in demand.values()):
                raise ValueError("every OD must keep a positive demand in every period")

    def itineraries_of(self, pair: OD) -> Tuple[Itinerary, ...]:
        return tuple(self.itineraries.get(pair, ()))

    def max_trip_cost(self, config: ModelConfig) -> float:
        """max_p c_p of the equivalent trip model: every leg at its lowest allowed frequency."""
        worst = 0.0
        for its in self.itineraries.values():
            for it in its:
                wait = sum(self.period_length / (2.0 * min(config.frequencies[leg.line_id])) for leg in it.legs)
                worst = max(worst, it.ride_time + self.transfer_penalty * it.transfers + wait)
        return worst


def _construct(data: CompactData, config: ModelConfig):
    line_ids = tuple(sorted(data.lines))
    if set(config.frequencies) != set(line_ids) or len(config.budgets) != len(data.demands):
        raise ValueError("frequencies for every line and one budget per period are required")
    worst = data.max_trip_cost(config)
    if worst >= config.unserved_penalty and not config.allow_trip_cost_above_penalty:
        raise ValueError(
            f"max trip cost {worst:.3f} >= M={config.unserved_penalty}: report and re-confirm the "
            "penalty interpretation instead of silently enlarging M")
    periods, M, P, tau = len(data.demands), config.unserved_penalty, data.period_length, data.transfer_penalty
    b = _Builder()
    z = {(t, l, f): b.variable(binary=True) for t in range(periods) for l in line_ids for f in config.frequencies[l]}
    b.set_plan_start(z, config)
    x: Dict[Tuple[int, OD, int], int] = {}
    u: Dict[Tuple[int, OD], int] = {}
    boarding: Dict[Tuple[int, int], Dict[int, float]] = {}
    capacity: Dict[Tuple[int, int, int], Dict[int, float]] = {}
    wmax: Dict[Tuple[int, int], float] = {}
    for t, demand in enumerate(data.demands):
        for pair in sorted(demand):
            u[(t, pair)] = b.variable(cost=M, upper=demand[pair])
            for i, it in enumerate(data.itineraries_of(pair)):
                var = b.variable(cost=it.ride_time + tau * it.transfers, upper=demand[pair])
                x[(t, pair, i)] = var
                for leg in it.legs:
                    terms = boarding.setdefault((t, leg.line_id), {})
                    terms[var] = terms.get(var, 0.0) + 1.0
                    wmax[(t, leg.line_id)] = wmax.get((t, leg.line_id), 0.0) + demand[pair]
                    for cid, _a, _b in leg.arcs:
                        cap = capacity.setdefault((t, leg.line_id, cid), {})
                        cap[var] = cap.get(var, 0.0) + 1.0
    w = {(t, l, f): b.variable(cost=P / (2.0 * f)) for (t, l) in boarding for f in config.frequencies[l]}
    max_f = {l: max(config.frequencies[l]) for l in line_ids}
    delta = {(t, l): b.variable(upper=2.0 * max_f[l]) for t in range(periods - 1) for l in line_ids}

    for t in range(periods):
        for l in line_ids:
            b.constraint({z[(t, l, f)]: 1.0 for f in config.frequencies[l]}, upper=1.0)
    for t, demand in enumerate(data.demands):
        for pair in sorted(demand):
            its = data.itineraries_of(pair)
            row = {x[(t, pair, i)]: 1.0 for i in range(len(its))}
            row[u[(t, pair)]] = 1.0
            b.constraint(row, lower=demand[pair], upper=demand[pair])
            for i, it in enumerate(its):
                for leg in it.legs:
                    row = {x[(t, pair, i)]: 1.0}
                    for f in config.frequencies[leg.line_id]:
                        row[z[(t, leg.line_id, f)]] = -demand[pair]
                    b.constraint(row, upper=0.0)
    for (t, l), terms in sorted(boarding.items()):
        row = dict(terms)
        for f in config.frequencies[l]:
            row[w[(t, l, f)]] = -1.0
        b.constraint(row, lower=0.0, upper=0.0)
        for f in config.frequencies[l]:
            b.constraint({w[(t, l, f)]: 1.0, z[(t, l, f)]: -wmax[(t, l)]}, upper=0.0)
    for (t, l, _cid), terms in sorted(capacity.items()):
        row = dict(terms)
        for f in config.frequencies[l]:
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
    size = ModelSize(
        binary_variables=sum(b.integrality), continuous_flow_variables=len(x) + len(w),
        continuous_unserved_variables=len(u), continuous_delta_variables=len(delta),
        constraints=len(b.rows), nonzeros=sum(len(r) for r in b.rows),
        activation_constraints=periods * sum(len(it.legs) for p in data.demands[0] for it in data.itineraries_of(p)),
        capacity_constraints=len(capacity))
    return b, z, x, u, size, worst


def estimate_compact_size(data: CompactData, config: ModelConfig) -> ModelSize:
    """Exact size (constructs the rows, does not solve)."""
    return _construct(data, config)[4]


def solve_compact(data: CompactData, config: ModelConfig) -> SolveResult:
    b, z, x, u, size, worst = _construct(data, config)
    periods, M, P, tau = len(data.demands), config.unserved_penalty, data.period_length, data.transfer_penalty
    line_ids = tuple(sorted(data.lines))
    result, runtime = b.solve(config)
    status = _STATUS.get(int(result.status), "unknown")
    selected, flows, unserved, rates = [], [], [], []
    travel = penalty = 0.0
    if result.x is not None:
        sol = result.x
        for t in range(periods):
            chosen = {l: sum(f * int(round(sol[z[(t, l, f)]])) for f in config.frequencies[l]) for l in line_ids}
            selected.append(chosen)
            period_flows, period_unserved = {}, {}
            for pair in sorted(data.demands[t]):
                for i, it in enumerate(data.itineraries_of(pair)):
                    value = float(sol[x[(t, pair, i)]])
                    # flows below 1e-6 on a line whose binary rounds to 0 are solver noise
                    if value > 1e-6 and all(chosen[leg.line_id] > 0 for leg in it.legs):
                        waiting = sum(P / (2.0 * chosen[leg.line_id]) for leg in it.legs)
                        period_flows[(pair, repr(it.signature))] = value
                        travel += value * (it.ride_time + tau * it.transfers + waiting)
                value_u = float(sol[u[(t, pair)]])
                if value_u > 1e-8:
                    period_unserved[pair] = value_u
                penalty += M * value_u
            flows.append(period_flows); unserved.append(period_unserved)
            rates.append(1.0 - sum(period_unserved.values()) / sum(data.demands[t].values()))
    total = sum(sum(d.values()) for d in data.demands)
    overall = (1.0 - sum(sum(p.values()) for p in unserved) / total) if unserved else float("nan")
    gap = getattr(result, "mip_gap", None)
    bound = getattr(result, "mip_dual_bound", None)
    return SolveResult(
        status=status, objective=float(result.fun) if result.fun is not None else math.inf,
        travel_cost=travel, unserved_cost=penalty, runtime_seconds=runtime,
        selected_frequencies=tuple(selected), passenger_flows=tuple(flows), unserved=tuple(unserved),
        service_rate=tuple(rates), overall_service_rate=overall,
        mip_gap=float(gap) if gap is not None else None, best_bound=float(bound) if bound is not None else None,
        message=str(result.message), model_size=size,
        metadata={"formulation": "line-level (equivalent to frequency-specific trips)",
                  "unserved_penalty_M": M, "similarity_limit_K": config.similarity_limit,
                  "budgets": list(config.budgets), "periods": periods, "max_trip_cost": worst,
                  "max_trip_cost_below_M": worst < M})
