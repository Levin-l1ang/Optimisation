"""Path-based, multi-period passenger-oriented line planning.

This package is intentionally separate from :mod:`multi_period_line_planning`,
which implements the operating-cost formulations from Schiewe et al. (2023).
"""

from .domain import Edge, Itinerary, Leg, Line, LinTimDataset, Trip
from .lintim_io import audit_dataset, load_lintim_dataset
from .model import ModelConfig, PassengerModelData, estimate_model_size, solve
from .passenger_paths import RoutingConfig, route_demands
from .trip_expansion import TripExpansionConfig, expand_itinerary

__all__ = [
    "Edge",
    "Itinerary",
    "Leg",
    "Line",
    "LinTimDataset",
    "ModelConfig",
    "PassengerModelData",
    "RoutingConfig",
    "Trip",
    "TripExpansionConfig",
    "audit_dataset",
    "estimate_model_size",
    "expand_itinerary",
    "load_lintim_dataset",
    "route_demands",
    "solve",
]
