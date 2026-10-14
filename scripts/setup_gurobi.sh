#!/usr/bin/env bash
# One-time Gurobi set-up on macOS/Linux:  bash scripts/setup_gurobi.sh [GRBGETKEY_KEY]
#
#  1. creates ./.venv and installs requirements.txt + gurobipy
#  2. if a key is given, runs `grbgetkey KEY` (needs the Gurobi licence tools, and the campus network / VPN)
#  3. runs scripts/verify_gurobi.py
#
# The key is passed straight to grbgetkey; it is never written to any file by this script.
set -euo pipefail
cd "$(dirname "$0")/.."

PY="${PYTHON:-python3}"
if [ ! -d .venv ]; then "$PY" -m venv .venv; fi
# shellcheck disable=SC1091
source .venv/bin/activate
python -m pip install --quiet --upgrade pip
python -m pip install --quiet -r requirements.txt -r requirements-gurobi.txt
echo "gurobipy: $(python -c 'import gurobipy as g; print(g.gurobi.version())')"

find_grbgetkey() {
  command -v grbgetkey 2>/dev/null && return 0
  local c
  for c in /Library/gurobi*/mac*/bin/grbgetkey /opt/gurobi*/linux64/bin/grbgetkey \
           "$HOME"/gurobi*/mac*/bin/grbgetkey "$HOME"/gurobi*/linux64/bin/grbgetkey \
           "$HOME"/gurobi_license_tools/*/bin/grbgetkey "$HOME"/gurobi_license_tools/bin/grbgetkey \
           "$HOME"/Downloads/licensetools*/bin/grbgetkey "$HOME"/Downloads/gurobi*/bin/grbgetkey; do
    if [ -x "$c" ]; then echo "$c"; return 0; fi
  done
  return 1
}

if [ "${1:-}" != "" ]; then
  KEY="${1#grbgetkey }"                    # accept the whole "grbgetkey XXXX" command too
  if GRB="$(find_grbgetkey)"; then
    echo "using $GRB"
    "$GRB" "$KEY"                          # asks where to store gurobi.lic: accept the default (your home folder)
  else
    cat <<MSG
grbgetkey was not found.  pip does NOT ship it.  Get the "licence tools" for macOS:
  Gurobi Portal -> Downloads (or the "Install" section of your licence page) -> "Gurobi License Tools"
  (licensetools*_macos_universal2.tar.gz), unpack it, then re-run:
      bash scripts/setup_gurobi.sh "$KEY"
  or run   <unpacked folder>/bin/grbgetkey $KEY   yourself and re-run this script without the key.
MSG
    exit 2
  fi
fi

python scripts/verify_gurobi.py
