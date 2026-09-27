#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

ant -q -f ${PROGRAMPATH}/build.xml build-linepool

lpool_model=`"${CONFIGCMD[@]}" -s lpool_model -u`

CLASSPATH=${CLASSPATH}${PATHSEP}${PROGRAMPATH}/../essentials/lp-helper${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jheaps-0.13.jar${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/build

if [[ ${lpool_model,,} == tree_based || ${lpool_model,,} == restricted_line_duration ]]; then
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/../line-planning/cost-model/cost-model.jar CreateLinepool ${1}
elif [[ ${lpool_model,,} == k_shortest_paths_waiting_times ]]; then
	java "${JFLAGS[@]}" -cp ${CLASSPATH} CreateLinepoolSP ${1}
elif [[ ${lpool_model,,} == center-periphery ]]; then
	bash ${PROGRAMPATH}/center-periphery/run.sh ${1}
elif [[ ${lpool_model,,} == terminal-to-terminal ]]; then
    bash ${PROGRAMPATH}/complete/run.sh $1
    bash ${PROGRAMPATH}/write-cost-file.sh $1
elif [[ ${lpool_model,,} == k_shortest_paths ]]; then
	bash ${PROGRAMPATH}/k_shortest_paths/run.sh $1
    bash ${PROGRAMPATH}/write-cost-file.sh $1
else
	echo "Error: Invalid lpool_model argument: ${lpool_model}"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}
