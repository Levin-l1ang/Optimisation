#!/usr/bin/env python3
"""Summarise result JSON files into a markdown report (no re-solving)."""

import json
import sys
from pathlib import Path


def fmt(x, nd=1):
    return "-" if x is None else f"{x:,.{nd}f}"


def table(doc):
    rows = ["| M | K | status | objective | travel | unserved cost | service rate (per period) | lines on (per period) | gap | runtime s |",
            "|---|---|---|---|---|---|---|---|---|---|"]
    for r in doc["runs"]:
        rows.append(
            f"| {r['M']:g} | {r['K']:g} | {r['status']} | {fmt(r['objective'])} | {fmt(r['travel_cost'])} | "
            f"{fmt(r['unserved_cost'])} | {', '.join(f'{x:.4f}' for x in r['service_rate_by_period'])} | "
            f"{r['lines_operated_by_period']} | {fmt(r['mip_gap'] and 100 * r['mip_gap'], 2)}% | {r['runtime_seconds']:.0f} |")
    return "\n".join(rows)


def plan_changes(doc):
    lines = []
    by_k = {}
    for r in doc["runs"]:
        by_k.setdefault(r["K"], []).append(r)
    for k, runs in sorted(by_k.items()):
        base = runs[0]["selected_frequencies"]
        for r in runs[1:]:
            same = r["selected_frequencies"] == base
            lines.append(f"- K={k:g}: frequency plan M={runs[0]['M']:g} vs M={r['M']:g}: "
                         f"{'identical' if same else 'DIFFERENT'}")
    return "\n".join(lines)


def main(paths):
    out = []
    for path in paths:
        doc = json.loads(Path(path).read_text())
        out.append(f"### {Path(path).name}\n")
        out.append(f"periods={doc['periods']}, budgets={doc['budgets']}, formulation={doc['formulation']}, "
                   f"reduced={doc['reduced_instance']} {doc['reduction']}, seed={doc['od_seed']}\n")
        out.append(table(doc))
        if len({r["M"] for r in doc["runs"]}) > 1:
            out.append("\nFrequency plans across M (per K):\n" + plan_changes(doc))
        out.append("")
    print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1:])
