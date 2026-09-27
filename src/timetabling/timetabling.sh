#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

TIM_CONCEPT=`"${CONFIGCMD[@]}" -s tim_concept -u`

if [[ ${TIM_CONCEPT,,} == "periodic" ]]; then
  ${PROGRAMPATH}/periodic/periodic-timetable.sh $1 || exit 1

elif [[ ${TIM_CONCEPT,,} == "aperiodic" ]]; then
	EXTENDED_EVENT_FILE=`"${CONFIGCMD[@]}" -s default_events_expanded_file -u`
	EXTENDED_ACTIVITY_FILE=`"${CONFIGCMD[@]}" -s default_activities_expanded_file -u`
	if [[ ! -e  ${EXTENDED_EVENT_FILE} ]] || [[ ! -e ${EXTENDED_ACTIVITY_FILE} ]]; then
		echo "Found not both expanded files, creating periodic timetable first!"
		${PROGRAMPATH}/periodic/periodic-timetable.sh || exit 1
		make ro-rollout
	fi
	ant -q -f ${PROGRAMPATH}/aperiodic/build.xml build-aperiodic-timetabling
	java "${JFLAGS[@]}" -classpath ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/aperiodic/src/${PATHSEP}${PROGRAMPATH}/../essentials/eigenmodell-helper net.lintim.algorithm.Xpress basis/Config.cnf || exit 1

elif [[ ${TIM_CONCEPT,,} == "odpesp" ]]; then
	bash ${PROGRAMPATH}/../../src/essentials/javatools/runner.sh basis/Config.cnf PeriodicTimetableOdpesp|| exit 1
else
	echo "Error: Requested TIM_CONCEPT \"${TIM_CONCEPT}\" not available!"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}
