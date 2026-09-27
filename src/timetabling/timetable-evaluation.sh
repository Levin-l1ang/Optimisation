#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

TIM_EVAL_CAP=`"${CONFIGCMD[@]}" -s tim_cap_eval -u`
TIM_CONCEPT=`"${CONFIGCMD[@]}" -s tim_concept -u`
USE_WALKING=`"${CONFIGCMD[@]}" -s tim_eval_walking -u`

if [[ ${TIM_CONCEPT,,} == "periodic" ]]; then
	echo "Periodic Evaluation"
	make -C ${PROGRAMPATH}/periodic/evaluation
	${PROGRAMPATH}/periodic/evaluation/evaluation || exit 1
	if [[ ${TIM_EVAL_CAP,,} == "true" ]]; then
		echo "Starting capacitated evaluation"
		bash ${PROGRAMPATH}/periodic/capacitated_evaluation/run.sh $1
	fi
	if [[ ${USE_WALKING,,} == "true" ]];then
		echo "Use walking"
		bash ${PROGRAMPATH}/periodic/evaluation/walking/eval.sh $1
	fi

elif [[ ${TIM_CONCEPT,,} == "aperiodic" ]]; then\
	echo "Aperiodic Evaluation"
	ant -q -f ${PROGRAMPATH}/aperiodic/evaluation/build.xml build-evaluation-aperiodic-timetabling
	java "${JFLAGS[@]}" -classpath ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/aperiodic/evaluation${PATHSEP}${PROGRAMPATH}/../essentials/config${PATHSEP}${PROGRAMPATH}/../essentials/statistic${PATHSEP}${PROGRAMPATH}/../essentials/dm-helper/Tools${PATHSEP}${PROGRAMPATH}/../essentials/dm-helper/EAN NonPeriodicTimetableEvaluator || exit 1
	
else
	echo "Error: Requested TIM_CONCEPT \"${TIM_CONCEPT}\" not available!"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}
