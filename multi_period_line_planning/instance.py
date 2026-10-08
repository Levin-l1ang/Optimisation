"""LinTim input handling and deterministic multi-period demand generation."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np


def _rows(path: Path) -> Iterable[List[str]]:
    with path.open(encoding="utf-8") as stream:
        for line_number, raw in enumerate(stream, 1):
            text = raw.strip()
            if not text or text.startswith("#"):
                continue
            fields = [field.strip().strip('"') for field in text.split(";")]
            if any(field == "" for field in fields):
                raise ValueError(f"Empty field in {path}:{line_number}")
            yield fields


@dataclass(frozen=True)
class LinePlanningInstance:
    """A cost line-planning instance with one bound vector per demand period."""

    edge_ids: Tuple[int, ...]
    edge_lengths: Tuple[float, ...]
    line_ids: Tuple[int, ...]
    line_edges: Tuple[frozenset, ...]
    line_costs: Tuple[float, ...]
    lower_bounds: Tuple[Tuple[int, ...], ...]
    upper_bounds: Tuple[Tuple[int, ...], ...]
    source: str = ""
    demand_description: str = ""

    def __post_init__(self) -> None:
        edge_count = len(self.edge_ids)
        line_count = len(self.line_ids)
        if not self.lower_bounds:
            raise ValueError("At least one demand period is required")
        if len(self.edge_lengths) != edge_count:
            raise ValueError("edge_ids and edge_lengths have different sizes")
        if len(self.line_edges) != line_count or len(self.line_costs) != line_count:
            raise ValueError("Line identifiers, paths and costs have different sizes")
        if len(self.lower_bounds) != len(self.upper_bounds):
            raise ValueError("Lower and upper bounds have different period counts")
        known_edges = set(self.edge_ids)
        for line_id, edges in zip(self.line_ids, self.line_edges):
            if not edges:
                raise ValueError(f"Line {line_id} has no edges")
            if not set(edges) <= known_edges:
                raise ValueError(f"Line {line_id} refers to an unknown edge")
        for period, (lower, upper) in enumerate(
            zip(self.lower_bounds, self.upper_bounds), 1
        ):
            if len(lower) != edge_count or len(upper) != edge_count:
                raise ValueError(f"Period {period} has the wrong number of edges")
            if any(lo < 0 or hi < lo for lo, hi in zip(lower, upper)):
                raise ValueError(f"Invalid frequency bounds in period {period}")

    @property
    def period_count(self) -> int:
        return len(self.lower_bounds)

    @property
    def edge_count(self) -> int:
        return len(self.edge_ids)

    @property
    def line_count(self) -> int:
        return len(self.line_ids)

    @property
    def max_frequency(self) -> int:
        return max(max(period) for period in self.upper_bounds)

    @property
    def incidence(self) -> np.ndarray:
        matrix = np.zeros((self.edge_count, self.line_count), dtype=float)
        edge_index = {edge_id: index for index, edge_id in enumerate(self.edge_ids)}
        for line_index, edges in enumerate(self.line_edges):
            for edge_id in edges:
                matrix[edge_index[edge_id], line_index] = 1.0
        return matrix

    @property
    def line_distance_matrix(self) -> np.ndarray:
        """Weighted Jaccard distance in Sect. 3.3 of the paper."""

        lengths = dict(zip(self.edge_ids, self.edge_lengths))
        distances = np.zeros((self.line_count, self.line_count), dtype=float)
        for first in range(self.line_count):
            for second in range(first + 1, self.line_count):
                left = self.line_edges[first]
                right = self.line_edges[second]
                union = left | right
                symmetric_difference = left ^ right
                denominator = sum(lengths[edge] for edge in union)
                value = (
                    sum(lengths[edge] for edge in symmetric_difference) / denominator
                    if denominator
                    else 0.0
                )
                distances[first, second] = distances[second, first] = value
        return distances

    def periods(self, indices: Sequence[int]) -> "LinePlanningInstance":
        return LinePlanningInstance(
            edge_ids=self.edge_ids,
            edge_lengths=self.edge_lengths,
            line_ids=self.line_ids,
            line_edges=self.line_edges,
            line_costs=self.line_costs,
            lower_bounds=tuple(self.lower_bounds[index] for index in indices),
            upper_bounds=tuple(self.upper_bounds[index] for index in indices),
            source=self.source,
            demand_description=self.demand_description,
        )


def _read_load(path: Path, edge_ids: Sequence[int]) -> Tuple[Tuple[int, ...], Tuple[int, ...]]:
    values: Dict[int, Tuple[int, int]] = {}
    for fields in _rows(path):
        if len(fields) < 4:
            raise ValueError(f"Expected four columns in {path}")
        edge_id = int(fields[0])
        values[edge_id] = (int(round(float(fields[2]))), int(round(float(fields[3]))))
    missing = set(edge_ids) - set(values)
    if missing:
        raise ValueError(f"Missing load bounds for edges {sorted(missing)} in {path}")
    lower = tuple(values[edge][0] for edge in edge_ids)
    upper = tuple(values[edge][1] for edge in edge_ids)
    return lower, upper


def _generate_monotone_periods(
    highest_lower: Sequence[int],
    highest_upper: Sequence[int],
    periods: int,
    seed: int,
) -> Tuple[Tuple[Tuple[int, ...], ...], Tuple[Tuple[int, ...], ...]]:
    """Create reproducible monotone bounds when the paper's private inputs are absent."""

    if periods < 1:
        raise ValueError("periods must be positive")
    if periods == 1:
        return (tuple(highest_lower),), (tuple(highest_upper),)
    generator = np.random.default_rng(seed)
    lower_start = generator.uniform(0.35, 0.65, len(highest_lower))
    lower_periods: List[Tuple[int, ...]] = []
    upper_periods: List[Tuple[int, ...]] = []
    for period in range(periods):
        progress = period / (periods - 1)
        lower = np.ceil(
            np.asarray(highest_lower)
            * (lower_start + (1.0 - lower_start) * progress)
        ).astype(int)
        # Demand changes between periods, while the operational/infrastructure
        # capacity remains fixed. Keeping the upper bounds constant also
        # guarantees that the paper's identical-concept benchmark is feasible
        # whenever the highest-demand single-period problem is feasible.
        upper = np.asarray(highest_upper, dtype=int)
        upper = np.maximum(upper, lower)
        lower_periods.append(tuple(int(value) for value in lower))
        upper_periods.append(tuple(int(value) for value in upper))
    return tuple(lower_periods), tuple(upper_periods)


