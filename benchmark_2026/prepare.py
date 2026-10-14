"""Phase A-D driver: verify the pinned benchmark, run the official generators,
freeze their output and validate it independently."""

from __future__ import annotations

import hashlib
import json
import math
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Dict, Optional

from . import BENCHMARK_COMMIT, paths
from .domain import MISSED_DEMAND_PENALTY_FACTOR
from .io import load_instance
from .line_pool import freeze_line_pool, load_line_pool, validate_line_pool
from .passenger_routes import build_route_pool, load_official_routes

# Detour window used for the Java route export.  Must satisfy
# shortest_realizable + 15 <= infrastructure_shortest + WINDOW for every OD;
# build_route_pool() records any violation (none for the primary configuration).
ROUTE_EXPORT_WINDOW = 45


def _sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _jdk_version() -> str:
    out = subprocess.run(["javac", "-version"], capture_output=True, text=True)
    return (out.stdout + out.stderr).strip().splitlines()[-1]


def run_official_generators(out_dir: Path, window: int = ROUTE_EXPORT_WINDOW) -> None:
    """Run scripts/run_java_export.sh (needs JDK 21, no CPLEX)."""
    route_file = out_dir / "route_window.txt"
    route_file.write_text(f"{window}\n")
    subprocess.run([str(paths.ROOT / "scripts" / "run_java_export.sh"), str(out_dir), str(route_file)],
                   check=True)


def derive_penalty(od_bounds_path: Path, factor: float = MISSED_DEMAND_PENALTY_FACTOR) -> Dict[str, object]:
    records = [json.loads(x) for x in od_bounds_path.read_text().splitlines() if x.strip()]
    worst = max(records, key=lambda r: r["min_travel_time"])
    return {
        "factor": factor,
        "max_min_travel_time": worst["min_travel_time"],
        "argmax_od": [worst["origin"], worst["destination"]],
        "M": math.ceil(factor * worst["min_travel_time"] - 1e-9),
        "M_sensitivity": {
            f"factor_{k}": math.ceil(k * worst["min_travel_time"] - 1e-9) for k in (2, 3, 5)
        },
    }


def prepare(refresh: bool = False) -> Dict[str, object]:
    """Create (or, with refresh=False, re-verify) the frozen derived data."""
    from subprocess import CalledProcessError  # noqa: F401
    if refresh or not (paths.LINE_POOL.exists() and paths.OFFICIAL_ROUTES.exists()):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dir = Path(tmp)
            run_official_generators(tmp_dir)
            provenance = {
                "benchmark_repository": "https://github.com/rn-van-lieshout/line-planning-benchmark",
                "benchmark_commit": BENCHMARK_COMMIT,
                "generator": "official linePlanning.lineGeneration.LinePoolGenerator via tools/java/ExportBenchmark.java",
                "generator_status": "official reference output (not a reimplementation)",
                "line_generation_setting": "small",
                "route_generation_export_window_minutes": ROUTE_EXPORT_WINDOW,
                "jdk": _jdk_version(),
                "generator_sources_sha256": {
                    p.name: _sha(p)
                    for p in sorted((paths.BENCHMARK / "src/linePlanning/lineGeneration").glob("*.java"))
                },
                "route_generator_sha256": _sha(paths.BENCHMARK / "src/linePlanning/routeGeneration/RouteGenerator.java"),
                "exporter_sha256": _sha(paths.ROOT / "tools/java/ExportBenchmark.java"),
                "benchmark_manifest_sha256": _sha(paths.ROOT / "data/external/benchmark_manifest.json"),
            }
            paths.DERIVED.mkdir(parents=True, exist_ok=True)
            freeze_line_pool(tmp_dir / "lines.jsonl", paths.LINE_POOL, provenance)
            shutil.copy2(tmp_dir / "routes.jsonl", paths.OFFICIAL_ROUTES)
            shutil.copy2(tmp_dir / "od_bounds.jsonl", paths.OD_BOUNDS)
    return verify_frozen_data()


def verify_frozen_data() -> Dict[str, object]:
    inst = load_instance(paths.INSTANCE)
    lines, provenance = load_line_pool(paths.LINE_POOL)
    pool_report = validate_line_pool(inst, lines, oracle=True)
    official = load_official_routes(paths.OFFICIAL_ROUTES)
    route_pool = build_route_pool(inst, lines, official, window=ROUTE_EXPORT_WINDOW)
    diag = route_pool.diagnostics
    # The route export's shortest durations must match the DPTN lower bounds.
    bounds = {(r["origin"], r["destination"]): r for r in
              (json.loads(x) for x in paths.OD_BOUNDS.read_text().splitlines() if x.strip())}
    mismatch = [k for k, (s, _) in official.items() if abs(s - bounds[k]["min_travel_time"]) > 1e-6]
    penalty = derive_penalty(paths.OD_BOUNDS)
    report = {
        "benchmark_commit": BENCHMARK_COMMIT,
        "stations_total": len(inst.stations),
        "stations_active": len(inst.active_stations),
        "connections": len(inst.connections),
        "od_pairs": len(inst.od_pairs),
        "total_base_demand": sum(o.volume for o in inst.od_pairs),
        "parameters": inst.parameters.__dict__,
        "line_pool": {**pool_report, "pool_sha256": json.loads(paths.LINE_POOL.read_text())["pool_sha256"]},
        "route_pool": {k: v for k, v in diag.__dict__.items() if not isinstance(v, list)},
        "od_without_route": diag.od_without_route,
        "window_insufficient": diag.window_insufficient,
        "min_travel_time_mismatches": len(mismatch),
        "penalty": penalty,
        "frozen_file_sha256": {p.name: _sha(p) for p in (paths.LINE_POOL, paths.OFFICIAL_ROUTES, paths.OD_BOUNDS)},
        "provenance": provenance,
    }
    assert len(inst.connections) == 80 and len(inst.od_pairs) == 416 and len(lines) == 93, report
    assert not diag.window_insufficient and not mismatch, report
    paths.PREPARE_REPORT.write_text(json.dumps(report, indent=1, sort_keys=True) + "\n")
    return report
