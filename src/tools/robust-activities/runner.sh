#!/usr/bin/env bash

PROGRAMPATH=`dirname ${0}`

source ${PROGRAMPATH}/../../base.sh

buffer_model=`"${CONFIGCMD[@]}" -s rob_buffer_generator -u`

if [[ ${buffer_model} == "proportional-restricted" ]]; then

    PYTHONPATH="${PROGRAMPATH}/src/python:${CORE_DIR}/python"
    env PYTHONPATH=${PYTHONPATH} python3 ${PROGRAMPATH}/src/python/robust_activities/main/proportional_buffer_some_activities.py $1

else

	MOD_CLASSPATH=${CLASSPATH}${PATHSEP}${PROGRAMPATH}/bin

    ant -q -f "${PROGRAMPATH}/build.xml" || exit 1

    MOD_CLASSPATH=${MOD_CLASSPATH}${PATHSEP}${LIB_DIR}/super-csv/super-csv-2.4.0.jar
    MOD_CLASSPATH=${MOD_CLASSPATH}${PATHSEP}${SRC_DIR}/essentials/javatools/bin
    java ${JFLAGS[@]} -cp ${MOD_CLASSPATH} net.lintim.main.${2} ${1}

fi
