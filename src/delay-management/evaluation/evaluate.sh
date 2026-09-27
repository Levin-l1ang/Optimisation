#!/usr/bin/env bash

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

ant -q -f ${PROGRAMPATH}/build.xml build-evaluation-delay-management
java "${JFLAGS[@]}" -classpath "${CLASSPATH}${PATHSEP}${PROGRAMPATH}${PATHSEP}${PROGRAMPATH}/../../essentials/config${PATHSEP}${PROGRAMPATH}/../../essentials/statistic${PATHSEP}${PROGRAMPATH}/../../essentials/dm-helper/Tools${PATHSEP}${PROGRAMPATH}/../../essentials/dm-helper/EAN" EvaluateDM
