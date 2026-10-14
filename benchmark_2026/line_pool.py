"""Frozen candidate line pool: load, freeze, and independently validate.

The pool is produced by the OFFICIAL Java ``LinePoolGenerator`` (see
``tools/java/ExportBenchmark.java``).  Because it is the reference code, the
pool may be called the official pool for the pinned commit.  This module adds
an *independent* Python check: every line is re-validated against the
infrastructure files, and a from-scratch enumeration (a test oracle, not the
production generator) must reproduce exactly the same set of lines.
"""

from __future__ import annotations

import hashlib
import heapq
import json
from collections import defaultdict
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Tuple

from .domain import (
    CAPACITY, DWELL_TIME, INTERCITY, PERIOD_MINUTES, TURNAROUND_TIME,
    BenchmarkInstance, CandidateLine,
)

EPS = 1e-6


def canonical_hash(lines: Iterable[CandidateLine]) -> str:
    payload = [
        [l.line_id, list(l.stations), list(l.connections_forward), list(l.connections_backward),
         l.train_type, round(l.duration, 8), round(l.cost_per_frequency, 8), l.max_frequency,
         l.capacity]
        for l in lines
    ]
    return hashlib.sha256(json.dumps(payload, separators=(",", ":")).encode()).hexdigest()


def line_from_record(rec: Mapping) -> CandidateLine:
    return CandidateLine(
        line_id=int(rec["line_id"]),
        stations=tuple(rec["stations"]),
        connections_forward=tuple(rec["connections_forward"]),
        connections_backward=tuple(rec["connections_backward"]),
        train_type=int(rec["type"]),
        duration=float(rec["duration"]),
        cost_per_frequency=float(rec["cost_per_frequency"]),
        max_frequency=int(rec["max_frequency"]),
        capacity=int(rec["capacity"]),
    )


def freeze_line_pool(java_lines_jsonl: Path, dest: Path, provenance: Mapping) -> Tuple[CandidateLine, ...]:
    """Convert the Java export into the persisted, hash-protected pool file."""
    records = [json.loads(x) for x in Path(java_lines_jsonl).read_text().splitlines() if x.strip()]
    lines = tuple(line_from_record(r) for r in records)
    document = {
        "description": "Frozen candidate line pool (official LinePoolGenerator output). "
                       "Backward service = reverse connections in reverse order.",
        "provenance": dict(provenance),
        "pool_sha256": canonical_hash(lines),
        "line_count": len(lines),
        "lines": records,
    }
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(document, indent=1) + "\n")
    return lines


def load_line_pool(path: Path) -> Tuple[Tuple[CandidateLine, ...], Mapping]:
    document = json.loads(Path(path).read_text())
    lines = tuple(line_from_record(r) for r in document["lines"])
    if canonical_hash(lines) != document["pool_sha256"]:
        raise ValueError(f"{path}: pool hash mismatch (file edited after freezing?)")
    if [l.line_id for l in lines] != list(range(len(lines))):
        raise ValueError("line ids must be 0..n-1 in pool order")
    return lines, document["provenance"]


# ----------------------------------------------------------------------------
# Infrastructure helpers (independent of the Java code)
# ----------------------------------------------------------------------------

def feasible_sequence(inst: BenchmarkInstance, incoming_id: int, outgoing_id: int) -> bool:
    """Python version of LinePlanningInstance.feasibleDriveArcSequence (dwell arc exists)."""
    inc, out = inst.connections[incoming_id], inst.connections[outgoing_id]
    last, first = inst.tracks[inc.track_ids[-1]], inst.tracks[out.track_ids[0]]
    if last.from_station == first.to_station:  # immediate return to the previous station
        return False
    if frozenset((last.track_id, first.track_id)) in inst.forbidden_turns or (
        len({last.track_id, first.track_id}) == 1 and frozenset((last.track_id,)) in inst.forbidden_turns
    ):
        return False
    if inc.train_type != out.train_type:
        raise NotImplementedError("type changes are not needed for the IC-only Randstad network")
    return True


def _track_distance(inst: BenchmarkInstance, source: str) -> Dict[str, float]:
    graph: Dict[str, List[Tuple[str, float]]] = defaultdict(list)
    for t in inst.tracks.values():
        graph[t.from_station].append((t.to_station, t.length))
    dist = {source: 0.0}
    heap = [(0.0, source)]
    while heap:
        d, u = heapq.heappop(heap)
        if d > dist.get(u, float("inf")):
            continue
        for v, w in graph[u]:
            if d + w < dist.get(v, float("inf")) - 1e-12:
                dist[v] = d + w
                heapq.heappush(heap, (d + w, v))
    return dist


def expected_line_attributes(inst: BenchmarkInstance, forward: Tuple[int, ...]) -> Tuple[float, int, float]:
    conns = [inst.connections[c] for c in forward]
    duration = sum(c.travel_time for c in conns) + DWELL_TIME * (len(conns) - 1)
    max_freq = min(c.max_frequency for c in conns)
    cost = (2 * duration + 2 * TURNAROUND_TIME) / PERIOD_MINUTES
    return duration, max_freq, cost


