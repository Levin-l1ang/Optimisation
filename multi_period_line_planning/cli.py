"""Command-line interface for the multi-period line-planning implementation."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from .germany_rail import prepare_germany_rail
from .instance import load_lintim_instance
from .models import (
    solve_lower_bound,
    solve_mp_b,
    solve_mp_b_ext,
    solve_mp_b_iter,
    solve_mp_f,
    solve_mp_w,
    solve_upper_bound,
)
from .reproduce import reproduce


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Multi-period line planning")
    subparsers = parser.add_subparsers(dest="command", required=True)

    prepare_parser = subparsers.add_parser(
        "prepare-germany-rail", help="Reconstruct the Germany-rail LinTim dataset"
    )
    prepare_parser.add_argument(
        "--dataset", type=Path, default=Path("datasets/germany-rail")
    )
    prepare_parser.add_argument(
        "--source-od", type=Path, default=Path("src/simulation/data/bahn-01/OD.giv")
    )
    prepare_parser.add_argument("--pool-size", type=int, default=132)
    prepare_parser.add_argument("--minimum-line-edges", type=int, default=2)
    prepare_parser.add_argument("--nominal-total-demand", type=float, default=5000.0)
    prepare_parser.add_argument("--vehicle-capacity", type=float, default=70.0)
    prepare_parser.add_argument("--upper-frequency", type=int, default=20)

    reproduce_parser = subparsers.add_parser("reproduce", help="Run the reproduction")
    reproduce_parser.add_argument(
        "--dataset", type=Path, default=Path("datasets/germany-rail")
    )
    reproduce_parser.add_argument(
        "--output", type=Path, default=Path("results/germany_rail")
    )
    reproduce_parser.add_argument("--periods", type=int, default=2)
    reproduce_parser.add_argument("--seed", type=int, default=2023)
    reproduce_parser.add_argument("--penalty", type=float, default=1.0)
    reproduce_parser.add_argument("--time-limit", type=float, default=60.0)

    solve_parser = subparsers.add_parser("solve", help="Solve one model")
    solve_parser.add_argument(
        "model",
        choices=("lower", "upper", "mp-f", "mp-b", "mp-b-iter", "mp-b-ext", "mp-w"),
    )
    solve_parser.add_argument(
        "--dataset", type=Path, default=Path("datasets/germany-rail")
    )
    solve_parser.add_argument("--periods", type=int, default=2)
    solve_parser.add_argument("--seed", type=int, default=2023)
    solve_parser.add_argument("--k", type=float, default=0.0)
    solve_parser.add_argument("--penalty", type=float, default=1.0)
    solve_parser.add_argument("--time-limit", type=float, default=60.0)
    return parser


def main() -> None:
    arguments = _parser().parse_args()
    if arguments.command == "prepare-germany-rail":
        prepared = prepare_germany_rail(
            arguments.dataset,
            arguments.source_od,
            target_pool_size=arguments.pool_size,
            minimum_line_edges=arguments.minimum_line_edges,
            nominal_total_demand=arguments.nominal_total_demand,
            vehicle_capacity=arguments.vehicle_capacity,
            fixed_upper_frequency=arguments.upper_frequency,
        )
        print(
            f"Prepared {prepared.stops} stops, {prepared.edges} edges, "
            f"and {prepared.lines} lines in {prepared.output}"
        )
        return

    if arguments.command == "reproduce":
        results = reproduce(
            arguments.dataset,
            arguments.output,
            periods=arguments.periods,
            seed=arguments.seed,
            penalty=arguments.penalty,
            time_limit=arguments.time_limit,
        )
        optimal = sum(result.optimal for result in results)
        print(f"Completed {len(results)} solves ({optimal} optimal). Results: {arguments.output}")
        return

    instance = load_lintim_instance(
        arguments.dataset, periods=arguments.periods, seed=arguments.seed
    )
    solvers = {
        "lower": lambda: solve_lower_bound(instance, arguments.time_limit),
        "upper": lambda: solve_upper_bound(instance, arguments.time_limit),
        "mp-f": lambda: solve_mp_f(instance, arguments.k, arguments.time_limit),
        "mp-b": lambda: solve_mp_b(instance, int(arguments.k), arguments.time_limit),
        "mp-b-iter": lambda: solve_mp_b_iter(instance, int(arguments.k), arguments.time_limit),
        "mp-b-ext": lambda: solve_mp_b_ext(instance, int(arguments.k), arguments.time_limit),
        "mp-w": lambda: solve_mp_w(
            instance, arguments.k, arguments.penalty, arguments.time_limit
        ),
    }
    result = solvers[arguments.model]()
    print(
        json.dumps(
            {
                "model": result.model,
                "status": result.status,
                "objective": result.objective,
                "runtime_seconds": result.runtime_seconds,
                "dissimilarity": result.dissimilarity,
                "frequencies": result.frequencies,
                "metadata": result.metadata,
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
