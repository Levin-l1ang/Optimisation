#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh

evaluate_extended=`"${CONFIGCMD[@]}" -s ean_eval_extended -u`

bash ${SRC_DIR}/essentials/javatools/runner.sh $1 EvaluateEventActivityNetwork

if [[ ${evaluate_extended} == "true" ]]; then
    bash ${PROGRAMPATH}/evaluate-passenger-load/run.sh $1
fi