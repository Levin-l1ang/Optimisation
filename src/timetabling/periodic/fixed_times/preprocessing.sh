#!/usr/bin/env bash
set -e

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../../base.sh

ant -q -f ${PROGRAMPATH}/build.xml
java -Djava.util.logging.config.file=${PROGRAMPATH}/../../../core/java/logging.properties -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/build${PATHSEP}${PROGRAMPATH}/../../../core/java/lintim-core.jar net.lintim.main.timetabling.PreprocessForFixedTimes $1
