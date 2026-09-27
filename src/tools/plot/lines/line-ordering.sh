#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../../core/python
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}:${PYTHON_CORE_PATH}"

source ${PROGRAMPATH}/../../../base.sh

python3 ${PROGRAMPATH}/optimization/LineOrdering.py $1 $2	
