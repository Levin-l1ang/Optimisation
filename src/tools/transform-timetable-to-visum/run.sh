#!/usr/bin/env bash
PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh

ant -f ${PROGRAMPATH}/build.xml

java "${JFLAGS[@]}" -classpath ${CORE_DIR}/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/build net.lintim.main.tools.TransformTimetableToVisumMain basis/Config.cnf