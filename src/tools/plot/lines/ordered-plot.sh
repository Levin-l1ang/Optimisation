#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`
PYTHON_CORE_PATH=${PROGRAMPATH}/../../../core/python
export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}:${PYTHON_CORE_PATH}"
source ${PROGRAMPATH}/../../../base.sh

frequency=`"${CONFIGCMD[@]}" -s lpool_opt_visualisation_draw_frequency -u`

python3 ${PROGRAMPATH}/plot/FrequencyOrderedPlot.py $1 $2 ${frequency,,}