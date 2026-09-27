#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../../../base.sh

ant -q -f ${PROGRAMPATH}/build.xml build
java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/build${PATHSEP}${CORE_DIR}/java/lintim-core.jar net.lintim.timetabling.main.WalkingEvaluatorMain $1

