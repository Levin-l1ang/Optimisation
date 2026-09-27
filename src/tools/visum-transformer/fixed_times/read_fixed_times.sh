#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../../core/python
COMMON_PATH=${PROGRAMPATH}/../common/src
LINES_PATH=${PROGRAMPATH}/../fixed_lines/src
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}/src:${COMMON_PATH}:${LINES_PATH}:${PYTHON_CORE_PATH}"
python3 ${PROGRAMPATH}/src/fixed_times/main/read_fixed_times.py $1