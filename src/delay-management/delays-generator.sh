#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

delays_generator=`"${CONFIGCMD[@]}" -s delays_generator -u`

if [[ ${delays_generator,,} == "uniform_background_noise" ]]; then
	bash ${PROGRAMPATH}/delay-generator-back-noise/runner.sh basis/Config.cnf  || exit 1
elif [[ ${delays_generator,,} == "uniform_distribution" || ${delays_generator,,} == "activities_on_track" || ${delays_generator,,} == "events_in_station" ]]; then
	ant -q -f ${PROGRAMPATH}/delay-generator/build.xml build-delay-generator
	java "${JFLAGS[@]}" -classpath ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/delay-generator${PATHSEP}${PROGRAMPATH}/../essentials/config${PATHSEP}${PROGRAMPATH}/../essentials/dm-helper/Tools${PATHSEP}${PROGRAMPATH}/../essentials/dm-helper/EAN DelayGenerator
else
	echo "Error: Invalid delays_generator argument: ${delays_generator}"
	exit 1;
fi
