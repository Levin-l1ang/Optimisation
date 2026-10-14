"""Passenger itineraries on the frozen line pool (medium route-generation policy).

Pipeline (all steps are deterministic):

1. The OFFICIAL ``RouteGenerator`` enumerates DPTN routes (infrastructure
   feasibility, forbidden turns, dwell/transfer arcs, transfer-count limit
   ``min(2, minimum transfers + 1)``) inside a wide duration window; the Java
   export is persisted as ``dptn_routes.jsonl``.
2. Each route is split into legs at its transfer arcs and every leg is mapped to
   the pool lines that serve it (same notion as ``Line.supports``): the leg's
   connection sequence must be a contiguous part of the line's forward or
   backward connection sequence.
3. ``shortest realizable`` = cheapest DPTN duration among routes whose legs are all
   served by pool lines.  Only itineraries with duration
   ``<= shortest realizable + 15`` are retained -- BEFORE any frequency expansion.
"""

from __future__ import annotations

import json
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Mapping, Optional, Sequence, Tuple

from .domain import DWELL_TIME, BenchmarkInstance, CandidateLine, Itinerary, Leg

EPS = 1e-6


@dataclass(frozen=True)
class OfficialRoute:
    duration: float
    transfers: int
    connections: Tuple[int, ...]
    transfer_after: Tuple[int, ...]


def load_official_routes(path: Path) -> Dict[Tuple[str, str], Tuple[Optional[float], Tuple[OfficialRoute, ...]]]:
    result = {}
    for raw in Path(path).read_text().splitlines():
        if not raw.strip():
            continue
        rec = json.loads(raw)
        routes = tuple(
            OfficialRoute(r["duration"], r["transfers"], tuple(r["connections"]), tuple(r["transfer_after"]))
            for r in rec["routes"]
        )
        result[(rec["origin"], rec["destination"])] = (rec["shortest"], routes)
    return result


class LineSupport:
    """Which pool lines serve a given connection sequence (a leg)."""

    def __init__(self, lines: Sequence[CandidateLine]):
        self._lines = lines
        self._cache: Dict[Tuple[int, ...], Tuple[int, ...]] = {}
        # index: first connection -> candidates, to avoid scanning all lines
        self._by_first: Dict[int, List[Tuple[int, Tuple[int, ...]]]] = defaultdict(list)
        for line in lines:
            for seq in (line.connections_forward, line.connections_backward):
                for position, cid in enumerate(seq):
                    self._by_first[cid].append((line.line_id, seq[position:]))

    def lines_for(self, leg: Tuple[int, ...]) -> Tuple[int, ...]:
        cached = self._cache.get(leg)
        if cached is None:
            n = len(leg)
            cached = tuple(sorted({
                line_id for line_id, tail in self._by_first.get(leg[0], ())
                if tail[:n] == leg
            }))
            self._cache[leg] = cached
        return cached


@dataclass
class RoutePoolDiagnostics:
    od_pairs: int = 0
    od_without_route: List[Tuple[str, str]] = field(default_factory=list)
    od_shortest_realizable_above_infrastructure: int = 0
    max_realizable_gap: float = 0.0
    window_insufficient: List[Tuple[str, str]] = field(default_factory=list)
    official_routes: int = 0
    realizable_routes: int = 0
    itineraries_before_pruning: int = 0
    pruned_same_line_transfer: int = 0
    duplicates_removed: int = 0
    itineraries: int = 0
    ride_time_mismatches: int = 0


@dataclass
class RoutePool:
    itineraries: Dict[Tuple[str, str], Tuple[Itinerary, ...]]
    shortest_realizable: Dict[Tuple[str, str], float]
    infrastructure_shortest: Dict[Tuple[str, str], float]
    diagnostics: RoutePoolDiagnostics
    max_detour: float
    transfer_penalty: float


def _itinerary_ride_time(inst: BenchmarkInstance, legs: Sequence[Sequence[int]]) -> float:
    total = 0.0
    for leg in legs:
        total += sum(inst.connections[c].travel_time for c in leg) + DWELL_TIME * (len(leg) - 1)
    return total


