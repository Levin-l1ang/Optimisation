"""Command line interface: ``python -m benchmark_2026 <command>``."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import List, Optional, Sequence

import yaml

from . import BENCHMARK_COMMIT, paths
from .multiperiod import DEFAULT_SEED, generate, identical, write_csv

DEFAULT_CONFIG = paths.ROOT / "configs" / "randstad_ic_primary.yaml"


def _floats(text: str) -> List[float]:
    return [float(x) for x in text.split(",") if x.strip()]


def _ints(text: str) -> List[int]:
    return [int(x) for x in text.split(",") if x.strip()]


def _parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="python -m benchmark_2026", description=__doc__)
    p.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    sub = p.add_subparsers(dest="command", required=True)

    prep = sub.add_parser("prepare", help="verify pinned benchmark; (re)generate and validate frozen data")
    prep.add_argument("--refresh", action="store_true", help="re-run the official Java generators (JDK 21)")

    sub.add_parser("verify", help="re-validate the frozen line pool, routes and penalty (no Java)")

    dem = sub.add_parser("generate-demand", help="write the seeded synthetic monotone OD periods")
    dem.add_argument("--periods", type=int, default=2)
    dem.add_argument("--seed", type=int)
    dem.add_argument("--output", type=Path)

    for name, help_text in (("estimate", "exact model size, nothing is built or solved"),
                            ("solve", "solve one or several (M, K) combinations")):
        s = sub.add_parser(name, help=help_text)
        s.add_argument("--formulation", choices=["compact", "trips"], default="compact",
                       help="compact = line-level (equivalent); trips = frequency-specific trips (spec)")
        s.add_argument("--periods", type=int, default=2)
        s.add_argument("--seed", type=int)
        s.add_argument("--frequencies", help="comma list restricting the frequency options (REDUCED experiment)")
        s.add_argument("--identical-demand", action="store_true",
                       help="all periods equal the base matrix (consistency check, not an experiment)")
        s.add_argument("--top-od", type=int, help="keep only the N largest OD pairs (REDUCED experiment)")
    solve = sub.choices["solve"]
    solve.add_argument("--budgets", type=_floats,
                       help="per-period budgets (default: 60 per period). Any other value defines a "
                            "separate, explicitly labelled budget experiment")
    solve.add_argument("--M", type=_floats, help="penalty values, e.g. 161,242,403 (default: config)")
    solve.add_argument("--K", type=_floats, help="similarity limits (default: config)")
    solve.add_argument("--time-limit", type=float)
    solve.add_argument("--mip-gap", type=float)
    solve.add_argument("--solver", choices=["highs", "gurobi"], default="highs",
                       help="MILP back-end (gurobi needs gurobipy and a licence)")
    solve.add_argument("--threads", type=int, help="solver threads (default: solver decides)")
    solve.add_argument("--initial-plan", type=Path,
                       help="JSON warm start: a list with one {line_id: frequency} object per period, "
                            "or a previous result file of this CLI (its last run is used)")
    solve.add_argument("--gurobi-param", action="append", default=[], metavar="NAME=VALUE",
                       help="extra Gurobi parameter (repeatable), e.g. MIPFocus=1; recorded in the result")
    solve.add_argument("--output", type=Path, required=True, help="JSON result file")
    return p


def _load_plan(path: Path, periods: int):
    data = json.loads(path.read_text())
    if isinstance(data, dict):  # a result file written by `solve`
        data = data["runs"][-1]["selected_frequencies"]
    if len(data) != periods:
        raise SystemExit(f"--initial-plan has {len(data)} periods, expected {periods}")
    return [{int(l): int(f) for l, f in period.items() if int(f)} for period in data]


def _load_cfg(path: Path) -> dict:
    return yaml.safe_load(path.read_text())


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = _parser().parse_args(argv)
    cfg = _load_cfg(args.config)
    if args.command in ("prepare", "verify"):
        from .prepare import prepare, verify_frozen_data
        report = prepare(refresh=args.refresh) if args.command == "prepare" else verify_frozen_data()
        print(json.dumps({k: report[k] for k in ("line_pool", "route_pool", "penalty", "od_without_route")}, indent=1))
        return 0
    if args.command == "generate-demand":
        from .io import load_instance
        inst = load_instance(paths.INSTANCE)
        seed = args.seed if args.seed is not None else cfg["demand"]["seed"]
        demand = generate(inst.od_pairs, args.periods, seed)
        out = args.output or paths.DERIVED / f"od_{args.periods}period_seed{seed}.csv"
        write_csv(demand, out)
        print(f"wrote {out} (sha256 of ratios: {demand.sha256()[:16]})")
        return 0

    from .model import estimate_model_size, solve as solve_trips
    from .model_compact import estimate_compact_size, solve_compact
    from .pipeline import (build_compact_data, build_config, build_data, estimate_scale,
                           frequency_options, load_primary, top_od_subset)

    primary = load_primary()
    seed = args.seed if args.seed is not None else cfg["demand"]["seed"]
    demand = (identical(primary.instance.od_pairs, args.periods) if args.identical_demand
              else generate(primary.instance.od_pairs, args.periods, seed))
    freqs = frequency_options(primary.lines, _ints(args.frequencies) if args.frequencies else None)
    subset = top_od_subset(primary, args.top_od) if args.top_od else None
    reduced = bool(args.frequencies or args.top_od)
    model_cfg = cfg["model"]
    budgets = model_cfg["budgets"] if args.periods == 2 else [primary.instance.parameters.budget] * args.periods
    if getattr(args, "budgets", None):
        if len(args.budgets) != args.periods:
            raise SystemExit("--budgets needs one value per period")
        budgets = args.budgets

    if args.command == "estimate":
        if args.formulation == "trips":
            out = estimate_scale(primary, freqs, args.periods, subset)
        else:
            config = build_config(primary, freqs, args.periods, model_cfg["unserved_penalty_M"],
                                  model_cfg["similarity_K"][1], budgets)
            size = estimate_compact_size(build_compact_data(primary, demand, subset), config)
            out = {**size.__dict__, "variables": size.variables, "periods": args.periods}
        out["formulation"] = args.formulation
        out["reduced_instance"] = reduced
        print(json.dumps(out, indent=1))
        return 0

    Ms = args.M or [model_cfg["unserved_penalty_M"]]
    Ks = args.K or model_cfg["similarity_K"]
    time_limit = args.time_limit if args.time_limit is not None else model_cfg["time_limit_seconds"]
    gap = args.mip_gap if args.mip_gap is not None else model_cfg["mip_gap"]
    initial_plan = _load_plan(args.initial_plan, args.periods) if args.initial_plan else None
    gurobi_params = {k: float(v) for k, v in (item.split("=", 1) for item in args.gurobi_param)}
    runs = []
    primary_m = model_cfg["unserved_penalty_M"]
    for M in Ms:
        for K in Ks:
            config = build_config(primary, freqs, args.periods, M, K, budgets, time_limit, gap,
                                  allow_trip_cost_above_penalty=(M != primary_m),
                                  solver=args.solver, threads=args.threads, initial_plan=initial_plan, gurobi_params=gurobi_params)
            started = time.time()
            if args.formulation == "compact":
                result = solve_compact(build_compact_data(primary, demand, subset), config)
            else:
                result = solve_trips(build_data(primary, demand, freqs, subset), config)
            lines_on = [{str(l): f for l, f in sorted(p.items()) if f} for p in result.selected_frequencies]
            runs.append({
                "M": M, "K": K, "solver": args.solver, "gurobi_params": gurobi_params, "status": result.status, "objective": result.objective,
                "travel_cost": result.travel_cost, "unserved_cost": result.unserved_cost,
                "best_bound": result.best_bound, "mip_gap": result.mip_gap,
                "runtime_seconds": result.runtime_seconds, "wall_seconds": time.time() - started,
                "service_rate_by_period": list(result.service_rate),
                "overall_service_rate": result.overall_service_rate,
                "unserved_total_by_period": [sum(p.values()) for p in result.unserved],
                "selected_frequencies": lines_on,
                "lines_operated_by_period": [len(p) for p in lines_on],
                "max_trip_cost": result.metadata["max_trip_cost"],
                "max_trip_cost_below_M": result.metadata["max_trip_cost_below_M"],
                "model_size": result.model_size.__dict__,
            })
            print(json.dumps({k: runs[-1][k] for k in ("M", "K", "status", "objective", "mip_gap",
                                                       "overall_service_rate", "runtime_seconds")}), flush=True)
    document = {
        "instance": "Randstad-IC, dense OD, small line generation, medium route generation, "
                    "medium transfer penalty, medium fixed cost, medium budget",
        "benchmark_commit": BENCHMARK_COMMIT,
        "line_pool_sha256": json.loads(paths.LINE_POOL.read_text())["pool_sha256"],
        "formulation": args.formulation, "periods": args.periods, "budgets": budgets,
        "od_seed": None if args.identical_demand else seed, "demand_distribution": demand.distribution, "demand_sha256": demand.sha256(),
        "reduced_instance": reduced,
        "budget_is_primary_60": all(abs(b - primary.instance.parameters.budget) < 1e-9 for b in budgets),
        "reduction": {"frequencies": args.frequencies, "top_od": args.top_od},
        "frequency_waiting_time": "60/(2f) per boarding leg (extension of the benchmark objective)",
        "penalty_basis": primary.penalty,
        "runs": runs,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=1) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
