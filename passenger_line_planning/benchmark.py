"""Auditing, preprocessing export, and model-size profiling utilities."""

from __future__ import annotations

import gzip
import json
from dataclasses import asdict
from pathlib import Path
from time import perf_counter
from typing import Dict, Optional, TextIO

from .domain import Itinerary, LinTimDataset
from .lintim_io import audit_dataset
from .passenger_paths import RoutingConfig, route_demands
from .trip_expansion import TripExpansionConfig, expansion_count


def _itinerary_record(
    pair, demand: float, itinerary: Itinerary, index: int
) -> Dict[str, object]:
    return {
        "origin": pair[0],
        "destination": pair[1],
        "demand": demand,
        "itinerary_index": index,
        "ride_time": itinerary.ride_time,
        "transfers": itinerary.transfers,
        "legs": [
            {
                "line_id": leg.line_id,
                "board_stop": leg.board_stop,
                "alight_stop": leg.alight_stop,
                "arcs": [list(arc) for arc in leg.arcs],
            }
            for leg in itinerary.legs
        ],
    }


def profile_preprocessing(
    dataset: LinTimDataset,
    routing: RoutingConfig,
    expansion: TripExpansionConfig,
    periods: int = 2,
    output_jsonl_gz: Optional[Path] = None,
) -> Dict[str, object]:
    """Route every positive OD and count the exact expansion without expanding it."""

    if periods <= 0:
        raise ValueError("periods must be positive")
    started = perf_counter()
    positive_pairs = 0
    covered_pairs = 0
    total_demand = 0.0
    uncovered_demand = 0.0
    physical_itineraries = 0
    expanded_trips = 0
    service_requirements = 0
    capacity_flow_coefficients = 0
    capacity_keys = set()
    maximum_expansion = 0
    transfer_histogram: Dict[int, int] = {}
    writer: Optional[TextIO] = None
    if output_jsonl_gz is not None:
        output_jsonl_gz = Path(output_jsonl_gz)
        output_jsonl_gz.parent.mkdir(parents=True, exist_ok=True)
        writer = gzip.open(output_jsonl_gz, "wt", encoding="utf-8")
    try:
        for pair, demand, itineraries in route_demands(dataset, routing):
            positive_pairs += 1
            total_demand += demand
            if not itineraries:
                uncovered_demand += demand
                continue
            covered_pairs += 1
            for index, itinerary in enumerate(itineraries):
                physical_itineraries += 1
                transfer_histogram[itinerary.transfers] = (
                    transfer_histogram.get(itinerary.transfers, 0) + 1
                )
                count = expansion_count(itinerary, expansion)
                maximum_expansion = max(maximum_expansion, count)
                expanded_trips += count
                service_requirements += count * len(itinerary.legs)
                capacity_flow_coefficients += count * sum(
                    len(leg.arcs) for leg in itinerary.legs
                )
                for leg in itinerary.legs:
                    for frequency in expansion.frequencies[leg.line_id]:
                        for edge_id, left, right in leg.arcs:
                            capacity_keys.add(
                                (leg.line_id, frequency, edge_id, left, right)
                            )
                if writer is not None:
                    writer.write(
                        json.dumps(
                            _itinerary_record(pair, demand, itinerary, index),
                            sort_keys=True,
                        )
                        + "\n"
                    )
    finally:
        if writer is not None:
            writer.close()

    lines = len(dataset.lines)
    frequency_variables_per_period = sum(
        len(expansion.frequencies[line_id]) for line_id in dataset.lines
    )
    binary_variables = periods * frequency_variables_per_period
    flow_variables = periods * expanded_trips
    delta_variables = max(0, periods - 1) * lines
    selection_constraints = periods * lines
    demand_constraints = periods * positive_pairs
    activation_constraints = periods * service_requirements
    capacity_constraints = periods * len(capacity_keys)
    budget_constraints = periods
    similarity_constraints = max(0, periods - 1) * (1 + 2 * lines)
    constraints = (
        selection_constraints
        + demand_constraints
        + activation_constraints
        + capacity_constraints
        + budget_constraints
        + similarity_constraints
    )
    similarity_nonzeros = max(0, periods - 1) * (
        lines
        + 2
        * sum(
            2 * len(expansion.frequencies[line_id]) + 1
            for line_id in dataset.lines
        )
    )
    nonzeros = (
        binary_variables  # selection
        + flow_variables  # demand
        + 2 * activation_constraints
        + periods * capacity_flow_coefficients
        + capacity_constraints  # one z in each capacity row
        + binary_variables  # budget
        + similarity_nonzeros
    )
    return {
        "status": "ROUTING_COMPLETE" if covered_pairs == positive_pairs else "MISSING_OD_ROUTES",
        "dataset": audit_dataset(dataset),
        "routing_config": asdict(routing),
        "trip_expansion_config": {
            "period_length": expansion.period_length,
            "alpha": expansion.alpha,
            "beta": expansion.beta,
            "walking_time": expansion.walking_time,
            "frequency_options": {
                str(line): list(values)
                for line, values in expansion.frequencies.items()
            },
        },
        "runtime_seconds": perf_counter() - started,
        "positive_od_pairs": positive_pairs,
        "covered_od_pairs": covered_pairs,
        "uncovered_od_pairs": positive_pairs - covered_pairs,
        "total_demand": total_demand,
        "uncovered_demand": uncovered_demand,
        "physical_itineraries": physical_itineraries,
        "frequency_specific_trips": expanded_trips,
        "maximum_trips_from_one_itinerary": maximum_expansion,
        "transfer_histogram": {
            str(key): value for key, value in sorted(transfer_histogram.items())
        },
        "model_size": {
            "periods": periods,
            "binary_z_variables": binary_variables,
            "continuous_x_variables": flow_variables,
            "continuous_delta_variables": delta_variables,
            "total_variables": binary_variables + flow_variables + delta_variables,
            "constraints": constraints,
            "activation_constraints": activation_constraints,
            "capacity_constraints": capacity_constraints,
            "estimated_sparse_nonzeros": nonzeros,
        },
        "output_itineraries": str(output_jsonl_gz) if output_jsonl_gz else None,
    }


