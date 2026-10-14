"""Strict readers and audit helpers for the LinTim files used here."""

from __future__ import annotations

import hashlib
import json
from collections import defaultdict
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Optional, Sequence, Tuple

from .domain import Edge, Line, LinTimDataset


def _rows(path: Path) -> Iterable[Tuple[int, List[str]]]:
    with path.open(encoding="utf-8") as stream:
        for line_number, raw in enumerate(stream, 1):
            text = raw.split("#", 1)[0].strip()
            if not text:
                continue
            fields = [field.strip().strip('"') for field in text.split(";")]
            if any(field == "" for field in fields):
                raise ValueError(f"Empty field in {path}:{line_number}")
            yield line_number, fields


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _read_config(path: Path, visited: Optional[set] = None) -> Dict[str, str]:
    """Read LinTim's simple include-based configuration format."""

    if not path.is_file():
        return {}
    visited = set() if visited is None else visited
    resolved = path.resolve()
    if resolved in visited:
        return {}
    visited.add(resolved)
    result: Dict[str, str] = {}
    with path.open(encoding="utf-8") as stream:
        for raw in stream:
            text = raw.split("#", 1)[0].strip()
            if not text:
                continue
            fields = [field.strip().strip('"') for field in text.split(";")]
            if len(fields) < 2:
                continue
            key, value = fields[0], fields[1]
            if key in {"include", "include_if_exists"}:
                child = (path.parent / value).resolve()
                if child.is_file():
                    result.update(_read_config(child, visited))
                continue
            result[key] = value
    return result


def _order_line(
    line_id: int,
    edge_ids: Sequence[int],
    edges: Mapping[int, Edge],
    directed: bool,
) -> Tuple[int, ...]:
    if not edge_ids:
        raise ValueError(f"Line {line_id} has no edges")
    first = edges[edge_ids[0]]
    if len(edge_ids) == 1:
        return (first.left, first.right)

    second = edges[edge_ids[1]]
    second_ends = {second.left, second.right}
    if first.right in second_ends:
        stops = [first.left, first.right]
    elif first.left in second_ends and not directed:
        stops = [first.right, first.left]
    else:
        raise ValueError(
            f"Line {line_id} edges {edge_ids[0]} and {edge_ids[1]} are not "
            "a valid ordered continuation"
        )

    for edge_id in edge_ids[1:]:
        edge = edges[edge_id]
        current = stops[-1]
        if edge.left == current:
            stops.append(edge.right)
        elif edge.right == current and not directed:
            stops.append(edge.left)
        else:
            raise ValueError(
                f"Line {line_id} cannot append edge {edge_id} at stop {current}"
            )
    if len(set(stops)) != len(stops):
        raise ValueError(f"Line {line_id} is not a simple path: {stops}")
    return tuple(stops)


