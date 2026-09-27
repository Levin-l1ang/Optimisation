#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

rc_model=`"${CONFIGCMD[@]}" -s rc_model -u`

if [[ ${rc_model,,} == cost || ${rc_model,,} == cost-beta ]]; then
	bash ${PROGRAMPATH}/cost/run.sh ${1}
else
	echo "Error: Invalid rc_model argument: ${rc_model}"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}