#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

taf_model=`"${CONFIGCMD[@]}" -s taf_model -u`

if [[ ${taf_model,,} == network_distance || ${taf_model,,} == beeline_distance ]]; then
	bash ${PROGRAMPATH}/distance/run.sh ${1}
elif [[ ${taf_model,,} == zone ]]; then
	bash ${PROGRAMPATH}/zone/run.sh ${1}
elif [[ ${taf_model,,} == flat ]]; then
	bash ${PROGRAMPATH}/flat/run.sh ${1}
else
	echo "Error: Invalid taf_model argument: ${taf_model}"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}