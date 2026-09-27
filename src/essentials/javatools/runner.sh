#!/usr/bin/env bash

set -e

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

ant -q -f "${PROGRAMPATH}/custom-build.xml" || exit 1

MOD_CLASSPATH=${CLASSPATH}${PATHSEP}${PROGRAMPATH}/bin${PATHSEP}../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}../../libs/jgrapht/jheaps-0.13.jar${PATHSEP}../../libs/apache-commons/commons-math-2.1.jar${PATHSEP}../../libs/super-csv/super-csv-2.4.0.jar${PATHSEP}${CORE_DIR}/java/lintim-core.jar

java -cp ${MOD_CLASSPATH} net.lintim.main.${2} ${1}
