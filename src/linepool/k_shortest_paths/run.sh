#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../core/python
COST_MODEL_PATH=${PROGRAMPATH}/../../line-planning/cost-model/python
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}:${PYTHON_CORE_PATH}:${COST_MODEL_PATH}"
python3 ${PROGRAMPATH}/main.py $1