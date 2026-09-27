#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh

mm_lc_rc_model=`"${CONFIGCMD[@]}" -s mm_lc_rc_model -u`

if [[ ${mm_lc_rc_model,,} == cost || ${mm_lc_rc_model,,} == cost-beta ]]; then
	bash ${PROGRAMPATH}/lprp/run.sh ${1}
elif [[ ${mm_lc_rc_model,,} == mm-travel-time-mip ]]; then
	bash ${PROGRAMPATH}/mm-travel-time-mip/run.sh ${1}
else
	echo "Error: Invalid mm_lc_rc_model argument: ${mm_lc_rc_model}"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}