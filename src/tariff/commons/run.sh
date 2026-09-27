#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../core/python
ZONE_PATH=${PROGRAMPATH}/../zone/src
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}:${PYTHON_CORE_PATH}:${ZONE_PATH}"
python3 ${PROGRAMPATH}/main.py $1 $2