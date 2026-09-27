#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../../core/python
COMMON_PATH=${PROGRAMPATH}/../../commons/
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}:${COMMON_PATH}:${PYTHON_CORE_PATH}"
#~/anaconda3/bin/python3.7 ${PROGRAMPATH}/src/main.py $1
python3 ${PROGRAMPATH}/src/main.py $1
