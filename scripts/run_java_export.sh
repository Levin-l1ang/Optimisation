#!/usr/bin/env bash
# Run the OFFICIAL benchmark generators (no CPLEX needed) and dump their output.
# Requires JDK 21.   usage: scripts/run_java_export.sh OUT_DIR [ROUTE_GEN_FILE]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
B="$ROOT/data/external/line-planning-benchmark"
I="$B/instances/Randstad-IC"
OUT="${1:?output dir}"
ROUTE="${2:-$I/RouteGeneration/medium.txt}"
BUILD="$(mktemp -d)"; trap 'rm -rf "$BUILD"' EXIT
v="$(javac -version 2>&1 | grep -o '[0-9]\+' | head -1)"
[ "$v" -ge 21 ] || { echo "JDK 21+ required (found $v)"; exit 1; }
javac -nowarn -d "$BUILD" -sourcepath "$B/src" "$ROOT/tools/java/ExportBenchmark.java" 2>&1 | grep -v -E '^(Note|Picked up)' || true
java -cp "$BUILD" exporter.ExportBenchmark "$I" "$I/ODMatrix/dense.txt" "$I/budget/medium.txt" \
  "$I/transferPenalty/medium.txt" "$I/LineGeneration/small.txt" "$ROUTE" "$OUT" ${3:-}
