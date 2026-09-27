#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

SL_CONCEPT=`"${CONFIGCMD[@]}" -s sl_model -u`
JGRAPHT_JAR_FILE=${PROGRAMPATH}/../../libs/jgrapht/jgrapht-core-1.1.0.jar
CORE_JAR=${CORE_DIR}/java/lintim-core.jar

function exit_on_build_failure {
    exit_code=$1
    if [ "$exit_code" -ne 0 ]; then
        echo "Failed to build project.";
        exit 1
    fi
}

if [[ ${SL_CONCEPT,,} == "dsl" ]]; then
	ant -q -f ${PROGRAMPATH}/algorithms/build.xml build-project
    exit_on_build_failure $?
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/algorithms/build${PATHSEP}${CORE_JAR}${PATHSEP}${JGRAPHT_JAR_FILE}${PATHSEP}${PROGRAMPATH}/../essentials/sl-helper/PTNTools SolveDSL $1

elif [[ ${SL_CONCEPT,,} == "greedy" ]]; then
	ant -q -f ${PROGRAMPATH}/algorithms/build.xml build-project
    exit_on_build_failure $?
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/algorithms/build${PATHSEP}${CORE_JAR}${PATHSEP}${JGRAPHT_JAR_FILE}${PATHSEP}${PROGRAMPATH}/../essentials/sl-helper/PTNTools SolveGreedy $1

elif [[ ${SL_CONCEPT,,} == "dsl-tt" ]]; then
	ant -q -f ${PROGRAMPATH}/algorithms/build.xml build-project
    exit_on_build_failure $?
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/algorithms/build${PATHSEP}${CORE_JAR}${PATHSEP}${JGRAPHT_JAR_FILE}${PATHSEP}${PROGRAMPATH}/../essentials/sl-helper/PTNTools SolveDSLTT $1

elif [[ ${SL_CONCEPT,,} == "dsl-tt-2" ]]; then
	ant -q -f ${PROGRAMPATH}/algorithms/build.xml build-project
    exit_on_build_failure $?
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/algorithms/build${PATHSEP}${CORE_JAR}${PATHSEP}${JGRAPHT_JAR_FILE}${PATHSEP}${PROGRAMPATH}/../essentials/sl-helper/PTNTools SolveDSLTT2 $1

elif [[ ${SL_CONCEPT,,} == "tt" ]]; then
    bash ${PROGRAMPATH}/travel-time/run.sh $1
    
elif [[ ${SL_CONCEPT,,} == "all" ]]; then
    bash ${PROGRAMPATH}/travel-time/run_simple_sl.sh $1

else
	echo "Error: Requested SL_CONCEPT \"${SL_CONCEPT}\" not available!"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}
