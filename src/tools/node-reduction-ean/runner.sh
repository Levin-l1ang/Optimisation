#!/usr/bin/env bash

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

MOD_CLASSPATH=${CLASSPATH}${PATHSEP}${PROGRAMPATH}/bin

ant -q -f "${PROGRAMPATH}/build.xml" || exit 1

for file in ${PROGRAMPATH}/lib/*.jar; do
	MOD_CLASSPATH=${MOD_CLASSPATH}${PATHSEP}${file}
done

java -server -Xbatch -XX:+AggressiveOpts -cp ${MOD_CLASSPATH} net.lintim.main.${1} ${2}


