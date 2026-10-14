"""Expand physical passenger itineraries into frequency-specific MILP trips."""

from __future__ import annotations

import hashlib
import itertools
from dataclasses import dataclass
from typing import Mapping, Sequence, Tuple

from .domain import Itinerary, Trip


@dataclass(frozen=True)
class TripExpansionConfig:
    frequencies: Mapping[int, Tuple[int, ...]]
    period_length: float = 60.0
    alpha: float = 1.0
    beta: float = 5.0
    walking_time: float = 0.0

    def __post_init__(self) -> None:
        if self.period_length <= 0 or self.alpha < 0 or self.beta < 0:
            raise ValueError("Invalid generalized-time parameters")
        for line_id, values in self.frequencies.items():
            if not values or any(value <= 0 for value in values):
                raise ValueError(f"Line {line_id} needs positive frequency options")
            if len(values) != len(set(values)):
                raise ValueError(f"Line {line_id} has duplicate frequencies")


def expansion_count(itinerary: Itinerary, config: TripExpansionConfig) -> int:
    count = 1
    for leg in itinerary.legs:
        count *= len(config.frequencies[leg.line_id])
    return count


def expand_itinerary(
    itinerary: Itinerary, config: TripExpansionConfig
) -> Tuple[Trip, ...]:
    frequency_sets: Sequence[Tuple[int, ...]] = tuple(
        config.frequencies[leg.line_id] for leg in itinerary.legs
    )
    trips = []
    for choice in itertools.product(*frequency_sets):
        services = tuple(
            (leg.line_id, frequency)
            for leg, frequency in zip(itinerary.legs, choice)
        )
        waiting = sum(config.period_length / (2.0 * frequency) for frequency in choice)
        generalized = (
            itinerary.ride_time
            + waiting
            + config.alpha * config.walking_time
            + config.beta * itinerary.transfers
        )
        service_arcs = tuple(
            (leg.line_id, frequency, edge_id, left, right)
            for leg, frequency in zip(itinerary.legs, choice)
            for edge_id, left, right in leg.arcs
        )
        raw_id = repr((itinerary.origin, itinerary.destination, itinerary.signature, choice))
        trip_id = hashlib.sha256(raw_id.encode("utf-8")).hexdigest()[:16]
        trips.append(
            Trip(
                itinerary.origin,
                itinerary.destination,
                trip_id,
                services,
                service_arcs,
                generalized,
                itinerary.ride_time,
                waiting,
                config.walking_time,
                itinerary.transfers,
            )
        )
    return tuple(trips)

