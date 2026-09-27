#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../core/python
COMMON_PATH=${PROGRAMPATH}/../commons/
EVAL_PATH=${PROGRAMPATH}/../evaluation/src/
TOOLS_PATH=${PROGRAMPATH}/../../tools/plot/od/
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}:${COMMON_PATH}:${EVAL_PATH}:${TOOLS_PATH}:${PYTHON_CORE_PATH}"
echo ${PROGRAMPATH}
python3 ${PROGRAMPATH}/src/main.py $1