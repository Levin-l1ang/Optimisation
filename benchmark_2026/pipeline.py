"""Glue between the frozen benchmark data, the route pool and the MILP."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

from . import paths
from .domain import BenchmarkInstance, CandidateLine, ODPair
from .io import load_instance
from .line_pool import load_line_pool
from .model import ModelConfig, ModelSize, PassengerModelData, _size
from .multiperiod import MultiPeriodDemand
from .passenger_routes import RoutePool, build_route_pool, load_official_routes
from .prepare import ROUTE_EXPORT_WINDOW, derive_penalty
from .trip_expansion import TripExpansionConfig, expand_itinerary, expansion_count


@dataclass
class Primary:
    instance: BenchmarkInstance
    lines: Tuple[CandidateLine, ...]
    route_pool: RoutePool
    penalty: Mapping[str, object]


def load_primary() -> Primary:
    instance = load_instance(paths.INSTANCE)
    lines, _ = load_line_pool(paths.LINE_POOL)
    official = load_official_routes(paths.OFFICIAL_ROUTES)
    pool = build_route_pool(instance, lines, official, window=ROUTE_EXPORT_WINDOW)
    return Primary(instance, lines, pool, derive_penalty(paths.OD_BOUNDS))


def frequency_options(lines: Sequence[CandidateLine],
                      allowed: Optional[Iterable[int]] = None) -> Dict[int, Tuple[int, ...]]:
    """Frequencies 1..max_frequency(l) (the benchmark upper bound); ``allowed``
    optionally restricts them (a reduced experiment, never the primary one)."""
    allowed_set = None if allowed is None else set(allowed)
    return {
        l.line_id: tuple(f for f in range(1, l.max_frequency + 1)
                         if allowed_set is None or f in allowed_set)
        for l in lines
    }


def estimate_scale(primary: Primary, freqs: Mapping[int, Tuple[int, ...]], periods: int,
                   od_subset: Optional[Iterable[Tuple[str, str]]] = None) -> Dict[str, object]:
    """Exact model size for the (sub)instance without materialising the trips."""
    wanted = None if od_subset is None else set(od_subset)
    pairs = [od for od in primary.instance.od_pairs if wanted is None or od.key in wanted]
    cfg_freqs = TripExpansionConfig(freqs, primary.instance.parameters.transfer_penalty)
    n_trips = services = incidences = 0
    keys = set()
    itineraries = 0
    for od in pairs:
        for it in primary.route_pool.itineraries.get(od.key, ()):
            itineraries += 1
            t = expansion_count(it, cfg_freqs)
            n_trips += t
            services += t * len(it.legs)
            incidences += t * sum(len(leg.arcs) for leg in it.legs)
            for leg in it.legs:
                for f in freqs[leg.line_id]:
                    for cid, _a, _b in leg.arcs:
                        keys.add((leg.line_id, f, cid))
    dummy = ModelConfig(freqs, {l: 1 for l in freqs}, {(l, f): 0 for l in freqs for f in freqs[l]},
                        (0,) * periods, 0, 1.0)
    size = _size(periods, tuple(sorted(freqs)), dummy, len(pairs), n_trips, services, len(keys), incidences)
    return {"od_pairs": len(pairs), "itineraries": itineraries, "trips_per_period": n_trips,
            "service_requirements_per_period": services, "capacity_keys_per_period": len(keys),
            "periods": periods, **size.__dict__, "variables": size.variables}


def build_data(primary: Primary, demand: MultiPeriodDemand, freqs: Mapping[int, Tuple[int, ...]],
               od_subset: Optional[Iterable[Tuple[str, str]]] = None) -> PassengerModelData:
    wanted = None if od_subset is None else set(od_subset)
    expansion = TripExpansionConfig(freqs, primary.instance.parameters.transfer_penalty)
    keep = [i for i, od in enumerate(demand.od_pairs) if wanted is None or od.key in wanted]
    demands = tuple({demand.od_pairs[i].key: demand.od_pairs[i].volume * demand.ratios[p][i] for i in keep}
                    for p in range(demand.periods))
    trips = {}
    for i in keep:
        key = demand.od_pairs[i].key
        expanded: List = []
        for it in primary.route_pool.itineraries.get(key, ()):
            expanded.extend(expand_itinerary(it, expansion))
        if expanded:
            trips[key] = tuple(expanded)
    return PassengerModelData({l.line_id: l for l in primary.lines}, demands, trips)


def build_config(primary: Primary, freqs, periods: int, M: float, K: float,
                 budgets: Optional[Sequence[float]] = None, time_limit: Optional[float] = 600.0,
                 mip_gap: float = 0.0, allow_trip_cost_above_penalty: bool = False,
                 solver: str = "highs", threads: Optional[int] = None, initial_plan=None,
                 gurobi_params=None) -> ModelConfig:
    params = primary.instance.parameters
    costs = {(l.line_id, f): params.fixed_cost + l.cost_per_frequency * f
             for l in primary.lines for f in freqs[l.line_id]}
    return ModelConfig(
        frequencies=dict(freqs), capacities={l.line_id: float(l.capacity) for l in primary.lines},
        operating_costs=costs, budgets=tuple(budgets if budgets is not None else [params.budget] * periods),
        similarity_limit=float(K), unserved_penalty=float(M), time_limit=time_limit, mip_gap=mip_gap,
        allow_trip_cost_above_penalty=allow_trip_cost_above_penalty,
        solver=solver, threads=threads,
        initial_plan=None if initial_plan is None else tuple(dict(p) for p in initial_plan),
        gurobi_params=dict(gurobi_params or {}))


def top_od_subset(primary: Primary, n: int) -> List[Tuple[str, str]]:
    ranked = sorted(primary.instance.od_pairs, key=lambda o: (-o.volume, o.key))
    return [o.key for o in ranked[:n]]


def build_compact_data(primary: Primary, demand: MultiPeriodDemand,
                       od_subset: Optional[Iterable[Tuple[str, str]]] = None):
    from .model_compact import CompactData
    wanted = None if od_subset is None else set(od_subset)
    keep = [i for i, od in enumerate(demand.od_pairs) if wanted is None or od.key in wanted]
    demands = tuple({demand.od_pairs[i].key: demand.od_pairs[i].volume * demand.ratios[p][i] for i in keep}
                    for p in range(demand.periods))
    its = {demand.od_pairs[i].key: primary.route_pool.itineraries.get(demand.od_pairs[i].key, ()) for i in keep}
    return CompactData({l.line_id: l for l in primary.lines}, demands, its,
                       float(primary.instance.parameters.transfer_penalty))