def validate_line_pool(inst: BenchmarkInstance, lines: Tuple[CandidateLine, ...],
                       oracle: bool = True) -> Dict[str, object]:
    """Re-check every line against the infrastructure; optionally compare with an
    independent enumeration.  Raises AssertionError listing problems."""
    problems: List[str] = []
    seen = set()
    for l in lines:
        fwd = l.connections_forward
        conns = [inst.connections.get(c) for c in fwd]
        if not fwd or any(c is None for c in conns):
            problems.append(f"line {l.line_id}: unknown connection"); continue
        if tuple(c.to_station for c in conns) != l.stations[1:] or conns[0].from_station != l.stations[0]:
            problems.append(f"line {l.line_id}: station list differs from connections")
        for a, b in zip(conns, conns[1:]):
            if a.to_station != b.from_station:
                problems.append(f"line {l.line_id}: connections not contiguous")
            elif not feasible_sequence(inst, a.connection_id, b.connection_id):
                problems.append(f"line {l.line_id}: infeasible turn {a.connection_id}->{b.connection_id}")
        if len(set(l.stations)) != len(l.stations):
            problems.append(f"line {l.line_id}: station repeated (not simple)")
        if any(c.train_type != INTERCITY for c in conns) or l.train_type != INTERCITY:
            problems.append(f"line {l.line_id}: not an IC-only line")
        o, d = inst.stations[l.stations[0]], inst.stations[l.stations[-1]]
        if not (o.terminal_ic and d.terminal_ic and o.name < d.name):
            problems.append(f"line {l.line_id}: bad terminals {o.name}->{d.name}")
        expected_back = tuple(inst.connections[c].reverse_id for c in reversed(fwd))
        if l.connections_backward != expected_back:
            problems.append(f"line {l.line_id}: backward service differs from reversed connections")
        duration, max_freq, cost = expected_line_attributes(inst, fwd)
        if abs(duration - l.duration) > EPS or abs(cost - l.cost_per_frequency) > EPS:
            problems.append(f"line {l.line_id}: duration/cost differ ({duration},{cost}) vs ({l.duration},{l.cost_per_frequency})")
        if max_freq != l.max_frequency or l.capacity != CAPACITY:
            problems.append(f"line {l.line_id}: max frequency/capacity differ")
        if fwd in seen:
            problems.append(f"line {l.line_id}: duplicate")
        seen.add(fwd)
        length = sum(c.length for c in conns)
        shortest = _track_distance(inst, l.stations[0]).get(l.stations[-1], float("inf"))
        if length > inst.parameters.line_detour_ratio * shortest + EPS:
            problems.append(f"line {l.line_id}: detour {length / shortest:.4f} > ratio")
    report: Dict[str, object] = {"lines": len(lines), "rule_problems": len(problems)}
    if oracle:
        reference = reference_line_enumeration(inst)
        pool = {l.connections_forward for l in lines}
        missing, extra = reference - pool, pool - reference
        report.update(oracle_lines=len(reference), missing_from_pool=len(missing), extra_in_pool=len(extra))
        if missing or extra:
            problems.append(f"oracle mismatch: missing={sorted(missing)[:3]} extra={sorted(extra)[:3]}")
    if problems:
        raise AssertionError("; ".join(problems[:10]))
    return report


def reference_line_enumeration(inst: BenchmarkInstance) -> frozenset:
    """Independent enumeration of all lines obeying the small-pool rules:
    simple station paths between IC terminals (name order), feasible turns,
    total connection length <= ratio * shortest track distance."""
    out_edges: Dict[str, List[int]] = defaultdict(list)
    for c in inst.connections.values():
        out_edges[c.from_station].append(c.connection_id)
    terminals = sorted(s for s in inst.active_stations if inst.stations[s].terminal_ic)
    found = set()
    for o in terminals:
        dist = _track_distance(inst, o)
        for d in terminals:
            if not o < d or d not in dist:
                continue
            threshold = inst.parameters.line_detour_ratio * dist[d]
            # lower bound for pruning: shortest connection-length distance to d
            back = defaultdict(list)
            for c in inst.connections.values():
                back[c.to_station].append((c.from_station, c.length))
            lb = {d: 0.0}
            heap = [(0.0, d)]
            while heap:
                x, u = heapq.heappop(heap)
                if x > lb.get(u, float("inf")):
                    continue
                for v, w in back[u]:
                    if x + w < lb.get(v, float("inf")) - 1e-12:
                        lb[v] = x + w
                        heapq.heappush(heap, (x + w, v))

            def dfs(station: str, path: List[int], visited: set, length: float):
                if station == d:
                    found.add(tuple(path))
                    return
                for cid in out_edges[station]:
                    c = inst.connections[cid]
                    if c.to_station in visited or c.train_type != INTERCITY:
                        continue
                    if path and not feasible_sequence(inst, path[-1], cid):
                        continue
                    if length + c.length + lb.get(c.to_station, float("inf")) > threshold + EPS:
                        continue
                    if not path and not inst.stations[o].terminal_ic:
                        continue
                    path.append(cid); visited.add(c.to_station)
                    dfs(c.to_station, path, visited, length + c.length)
                    path.pop(); visited.discard(c.to_station)

            dfs(o, [], {o}, 0.0)
    return frozenset(found)
