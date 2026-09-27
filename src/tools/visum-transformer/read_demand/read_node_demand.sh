#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../../core/python
COMMON_PATH=${PROGRAMPATH}/../common/src
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}/src:${COMMON_PATH}:${PYTHON_CORE_PATH}"
python3 ${PROGRAMPATH}/src/read_demand/main/read_node_demand.py $1