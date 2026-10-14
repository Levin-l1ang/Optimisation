"""Expand itineraries into frequency-specific trips (frequency-dependent waiting)."""

from __future__ import annotations

import hashlib
import itertools
from dataclasses import dataclass
from typing import Mapping, Sequence, Tuple

from .domain import Itinerary, Trip


@dataclass(frozen=True)
class TripExpansionConfig:
    """``frequencies[line] = allowed positive frequencies``; waiting = period/(2 f) per leg."""

    frequencies: Mapping[int, Tuple[int, ...]]
    transfer_penalty: float  # required: 15 min for the primary configuration
    period_length: float = 60.0

    def __post_init__(self) -> None:
        if self.period_length <= 0 or self.transfer_penalty < 0:
            raise ValueError("invalid generalized-time parameters")
        for line_id, values in self.frequencies.items():
            if not values or any(v <= 0 for v in values) or len(values) != len(set(values)):
                raise ValueError(f"line {line_id}: needs distinct positive frequencies")


def expansion_count(itinerary: Itinerary, config: TripExpansionConfig) -> int:
    count = 1
    for leg in itinerary.legs:
        count *= len(config.frequencies[leg.line_id])
    return count


def expand_itinerary(itinerary: Itinerary, config: TripExpansionConfig) -> Tuple[Trip, ...]:
    options: Sequence[Tuple[int, ...]] = tuple(config.frequencies[leg.line_id] for leg in itinerary.legs)
    transfer_time = config.transfer_penalty * itinerary.transfers
    trips = []
    for choice in itertools.product(*options):
        waiting = sum(config.period_length / (2.0 * f) for f in choice)
        service_arcs = tuple(
            (leg.line_id, f, cid, a, b) for leg, f in zip(itinerary.legs, choice) for cid, a, b in leg.arcs
        )
        raw = repr((itinerary.origin, itinerary.destination, itinerary.signature, choice))
        trips.append(Trip(
            itinerary.origin, itinerary.destination, hashlib.sha256(raw.encode()).hexdigest()[:16],
            tuple((leg.line_id, f) for leg, f in zip(itinerary.legs, choice)), service_arcs,
            itinerary.ride_time + waiting + transfer_time, itinerary.ride_time, waiting,
            transfer_time, itinerary.transfers,
        ))
    return tuple(trips)
