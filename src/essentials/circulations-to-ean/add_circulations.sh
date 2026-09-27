#!/usr/bin/env bash

set -e

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

ant -q -f ${PROGRAMPATH}/build.xml build-circulations-to-ean
java "${JFLAGS[@]}" -classpath ${CLASSPATH}${PATHSEP}${CORE_DIR}/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/build CirculationsToEAN $1
