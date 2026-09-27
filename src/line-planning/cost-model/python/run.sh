#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../../core/python
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}:${PYTHON_CORE_PATH}"
python3 ${PROGRAMPATH}/cost.py $1