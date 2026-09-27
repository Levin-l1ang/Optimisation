#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../core/python/
export PYTHONPATH="${PROGRAMPATH}:${PYTHON_CORE_PATH}"
python3 ${PROGRAMPATH}/25_datasets.py $1

