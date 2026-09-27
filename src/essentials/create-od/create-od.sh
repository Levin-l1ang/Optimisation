#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh


ant -q -f ${PROGRAMPATH}/build.xml build-create-od
	java ${JFLAGS[@]} -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/../../core/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/../sl-helper/PTNTools${PATHSEP}${PROGRAMPATH}/../shortest-paths/src/${PATHSEP}${PROGRAMPATH}/../../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}${PROGRAMPATH}/../../../libs/jgrapht/jheaps-0.13.jar${PATHSEP}${PROGRAMPATH} CreateOD $1


EXITSTATUS=$?

exit ${EXITSTATUS}