def write_audit_reports(
    dataset: LinTimDataset, json_path: Path, markdown_path: Path
) -> Dict[str, object]:
    report = audit_dataset(dataset)
    json_path = Path(json_path)
    markdown_path = Path(markdown_path)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    markdown_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    generation = report["provenance"].get("generation_metadata", {})
    identity = generation.get("od_identity", "not recorded")
    network_source = generation.get("network_source", "not recorded")
    pool_algorithm = generation.get("pool_algorithm")
    if pool_algorithm:
        identity_conclusion = (
            "This is a reconstructed research instance, not the unpublished original "
            f"`bahn-01` basis directory. Its PTN source is recorded above and its "
            f"{report['lines']}-line candidate pool was generated with: "
            f"`{pool_algorithm}`. Results are valid only for these audited files."
        )
    else:
        identity_conclusion = (
            "No reconstruction metadata was found. File hashes and structural checks "
            "are recorded, but benchmark identity must be established separately."
        )
    markdown_path.write_text(
        "\n".join(
            [
                "# Dataset audit",
                "",
                f"- Dataset path: `{report['dataset_path']}`",
                f"- Stops / edges / lines: {report['stops']} / {report['edges']} / {report['lines']}",
                f"- Positive OD pairs: {report['positive_od_pairs']}",
                f"- Total positive demand: {report['total_demand']}",
                f"- Directed: {report['directed']}",
                f"- Unserved PTN edges: {len(report['unserved_edges'])}",
                f"- Recorded network source: {network_source}",
                f"- Recorded OD identity: {identity}",
                "",
                "## Benchmark identity conclusion",
                "",
                identity_conclusion,
                "",
                "Exact SHA-256 values and machine-readable checks are in the adjacent JSON report.",
                "",
            ]
        ),
        encoding="utf-8",
    )
    return report

