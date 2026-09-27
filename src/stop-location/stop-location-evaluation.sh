#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`
CONFIGPROG=${PROGRAMPATH}/../essentials/config/config.sh

source ${PROGRAMPATH}/../base.sh

ant -q -f ${PROGRAMPATH}/evaluation/build.xml build-stop-location-evaluation
java ${JFLAGS[@]} -cp ${CLASSPATH}${PATHSEP}${CORE_DIR}/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/../essentials/sl-helper/PTNTools${PATHSEP}${PROGRAMPATH}/evaluation${PATHSEP}${PROGRAMPATH}/../essentials/shortest-paths/src/${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jheaps-0.13.jar EvaluateSL ${1} ${2}


EXITSTATUS=$?

exit ${EXITSTATUS}
