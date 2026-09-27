#!/usr/bin/env bash

# use absolute path
PROGRAMPATH=$(cd $(dirname $0); pwd)

source ${PROGRAMPATH}/../../base.sh

# use absolute path instead of relative in pythonpath
PYTHON_CORE_PATH=$(cd $PROGRAMPATH/../../core/python; pwd)
PYTHONPATH=$PYTHONPATH:$PYTHON_CORE_PATH \
python3 $PROGRAMPATH/nonscheduled_timetable.py $1