def load_lintim_dataset(
    dataset: Path,
    directed: Optional[bool] = None,
    travel_time_model: Optional[str] = None,
) -> LinTimDataset:
    """Load and validate ``basis/{Stop,Edge,OD,Pool,Pool-Cost}.giv``.

    The function does not infer missing benchmark files and never generates a
    replacement line pool.  ``directed`` can override the LinTim configuration
    for controlled experiments; otherwise ``ptn_is_undirected`` is respected.
    """

    root = Path(dataset)
    basis = root / "basis"
    paths = {
        "stops": basis / "Stop.giv",
        "edges": basis / "Edge.giv",
        "od": basis / "OD.giv",
        "pool": basis / "Pool.giv",
        "costs": basis / "Pool-Cost.giv",
    }
    for name, path in paths.items():
        if not path.is_file():
            raise FileNotFoundError(f"Missing LinTim {name} file: {path}")

    config = _read_config(basis / "Config.cnf")
    if directed is None:
        raw = config.get("ptn_is_undirected")
        if raw is None:
            raise ValueError(
                "ptn_is_undirected is absent; pass directed=True or directed=False explicitly"
            )
        directed = raw.strip().lower() not in {"true", "1", "yes"}
    drive_model = (
        travel_time_model
        or config.get("ean_model_weight_drive", "AVERAGE_DRIVING_TIME")
    ).upper()
    supported_drive_models = {
        "AVERAGE_DRIVING_TIME",
        "MINIMAL_DRIVING_TIME",
        "MAXIMAL_DRIVING_TIME",
        "EDGE_LENGTH",
    }
    if drive_model not in supported_drive_models:
        raise ValueError(
            f"Unsupported travel-time model {drive_model}; expected one of "
            f"{sorted(supported_drive_models)}"
        )

    stops: List[int] = []
    for line_number, fields in _rows(paths["stops"]):
        if len(fields) != 5:
            raise ValueError(f"Expected 5 columns in {paths['stops']}:{line_number}")
        stops.append(int(fields[0]))
    if len(stops) != len(set(stops)):
        raise ValueError("Duplicate stop ids")
    stop_set = set(stops)

    edges: Dict[int, Edge] = {}
    for line_number, fields in _rows(paths["edges"]):
        if len(fields) != 6:
            raise ValueError(f"Expected 6 columns in {paths['edges']}:{line_number}")
        length, lower_time, upper_time = map(float, fields[3:6])
        if lower_time > upper_time:
            raise ValueError(f"Edge {fields[0]} has lower time above upper time")
        travel_times = {
            "AVERAGE_DRIVING_TIME": (lower_time + upper_time) / 2.0,
            "MINIMAL_DRIVING_TIME": lower_time,
            "MAXIMAL_DRIVING_TIME": upper_time,
            "EDGE_LENGTH": length,
        }
        edge = Edge(
            int(fields[0]),
            int(fields[1]),
            int(fields[2]),
            length,
            lower_time,
            upper_time,
            travel_times[drive_model],
        )
        if edge.edge_id in edges:
            raise ValueError(f"Duplicate edge id {edge.edge_id}")
        if edge.left not in stop_set or edge.right not in stop_set:
            raise ValueError(f"Edge {edge.edge_id} references an unknown stop")
        if (
            edge.left == edge.right
            or edge.length < 0
            or edge.lower_time < 0
            or edge.upper_time < 0
            or edge.travel_time < 0
        ):
            raise ValueError(f"Invalid edge {edge}")
        edges[edge.edge_id] = edge

    ordered_edges: Dict[int, Dict[int, int]] = defaultdict(dict)
    for line_number, fields in _rows(paths["pool"]):
        if len(fields) != 3:
            raise ValueError(f"Expected 3 columns in {paths['pool']}:{line_number}")
        line_id, order, edge_id = map(int, fields)
        if edge_id not in edges:
            raise ValueError(f"Line {line_id} references unknown edge {edge_id}")
        if order in ordered_edges[line_id]:
            raise ValueError(f"Line {line_id} repeats edge order {order}")
        ordered_edges[line_id][order] = edge_id

    costs: Dict[int, float] = {}
    for line_number, fields in _rows(paths["costs"]):
        if len(fields) != 3:
            raise ValueError(f"Expected 3 columns in {paths['costs']}:{line_number}")
        costs[int(fields[0])] = float(fields[2])

    lines: Dict[int, Line] = {}
    for line_id in sorted(ordered_edges):
        positions = ordered_edges[line_id]
        expected = list(range(1, len(positions) + 1))
        if sorted(positions) != expected:
            raise ValueError(f"Line {line_id} has non-contiguous edge order")
        if line_id not in costs:
            raise ValueError(f"Line {line_id} has no cost row")
        edge_ids = tuple(positions[position] for position in expected)
        lines[line_id] = Line(
            line_id,
            edge_ids,
            _order_line(line_id, edge_ids, edges, bool(directed)),
            costs[line_id],
        )
    unknown_costs = set(costs) - set(lines)
    if unknown_costs:
        raise ValueError(f"Costs reference unknown lines: {sorted(unknown_costs)}")

    demand: Dict[Tuple[int, int], float] = {}
    for line_number, fields in _rows(paths["od"]):
        if len(fields) != 3:
            raise ValueError(f"Expected 3 columns in {paths['od']}:{line_number}")
        origin, destination, value = int(fields[0]), int(fields[1]), float(fields[2])
        if origin not in stop_set or destination not in stop_set:
            raise ValueError(f"OD row {line_number} references an unknown stop")
        if value < 0:
            raise ValueError(f"Negative demand in {paths['od']}:{line_number}")
        if value > 0:
            if origin == destination:
                raise ValueError(f"Positive self-demand at stop {origin}")
            demand[(origin, destination)] = demand.get((origin, destination), 0.0) + value

    metadata_path = basis / "Generation-Metadata.json"
    provenance: Dict[str, object] = {
        "dataset_path": str(root.resolve()),
        "travel_time_model": drive_model,
        "files": {name: {"path": str(path), "sha256": _sha256(path)} for name, path in paths.items()},
    }
    if metadata_path.is_file():
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        provenance["generation_metadata"] = metadata
        provenance["generation_metadata_sha256"] = _sha256(metadata_path)
        source_name = metadata.get("od_source")
        if isinstance(source_name, str):
            source_path = root.resolve().parents[1] / source_name
            if source_path.is_file():
                all_rows = 0
                positive_rows = 0
                positive_total = 0.0
                self_pairs = 0
                for _, fields in _rows(source_path):
                    if len(fields) != 3:
                        raise ValueError(f"Expected 3 columns in source OD file {source_path}")
                    all_rows += 1
                    origin, destination, value = int(fields[0]), int(fields[1]), float(fields[2])
                    if value > 0:
                        positive_rows += 1
                        positive_total += value
                        self_pairs += int(origin == destination)
                provenance["source_od_file"] = {
                    "path": str(source_path),
                    "sha256": _sha256(source_path),
                    "rows": all_rows,
                    "positive_rows": positive_rows,
                    "positive_total": positive_total,
                    "positive_self_pairs": self_pairs,
                }

    return LinTimDataset(
        root=root,
        stops=tuple(sorted(stops)),
        edges=edges,
        lines=lines,
        demand=demand,
        directed=bool(directed),
        provenance=provenance,
    )


def audit_dataset(dataset: LinTimDataset) -> Dict[str, object]:
    degrees: Dict[int, int] = {stop: 0 for stop in dataset.stops}
    for edge in dataset.edges.values():
        degrees[edge.left] += 1
        degrees[edge.right] += 1
    served_edges = {edge_id for line in dataset.lines.values() for edge_id in line.edge_ids}
    demand_values = list(dataset.demand.values())
    return {
        "dataset_path": str(dataset.root.resolve()),
        "directed": dataset.directed,
        "stops": len(dataset.stops),
        "edges": len(dataset.edges),
        "lines": len(dataset.lines),
        "line_edge_rows": sum(len(line.edge_ids) for line in dataset.lines.values()),
        "positive_od_pairs": len(dataset.demand),
        "positive_self_pairs": sum(1 for o, d in dataset.demand if o == d),
        "total_demand": sum(demand_values),
        "minimum_positive_demand": min(demand_values) if demand_values else None,
        "maximum_demand": max(demand_values) if demand_values else None,
        "isolated_stops": sorted(stop for stop, degree in degrees.items() if degree == 0),
        "unserved_edges": sorted(set(dataset.edges) - served_edges),
        "all_line_paths_simple": all(
            len(line.stop_ids) == len(set(line.stop_ids)) for line in dataset.lines.values()
        ),
        "provenance": dataset.provenance,
    }

