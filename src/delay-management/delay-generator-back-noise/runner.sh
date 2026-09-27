#!/usr/bin/env bash

set -eu

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

ant -q -f "${PROGRAMPATH}/build.xml"

MOD_CLASSPATH=${CLASSPATH}${PATHSEP}${PROGRAMPATH}/bin${PATHSEP}${SRC_DIR}/essentials/javatools/bin${PATHSEP}${LIB_DIR}/super-csv/super-csv-2.4.0.jar
java -cp ${MOD_CLASSPATH} net.lintim.GenerateDelays ${1}
