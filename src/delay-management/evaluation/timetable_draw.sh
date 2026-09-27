#!/usr/bin/env bash

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh


ant -q -f ${PROGRAMPATH}/build.xml build-evaluation-delay-management
java "${JFLAGS[@]}" -classpath ${CLASSPATH}${PATHSEP}${SRC_DIR}/delay-management/evaluation${PATHSEP}${SRC_DIR}/essentials/config${PATHSEP}${SRC_DIR}/essentials/statistic${PATHSEP}${SRC_DIR}/essentials/dm-helper/Tools${PATHSEP}${SRC_DIR}/essentials/dm-helper/EAN${PATHSEP}${SRC_DIR}/core/java/lintim-core.jar DelaysAtStations
bash ${PROGRAMPATH}/animate_delays.sh