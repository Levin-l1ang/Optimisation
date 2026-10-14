"""Immutable domain objects for the 2026 benchmark migration."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Mapping, Tuple

# Reference constants (LinePlanningParameters.java at the pinned commit).
PERIOD_MINUTES = 60
DWELL_TIME = 2
TURNAROUND_TIME = 30
CAPACITY = 1500
MISSED_DEMAND_PENALTY_FACTOR = 3.0

SPRINTER, TWIJFEL, INTERCITY = 0, 1, 2


@dataclass(frozen=True)
class Station:
    index: int
    name: str
    full_name: str
    terminal_ic: bool
    terminal_spr: bool
    station_type: int
    in_randstad: bool


@dataclass(frozen=True)
class Track:
    track_id: int
    from_station: str
    to_station: str
    length: float
    speed: int
    travel_time: float
    num_tracks: int


@dataclass(frozen=True)
class Connection:
    """Directed connection between adjacent stations (``connections.txt``)."""

    connection_id: int
    from_station: str
    to_station: str
    length: float
    travel_time: float
    max_frequency: int
    train_type: int
    reverse_id: int
    track_ids: Tuple[int, ...]


@dataclass(frozen=True)
class ODPair:
    origin: str
    destination: str
    volume: float

    @property
    def key(self) -> Tuple[str, str]:
        return (self.origin, self.destination)


@dataclass(frozen=True)
class InstanceParameters:
    """Scalar parameters selected by the primary configuration."""

    line_detour_ratio: float
    line_max_duration: float
    route_max_detour: int
    transfer_penalty: int
    fixed_cost: float
    budget: float
    has_ics: bool
    has_sprs: bool


@dataclass(frozen=True)
class BenchmarkInstance:
    root: str
    stations: Mapping[str, Station]
    active_stations: Tuple[str, ...]
    tracks: Mapping[int, Track]
    connections: Mapping[int, Connection]
    forbidden_turns: frozenset
    allowed_type_changes: frozenset
    od_pairs: Tuple[ODPair, ...]
    parameters: InstanceParameters
    file_hashes: Mapping[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class CandidateLine:
    """A frozen candidate line.

    ``connections_forward`` is the service in the generated direction; the
    backward service uses the reverse connections in reverse order.
    """

    line_id: int
    stations: Tuple[str, ...]
    connections_forward: Tuple[int, ...]
    connections_backward: Tuple[int, ...]
    train_type: int
    duration: float
    cost_per_frequency: float  # c^V = (2*duration + 2*30) / 60
    max_frequency: int
    capacity: int


@dataclass(frozen=True)
class Leg:
    """Uninterrupted ride on one line; ``arcs`` are (connection_id, from, to)."""

    line_id: int
    board_station: str
    alight_station: str
    arcs: Tuple[Tuple[int, str, str], ...]


@dataclass(frozen=True)
class Itinerary:
    origin: str
    destination: str
    legs: Tuple[Leg, ...]
    ride_time: float  # driving + dwell, excludes transfer penalties

    @property
    def transfers(self) -> int:
        return max(0, len(self.legs) - 1)

    @property
    def signature(self) -> tuple:
        return tuple((leg.line_id, leg.board_station, leg.alight_station) for leg in self.legs)


@dataclass(frozen=True)
class Trip:
    """Frequency-specific passenger trip used by the MILP (c_p precomputed)."""

    origin: str
    destination: str
    trip_id: str
    services: Tuple[Tuple[int, int], ...]
    # (line_id, frequency, connection_id, from_station, to_station); the directed
    # connection id already encodes the service direction.
    service_arcs: Tuple[Tuple[int, int, int, str, str], ...]
    generalized_time: float
    ride_time: float
    waiting_time: float
    transfer_time: float
    transfers: int
