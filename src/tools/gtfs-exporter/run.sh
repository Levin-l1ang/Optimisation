#!/usr/bin/env bash
set -e
PROGRAMPATH=`dirname $0`


source ${PROGRAMPATH}/../../base.sh

ant -q -f ${PROGRAMPATH}/build.xml

java "${JFLAGS[@]}" -classpath ${CORE_DIR}/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/build net.lintim.main.tools.GTFSExporterMain ${1}