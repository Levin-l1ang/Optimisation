#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../../../core/python
CG_MODEL_PATH=${PROGRAMPATH}/../../mm-travel-time-mip
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}/src:${PYTHON_CORE_PATH}:${CG_MODEL_PATH}"
python3 ${PROGRAMPATH}/mm_lc_rc_evaluation.py $1