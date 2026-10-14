"""Command line entry points for the passenger-oriented pipeline."""

from __future__ import annotations

import argparse
import json
from dataclasses import asdict
from pathlib import Path
from typing import Dict, Iterable, Tuple

from .benchmark import profile_preprocessing, write_audit_reports
from .lintim_io import load_lintim_dataset
from .model import ModelConfig, PassengerModelData, solve
from .passenger_paths import RoutingConfig, route_demands
from .trip_expansion import TripExpansionConfig, expand_itinerary


def _numbers(text: str, cast=float) -> Tuple:
    values = tuple(cast(value.strip()) for value in text.split(",") if value.strip())
    if not values:
        raise argparse.ArgumentTypeError("Expected a comma-separated nonempty list")
    return values


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Path-based multi-period passenger line planning"
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    audit = subparsers.add_parser("audit", help="Validate LinTim input files")
    audit.add_argument("--dataset", type=Path, required=True)
    audit.add_argument("--json", type=Path, default=Path("reports/dataset_audit.json"))
    audit.add_argument("--markdown", type=Path, default=Path("reports/dataset_audit.md"))

    profile = subparsers.add_parser(
        "preprocess-full", help="Route every positive OD and report model scale"
    )
    profile.add_argument("--dataset", type=Path, required=True)
    profile.add_argument("--output", type=Path, required=True)
    profile.add_argument("--itineraries", type=Path)
    profile.add_argument("--frequencies", default="1,2,3,4")
    profile.add_argument("--periods", type=int, default=2)
    profile.add_argument("--period-length", type=float, default=60.0)
    profile.add_argument("--k-ptn-paths", type=int, default=1)
    profile.add_argument("--k-itineraries", type=int, default=3)
    profile.add_argument("--max-transfers", type=int, default=2)
    profile.add_argument("--transfer-penalty", type=float, default=5.0)

    solve_parser = subparsers.add_parser(
        "solve", help="Materialize and solve a resource-bounded instance"
    )
    solve_parser.add_argument("--dataset", type=Path, required=True)
    solve_parser.add_argument("--output", type=Path, required=True)
    solve_parser.add_argument("--frequencies", default="1,2")
    solve_parser.add_argument("--demand-scales", default="0.5,1.0")
    solve_parser.add_argument("--budgets", required=True)
    solve_parser.add_argument("--capacity", type=float, required=True)
    solve_parser.add_argument("--similarity-limit", type=float, required=True)
    solve_parser.add_argument("--period-length", type=float, default=60.0)
    solve_parser.add_argument("--beta", type=float, default=5.0)
    solve_parser.add_argument("--k-ptn-paths", type=int, default=1)
    solve_parser.add_argument("--k-itineraries", type=int, default=3)
    solve_parser.add_argument("--max-transfers", type=int, default=2)
    solve_parser.add_argument("--time-limit", type=float, default=60.0)
    solve_parser.add_argument("--mip-gap", type=float, default=0.0)
    solve_parser.add_argument(
        "--max-expanded-trips",
        type=int,
        default=250_000,
        help="Hard guard before MILP materialization",
    )
    return parser


def _frequency_map(line_ids: Iterable[int], raw: str) -> Dict[int, Tuple[int, ...]]:
    values = tuple(sorted(set(_numbers(raw, int))))
    if any(value <= 0 for value in values):
        raise ValueError("Frequencies must be positive")
    return {line_id: values for line_id in line_ids}


def _result_json(result) -> Dict[str, object]:
    return {
        "status": result.status,
        "objective": result.objective,
        "runtime_seconds": result.runtime_seconds,
        "mip_gap": result.mip_gap,
        "best_bound": result.best_bound,
        "message": result.message,
        "model_size": asdict(result.model_size),
        "selected_frequencies": [
            {str(line): frequency for line, frequency in period.items()}
            for period in result.selected_frequencies
        ],
        "passenger_flows": [
            [
                {
                    "origin": pair[0],
                    "destination": pair[1],
                    "trip_id": trip_id,
                    "flow": value,
                }
                for (pair, trip_id), value in period.items()
            ]
            for period in result.passenger_flows
        ],
        "metadata": dict(result.metadata),
    }