def build_route_pool(
    inst: BenchmarkInstance,
    lines: Sequence[CandidateLine],
    official: Mapping[Tuple[str, str], Tuple[Optional[float], Tuple[OfficialRoute, ...]]],
    window: float,
    max_detour: Optional[float] = None,
    prune_same_line_transfers: bool = True,
) -> RoutePool:
    """Apply the +``max_detour`` policy relative to the shortest *realizable* route.

    ``window`` is the detour window used when the official routes were exported;
    it must be large enough that ``realizable + max_detour`` lies inside it.
    """
    detour = float(inst.parameters.route_max_detour if max_detour is None else max_detour)
    penalty = float(inst.parameters.transfer_penalty)
    support = LineSupport(lines)
    diag = RoutePoolDiagnostics()
    pool: Dict[Tuple[str, str], Tuple[Itinerary, ...]] = {}
    shortest_real: Dict[Tuple[str, str], float] = {}
    infra: Dict[Tuple[str, str], float] = {}

    for od in inst.od_pairs:
        diag.od_pairs += 1
        shortest, routes = official[od.key]
        diag.official_routes += len(routes)
        if shortest is None:
            diag.od_without_route.append(od.key)
            continue
        infra[od.key] = shortest
        candidates: List[Tuple[float, Tuple[Tuple[int, ...], ...], Tuple[Tuple[int, ...], ...]]] = []
        for route in routes:
            cuts = (0, *route.transfer_after, len(route.connections))
            legs = tuple(route.connections[cuts[i]:cuts[i + 1]] for i in range(len(cuts) - 1))
            if any(len(leg) == 0 for leg in legs):
                raise ValueError(f"{od.key}: empty leg in official route")
            options = tuple(support.lines_for(leg) for leg in legs)
            if all(options):
                ride = _itinerary_ride_time(inst, legs)
                if abs(ride + penalty * route.transfers - route.duration) > EPS:
                    diag.ride_time_mismatches += 1
                candidates.append((route.duration, legs, options))
        diag.realizable_routes += len(candidates)
        if not candidates:
            diag.od_without_route.append(od.key)
            continue
        best = min(c[0] for c in candidates)
        shortest_real[od.key] = best
        if best > shortest + EPS:
            diag.od_shortest_realizable_above_infrastructure += 1
            diag.max_realizable_gap = max(diag.max_realizable_gap, best - shortest)
        if best + detour > shortest + window + EPS:
            diag.window_insufficient.append(od.key)

        seen = set()
        kept: List[Itinerary] = []
        for duration, legs, options in sorted(candidates, key=lambda c: (c[0], c[1])):
            if duration > best + detour + EPS:
                continue
            for assignment in _product(options):
                diag.itineraries_before_pruning += 1
                if prune_same_line_transfers and _has_mergeable_legs(lines, legs, assignment):
                    diag.pruned_same_line_transfer += 1
                    continue
                signature = tuple((line_id, leg[0], leg[-1], len(leg)) for line_id, leg in zip(assignment, legs))
                if signature in seen:
                    diag.duplicates_removed += 1
                    continue
                seen.add(signature)
                kept.append(_make_itinerary(inst, lines, od.origin, od.destination, legs, assignment))
        pool[od.key] = tuple(kept)
        diag.itineraries += len(kept)
    return RoutePool(pool, shortest_real, infra, diag, detour, penalty)


def _product(options: Sequence[Sequence[int]]):
    result = [()]
    for opts in options:
        result = [prefix + (o,) for prefix in result for o in opts]
    return result


def _is_contiguous(seq: Sequence[int], part: Sequence[int]) -> bool:
    n = len(part)
    return any(tuple(seq[i:i + n]) == tuple(part) for i in range(len(seq) - n + 1))


def _has_mergeable_legs(lines, legs, assignment) -> bool:
    """True if two consecutive legs ride the same line and the merged leg is
    still served by that line: that itinerary is strictly dominated by the
    transfer-free one (same arcs and services, one fewer transfer and wait)."""
    for i in range(len(legs) - 1):
        if assignment[i] != assignment[i + 1]:
            continue
        line = lines[assignment[i]]
        merged = tuple(legs[i]) + tuple(legs[i + 1])
        if _is_contiguous(line.connections_forward, merged) or _is_contiguous(line.connections_backward, merged):
            return True
    return False


def _make_itinerary(inst, lines, origin, destination, legs, assignment) -> Itinerary:
    leg_objects = []
    for leg, line_id in zip(legs, assignment):
        arcs = tuple((cid, inst.connections[cid].from_station, inst.connections[cid].to_station) for cid in leg)
        leg_objects.append(Leg(line_id, arcs[0][1], arcs[-1][2], arcs))
    return Itinerary(origin, destination, tuple(leg_objects), _itinerary_ride_time(inst, legs))
