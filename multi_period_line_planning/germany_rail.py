"""Reconstruct the public Germany-rail/Bahn instance and generate a line pool.

The repository contains the 250-by-250 Bahn-01 OD matrix but not the physical
network. TimPassLib's public ``long-distance`` instance contains an event-
activity representation of the same 250-stop, 326-edge LinTim network. This
module deterministically reconstructs the PTN, combines it with the checked-in
OD matrix, and creates a compact demand-ranked shortest-path line pool.
"""

from __future__ import annotations

import heapq
import json
import math
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Sequence, Tuple


def _rows(path: Path) -> Iterable[List[str]]:
    with path.open(encoding="utf-8") as stream:
        for line_number, raw in enumerate(stream, 1):
            text = raw.split("#", 1)[0].strip()
            if not text:
                continue
            fields = [field.strip().strip('"') for field in text.split(";")]
            if any(field == "" for field in fields):
                raise ValueError(f"Empty field in {path}:{line_number}")
            yield fields


def _write_rows(path: Path, header: str, rows: Iterable[Sequence[object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as stream:
        stream.write(f"# {header}\n")
        for row in rows:
            stream.write("; ".join(str(value) for value in row) + "\n")


@dataclass(frozen=True)
class Event:
    stop: int
    line: int
    direction: str
    repetition: int


@dataclass(frozen=True)
class PhysicalEdge:
    edge_id: int
    left: int
    right: int
    travel_time: int
    upper_travel_time: int


@dataclass(frozen=True)
class PreparedGermanyRail:
    stops: int
    edges: int
    lines: int
    positive_od_pairs: int
    raw_demand: float
    normalized_demand: float
    max_lower_frequency: int
    output: Path


def _read_events(path: Path) -> Dict[int, Event]:
    events: Dict[int, Event] = {}
    for fields in _rows(path):
        if len(fields) != 6:
            raise ValueError(f"Expected six columns in {path}")
        event_id = int(fields[0])
        events[event_id] = Event(
            stop=int(fields[2]),
            line=int(fields[3]),
            direction=fields[4],
            repetition=int(fields[5]),
        )
    return events


def _read_drive_activities(
    path: Path, events: Mapping[int, Event]
) -> List[Tuple[int, Event, Event, int, int]]:
    drives: List[Tuple[int, Event, Event, int, int]] = []
    for fields in _rows(path):
        if len(fields) != 6:
            raise ValueError(f"Expected six columns in {path}")
        if fields[1].lower() != "drive":
            continue
        activity_id = int(fields[0])
        source = events[int(fields[2])]
        target = events[int(fields[3])]
        if source.line != target.line or source.direction != target.direction:
            raise ValueError(f"Drive activity {activity_id} changes line or direction")
        drives.append((activity_id, source, target, int(fields[4]), int(fields[5])))
    return drives


def _physical_network(
    drives: Sequence[Tuple[int, Event, Event, int, int]]
) -> Tuple[Tuple[PhysicalEdge, ...], Dict[Tuple[int, int], int]]:
    attributes: Dict[Tuple[int, int], List[Tuple[int, int]]] = defaultdict(list)
    for _, source, target, lower, upper in drives:
        pair = tuple(sorted((source.stop, target.stop)))
        if pair[0] == pair[1]:
            raise ValueError("A drive activity cannot be a self-loop")
        attributes[pair].append((lower, upper))

    edges: List[PhysicalEdge] = []
    pair_to_edge: Dict[Tuple[int, int], int] = {}
    for edge_id, pair in enumerate(sorted(attributes), 1):
        bounds = attributes[pair]
        edge = PhysicalEdge(
            edge_id=edge_id,
            left=pair[0],
            right=pair[1],
            travel_time=min(lower for lower, _ in bounds),
            upper_travel_time=max(upper for _, upper in bounds),
        )
        edges.append(edge)
        pair_to_edge[pair] = edge_id
    return tuple(edges), pair_to_edge


def _chain_arcs(arcs: Sequence[Tuple[int, int, int]]) -> Tuple[int, ...]:
    """Return edge ids in path order for directed ``(u, v, edge_id)`` arcs."""

    outgoing: Dict[int, Tuple[int, int]] = {}
    incoming = set()
    for left, right, edge_id in arcs:
        if left in outgoing:
            raise ValueError(f"A reconstructed line branches at stop {left}")
        outgoing[left] = (right, edge_id)
        incoming.add(right)
    starts = sorted(set(outgoing) - incoming)
    if len(starts) != 1:
        raise ValueError(f"Expected one line-path start, found {starts}")
    ordered: List[int] = []
    current = starts[0]
    while current in outgoing:
        current, edge_id = outgoing[current]
        ordered.append(edge_id)
    if len(ordered) != len(arcs):
        raise ValueError("A reconstructed line is disconnected or cyclic")
    return tuple(ordered)


def _official_lines(
    drives: Sequence[Tuple[int, Event, Event, int, int]],
    pair_to_edge: Mapping[Tuple[int, int], int],
) -> List[Tuple[int, ...]]:
    arcs_by_line: Dict[int, List[Tuple[int, int, int]]] = defaultdict(list)
    for _, source, target, _, _ in drives:
        if source.direction != ">" or source.repetition != 1:
            continue
        edge_id = pair_to_edge[tuple(sorted((source.stop, target.stop)))]
        arcs_by_line[source.line].append((source.stop, target.stop, edge_id))
    return [_chain_arcs(arcs_by_line[line]) for line in sorted(arcs_by_line)]


def _read_od(path: Path) -> Dict[Tuple[int, int], float]:
    demand: Dict[Tuple[int, int], float] = {}
    for fields in _rows(path):
        if len(fields) < 3:
            raise ValueError(f"Expected three columns in {path}")
        value = float(fields[2])
        if value > 0:
            demand[(int(fields[0]), int(fields[1]))] = value
    return demand


def _adjacency(edges: Sequence[PhysicalEdge]):
    graph: Dict[int, List[Tuple[int, int, int]]] = defaultdict(list)
    for edge in edges:
        graph[edge.left].append((edge.right, edge.travel_time, edge.edge_id))
        graph[edge.right].append((edge.left, edge.travel_time, edge.edge_id))
    for neighbours in graph.values():
        neighbours.sort()
    return graph


def _shortest_path_tree(start: int, graph):
    distances = {start: 0.0}
    predecessor: Dict[int, Tuple[int, int]] = {}
    queue: List[Tuple[float, int]] = [(0.0, start)]
    while queue:
        distance, node = heapq.heappop(queue)
        if distance != distances.get(node):
            continue
        for neighbour, weight, edge_id in graph[node]:
            candidate = distance + weight
            old = distances.get(neighbour, math.inf)
            old_predecessor = predecessor.get(neighbour, (math.inf, math.inf))
            if candidate < old or (
                candidate == old and (node, edge_id) < old_predecessor
            ):
                distances[neighbour] = candidate
                predecessor[neighbour] = (node, edge_id)
                heapq.heappush(queue, (candidate, neighbour))
    return predecessor


def _path_from_tree(
    origin: int, destination: int, predecessor: Mapping[int, Tuple[int, int]]
) -> Tuple[int, ...]:
    if destination == origin:
        return tuple()
    path: List[int] = []
    current = destination
    while current != origin:
        if current not in predecessor:
            raise ValueError(f"No path from stop {origin} to stop {destination}")
        current, edge_id = predecessor[current]
        path.append(edge_id)
    path.reverse()
    return tuple(path)


def _all_shortest_paths(
    stops: Sequence[int], edges: Sequence[PhysicalEdge]
) -> Dict[Tuple[int, int], Tuple[int, ...]]:
    graph = _adjacency(edges)
    paths: Dict[Tuple[int, int], Tuple[int, ...]] = {}
    for origin in stops:
        predecessor = _shortest_path_tree(origin, graph)
        for destination in stops:
            if destination != origin:
                paths[(origin, destination)] = _path_from_tree(
                    origin, destination, predecessor
                )
    return paths


def _canonical_path(path: Sequence[int]) -> Tuple[int, ...]:
    forward = tuple(path)
    backward = tuple(reversed(path))
    return min(forward, backward)


def _generate_line_pool(
    official_lines: Sequence[Tuple[int, ...]],
    demand: Mapping[Tuple[int, int], float],
    shortest_paths: Mapping[Tuple[int, int], Tuple[int, ...]],
    target_size: int,
    minimum_edges: int,
) -> Tuple[Tuple[int, ...], ...]:
    lines: List[Tuple[int, ...]] = []
    seen = set()

    def add(path: Sequence[int]) -> None:
        canonical = _canonical_path(path)
        if len(canonical) >= minimum_edges and canonical not in seen:
            seen.add(canonical)
            lines.append(tuple(path))

    for line in official_lines:
        add(line)
    ranked_pairs = sorted(demand, key=lambda pair: (-demand[pair], pair[0], pair[1]))
    for pair in ranked_pairs:
        add(shortest_paths[pair])
        if len(lines) >= target_size:
            break
    if len(lines) < target_size:
        for pair in sorted(shortest_paths):
            add(shortest_paths[pair])
            if len(lines) >= target_size:
                break
    if len(lines) != target_size:
        raise ValueError(f"Generated {len(lines)} lines, expected {target_size}")
    return tuple(lines)


def prepare_germany_rail(
    dataset: Path,
    source_od: Path,
    target_pool_size: int = 132,
    minimum_line_edges: int = 2,
    nominal_total_demand: float = 5000.0,
    vehicle_capacity: float = 70.0,
    fixed_upper_frequency: int = 20,
    fixed_line_cost: float = 50.0,
    time_cost: float = 0.05,
    edge_cost: float = 0.05,
) -> PreparedGermanyRail:
    """Build a reproducible LinTim dataset from public and checked-in sources."""

    dataset = Path(dataset)
    source_od = Path(source_od)
    events_path = dataset / "timpasslib" / "Events.csv"
    activities_path = dataset / "timpasslib" / "Activities.csv"
    for required in (events_path, activities_path, source_od):
        if not required.is_file():
            raise FileNotFoundError(required)

    events = _read_events(events_path)
    drives = _read_drive_activities(activities_path, events)
    edges, pair_to_edge = _physical_network(drives)
    stops = tuple(sorted({event.stop for event in events.values()}))
    if len(stops) != 250 or len(edges) != 326:
        raise ValueError(
            f"Expected the 250-stop, 326-edge Bahn network; got {len(stops)}, {len(edges)}"
        )

    demand = _read_od(source_od)
    shortest_paths = _all_shortest_paths(stops, edges)
    official_lines = _official_lines(drives, pair_to_edge)
    lines = _generate_line_pool(
        official_lines,
        demand,
        shortest_paths,
        target_pool_size,
        minimum_line_edges,
    )

    raw_total = sum(demand.values())
    scale = nominal_total_demand / raw_total
    edge_loads = {edge.edge_id: 0.0 for edge in edges}
    for pair, value in demand.items():
        for edge_id in shortest_paths[pair]:
            edge_loads[edge_id] += value * scale
    lower_frequencies = {
        edge_id: int(math.ceil(load / vehicle_capacity - 1e-12))
        for edge_id, load in edge_loads.items()
    }
    max_lower = max(lower_frequencies.values())
    if max_lower > fixed_upper_frequency:
        raise ValueError(
            f"Maximum lower frequency {max_lower} exceeds fixed upper bound "
            f"{fixed_upper_frequency}; reduce nominal_total_demand"
        )

    basis = dataset / "basis"
    _write_rows(
        basis / "Stop.giv",
        "stop-id; short-name; long-name; x-coordinate; y-coordinate",
        ((stop, stop, stop, 0, 0) for stop in stops),
    )
    _write_rows(
        basis / "Edge.giv",
        "edge-id; left-stop-id; right-stop-id; length; lower-bound; upper-bound",
        (
            (
                edge.edge_id,
                edge.left,
                edge.right,
                edge.travel_time,
                edge.travel_time,
                edge.upper_travel_time,
            )
            for edge in edges
        ),
    )
    _write_rows(
        basis / "OD.giv",
        "left-stop-id; right-stop-id; customers",
        ((origin, destination, value) for (origin, destination), value in sorted(demand.items())),
    )
    _write_rows(
        basis / "Load.giv",
        "edge-id; load; lower-frequency; upper-frequency",
        (
            (
                edge.edge_id,
                f"{edge_loads[edge.edge_id]:.6f}",
                lower_frequencies[edge.edge_id],
                fixed_upper_frequency,
            )
            for edge in edges
        ),
    )
    _write_rows(
        basis / "Pool.giv",
        "line-id; edge-order; edge-id",
        (
            (line_id, order, edge_id)
            for line_id, line in enumerate(lines, 1)
            for order, edge_id in enumerate(line, 1)
        ),
    )
    edge_by_id = {edge.edge_id: edge for edge in edges}
    line_lengths = [sum(edge_by_id[edge_id].travel_time for edge_id in line) for line in lines]
    line_costs = [
        fixed_line_cost + time_cost * length + edge_cost * len(line)
        for line, length in zip(lines, line_lengths)
    ]
    _write_rows(
        basis / "Pool-Cost.giv",
        "line-id; length; cost",
        (
            (line_id, length, f"{cost:.6f}")
            for line_id, (length, cost) in enumerate(zip(line_lengths, line_costs), 1)
        ),
    )

    metadata = {
        "network_source": "TimPassLib long-distance (LinTim Germany long-distance network)",
        "network_source_url": "https://timpasslib.aalto.fi/data/long-distance.zip",
        "od_source": str(source_od),
        "od_identity": "repository Bahn-01 matrix; statistics match TimPassLib/PESPlib R1L1",
        "stops": len(stops),
        "edges": len(edges),
        "official_seed_lines": len(official_lines),
        "pool_lines": len(lines),
        "pool_algorithm": "official-line seeds plus demand-ranked one-shortest-path augmentation",
        "minimum_line_edges": minimum_line_edges,
        "raw_positive_od_pairs": len(demand),
        "raw_total_demand": raw_total,
        "nominal_total_demand": nominal_total_demand,
        "vehicle_capacity": vehicle_capacity,
        "fixed_upper_frequency": fixed_upper_frequency,
        "maximum_lower_frequency": max_lower,
        "line_cost": {
            "fixed": fixed_line_cost,
            "travel_time_factor": time_cost,
            "edge_count_factor": edge_cost,
        },
    }
    (basis / "Generation-Metadata.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    return PreparedGermanyRail(
        stops=len(stops),
        edges=len(edges),
        lines=len(lines),
        positive_od_pairs=len(demand),
        raw_demand=raw_total,
        normalized_demand=nominal_total_demand,
        max_lower_frequency=max_lower,
        output=dataset,
    )
