"""Immutable domain objects used by the preprocessing and MILP layers."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Tuple


@dataclass(frozen=True)
class Edge:
    edge_id: int
    left: int
    right: int
    length: float
    lower_time: float
    upper_time: float
    travel_time: float


@dataclass(frozen=True)
class Line:
    line_id: int
    edge_ids: Tuple[int, ...]
    stop_ids: Tuple[int, ...]
    base_cost: float


@dataclass(frozen=True)
class LinTimDataset:
    root: Path
    stops: Tuple[int, ...]
    edges: Mapping[int, Edge]
    lines: Mapping[int, Line]
    demand: Mapping[Tuple[int, int], float]
    directed: bool
    provenance: Mapping[str, object]


@dataclass(frozen=True)
class Leg:
    """One uninterrupted ride on one line.

    ``arcs`` stores ``(edge_id, from_stop, to_stop)`` so capacity can be
    imposed separately by service and travel direction.
    """

    line_id: int
    board_stop: int
    alight_stop: int
    arcs: Tuple[Tuple[int, int, int], ...]


@dataclass(frozen=True)
class Itinerary:
    origin: int
    destination: int
    legs: Tuple[Leg, ...]
    ride_time: float

    @property
    def transfers(self) -> int:
        return max(0, len(self.legs) - 1)

    @property
    def signature(self) -> Tuple[Tuple[int, int, int], ...]:
        return tuple(
            (leg.line_id, leg.board_stop, leg.alight_stop) for leg in self.legs
        )


@dataclass(frozen=True)
class Trip:
    """A frequency-specific passenger trip used by the MILP."""

    origin: int
    destination: int
    trip_id: str
    services: Tuple[Tuple[int, int], ...]
    service_arcs: Tuple[Tuple[int, int, int, int, int], ...]
    generalized_time: float
    ride_time: float
    waiting_time: float
    walking_time: float
    transfers: int

