#!/usr/bin/env bash
set -e

if [[ -d ../venv ]]; then
    echo "Enabling lintim virtual environment"
    source ../venv/bin/activate 
fi

cd "$(dirname "$0")"
# First, run the unit tests
bash run_unit_tests.sh
# Now run every test in this directory
python3 util/test_framework.py .