def load_lintim_instance(
    dataset: Path,
    load_files: Optional[Sequence[Path]] = None,
    periods: int = 2,
    seed: int = 2023,
) -> LinePlanningInstance:
    """Read a LinTim dataset and optional per-period ``Load.giv`` files.

    If no per-period files are supplied, the dataset's normal ``Load.giv`` is used as
    the highest-demand period and lower monotone periods are generated deterministically.
    This is necessary because the paper does not publish its random demand vectors.
    """

    dataset_input = Path(dataset)
    dataset = dataset_input.resolve()
    basis = dataset / "basis"
    edge_path = basis / "Edge.giv"
    pool_path = basis / "Pool.giv"
    cost_path = basis / "Pool-Cost.giv"
    default_load_path = basis / "Load.giv"
    for required in (edge_path, pool_path, cost_path):
        if not required.is_file():
            raise FileNotFoundError(required)

    edge_ids: List[int] = []
    edge_lengths: List[float] = []
    for fields in _rows(edge_path):
        if len(fields) < 4:
            raise ValueError(f"Expected at least four columns in {edge_path}")
        edge_ids.append(int(fields[0]))
        edge_lengths.append(float(fields[3]))

    ordered_line_edges: Dict[int, List[Tuple[int, int]]] = {}
    for fields in _rows(pool_path):
        if len(fields) < 3:
            raise ValueError(f"Expected three columns in {pool_path}")
        line_id, order, edge_id = (int(fields[0]), int(fields[1]), int(fields[2]))
        ordered_line_edges.setdefault(line_id, []).append((order, edge_id))

    costs: Dict[int, float] = {}
    for fields in _rows(cost_path):
        if len(fields) < 3:
            raise ValueError(f"Expected three columns in {cost_path}")
        costs[int(fields[0])] = float(fields[2])
    line_ids = tuple(sorted(ordered_line_edges))
    missing_costs = set(line_ids) - set(costs)
    if missing_costs:
        raise ValueError(f"Missing costs for lines {sorted(missing_costs)}")
    line_edges = tuple(
        frozenset(edge for _, edge in sorted(ordered_line_edges[line_id]))
        for line_id in line_ids
    )
    line_costs = tuple(costs[line_id] for line_id in line_ids)

    if load_files:
        display_loads = [Path(path) for path in load_files]
        resolved_loads = [path.resolve() for path in display_loads]
        bounds = [_read_load(path, edge_ids) for path in resolved_loads]
        lower_bounds = tuple(bound[0] for bound in bounds)
        upper_bounds = tuple(bound[1] for bound in bounds)
        demand_description = "Explicit per-period Load.giv inputs: " + ", ".join(
            str(path) for path in display_loads
        )
    else:
        if not default_load_path.is_file():
            raise FileNotFoundError(default_load_path)
        highest_lower, highest_upper = _read_load(default_load_path, edge_ids)
        lower_bounds, upper_bounds = _generate_monotone_periods(
            highest_lower, highest_upper, periods, seed
        )
        demand_description = (
            f"Deterministic monotone demand generated with seed {seed}; final period "
            f"equals {dataset_input / 'basis' / 'Load.giv'}."
        )

    return LinePlanningInstance(
        edge_ids=tuple(edge_ids),
        edge_lengths=tuple(edge_lengths),
        line_ids=line_ids,
        line_edges=line_edges,
        line_costs=line_costs,
        lower_bounds=lower_bounds,
        upper_bounds=upper_bounds,
        source=str(dataset),
        demand_description=demand_description,
    )
