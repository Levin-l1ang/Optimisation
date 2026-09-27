#!/usr/bin/env bash

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

ant -q -f ${PROGRAMPATH}/build.xml
java "${JFLAGS[@]}" -classpath ${PROGRAMPATH}/build${PATHSEP}${PROGRAMPATH}/../../core/java/lintim-core.jar net.lintim.main.evaluation.VehicleScheduleMain basis/Config.cnf
