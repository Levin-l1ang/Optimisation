#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh

taf_draw_heatmap=`"${CONFIGCMD[@]}" -s taf_draw_heatmap -u`
taf_draw_zones=`"${CONFIGCMD[@]}" -s taf_draw_zones -u`

if [[ ${taf_draw_heatmap} == true ]]; then
    bash ${PROGRAMPATH}/heatmap.sh ${1}
fi
if [[ ${taf_draw_zones} == true ]]; then
    bash ${PROGRAMPATH}/../../tools/plot/ptn/ptn-draw.sh ${1} "zone"
fi

if [[ ${taf_draw_heatmap} == false && ${taf_draw_zones} == false ]]; then
    echo "WARNING: None of the available visualization methods was run! Check truth values of corresponding config parameters"
    exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}