def main() -> None:
    arguments = _parser().parse_args()
    dataset = load_lintim_dataset(arguments.dataset)

    if arguments.command == "audit":
        report = write_audit_reports(dataset, arguments.json, arguments.markdown)
        print(json.dumps({key: report[key] for key in ("stops", "edges", "lines", "positive_od_pairs", "total_demand")}, indent=2))
        return

    frequencies = _frequency_map(dataset.lines, arguments.frequencies)
    routing = RoutingConfig(
        k_ptn_paths=arguments.k_ptn_paths,
        k_itineraries=arguments.k_itineraries,
        max_transfers=arguments.max_transfers,
        transfer_penalty=getattr(arguments, "transfer_penalty", arguments.beta if arguments.command == "solve" else 5.0),
    )
    beta = arguments.beta if arguments.command == "solve" else arguments.transfer_penalty
    expansion = TripExpansionConfig(
        frequencies=frequencies,
        period_length=arguments.period_length,
        beta=beta,
    )

    if arguments.command == "preprocess-full":
        report = profile_preprocessing(
            dataset,
            routing,
            expansion,
            periods=arguments.periods,
            output_jsonl_gz=arguments.itineraries,
        )
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        print(json.dumps({"status": report["status"], **report["model_size"]}, indent=2))
        return

    scales = _numbers(arguments.demand_scales, float)
    budgets = _numbers(arguments.budgets, float)
    if len(scales) != len(budgets):
        raise ValueError("demand-scales and budgets must have equal length")
    trips = {}
    expanded_count = 0
    missing = []
    for pair, _demand, itineraries in route_demands(dataset, routing):
        if not itineraries:
            missing.append(pair)
            continue
        expanded = tuple(
            trip
            for itinerary in itineraries
            for trip in expand_itinerary(itinerary, expansion)
        )
        expanded_count += len(expanded)
        if expanded_count > arguments.max_expanded_trips:
            raise RuntimeError(
                f"Expanded trip guard exceeded: {expanded_count} > "
                f"{arguments.max_expanded_trips}. Run preprocess-full first or "
                "raise the guard explicitly."
            )
        trips[pair] = expanded
    if missing:
        raise RuntimeError(
            f"Cannot solve: {len(missing)} positive-demand OD pairs have no trip; "
            f"first examples: {missing[:10]}"
        )
    demands = tuple(
        {pair: value * scale for pair, value in dataset.demand.items()}
        for scale in scales
    )
    data = PassengerModelData(dataset.lines, demands, trips)
    config = ModelConfig(
        frequencies=frequencies,
        capacities={line: arguments.capacity for line in dataset.lines},
        # Explicit experiment interpretation: Pool-Cost is the cost of one
        # frequency unit, hence b_lf = f * base_cost_l.
        operating_costs={
            (line, frequency): dataset.lines[line].base_cost * frequency
            for line in dataset.lines
            for frequency in frequencies[line]
        },
        budgets=budgets,
        similarity_limit=arguments.similarity_limit,
        time_limit=arguments.time_limit,
        mip_gap=arguments.mip_gap,
    )
    result = solve(data, config)
    output = _result_json(result)
    output["experiment_assumptions"] = {
        "demand_scales": scales,
        "vehicle_capacity": arguments.capacity,
        "budget_cost": "b_lf = Pool-Cost_l * f",
        "waiting_time": "period_length / (2*f) at every boarding",
        "direction": "undirected PTN duplicated into two service directions",
    }
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(
        json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps({key: output[key] for key in ("status", "objective", "runtime_seconds", "mip_gap")}, indent=2))


if __name__ == "__main__":
    main()

