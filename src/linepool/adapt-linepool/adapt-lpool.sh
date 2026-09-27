#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh

lpool_adapt_method=`"${CONFIGCMD[@]}" -s lpool_adapt_method -u`

if [[ ${lpool_adapt_method,,} == trim_all || ${lpool_adapt_method,,} == trim_low_demand ]]; then
	bash ${PROGRAMPATH}/trim/run.sh ${1}
elif [[ ${lpool_adapt_method,,} == concatenate ]]; then
	bash ${PROGRAMPATH}/concatenate/run.sh ${1}
else
	echo "Error: Invalid lpool_adapt_method argument: ${lpool_adapt_method}"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}