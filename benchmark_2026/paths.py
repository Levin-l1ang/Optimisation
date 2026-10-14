"""Repository-relative locations of the primary configuration's inputs/outputs."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BENCHMARK = ROOT / "data" / "external" / "line-planning-benchmark"
INSTANCE = BENCHMARK / "instances" / "Randstad-IC"
DERIVED = ROOT / "data" / "derived" / "randstad_ic"
LINE_POOL = DERIVED / "line_pool.json"
OFFICIAL_ROUTES = DERIVED / "dptn_routes.jsonl"
OD_BOUNDS = DERIVED / "od_bounds.jsonl"
PREPARE_REPORT = DERIVED / "prepare_report.json"
