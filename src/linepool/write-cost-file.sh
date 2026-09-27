#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

ant -q -f ${PROGRAMPATH}/build.xml build-linepool
java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/../essentials/lp-helper${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jheaps-0.13.jar${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/build WriteCostFile ${1}


EXITSTATUS=$?

exit ${EXITSTATUS}
