#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

dm_method=`"${CONFIGCMD[@]}" -s DM_method -u`

if [[ ${dm_method,,} == "dm2" || ${dm_method,,} == "dm1" || ${dm_method,,} == "dm2-pre" ||   ${dm_method,,} == "fsfs" ||   ${dm_method,,} == "frfs" ||   ${dm_method,,} == "earlyfix" ||   ${dm_method,,} == "priority" ||   ${dm_method,,} == "priorepair" ||   ${dm_method,,} == "best-of-all" ||   ${dm_method,,} == "passengerpriofix" ||   ${dm_method,,} == "passengerfix" ||   ${dm_method,,} == "fixfsfs" ||   ${dm_method,,} == "fixfrfs" ||   ${dm_method,,} == "propagate" ]]; then
	make  --quiet -C ${PROGRAMPATH}/../essentials/config config_cmd
	${PROGRAMPATH}/ip-based/Solve/update_solver.sh
	ant -q -f ${PROGRAMPATH}/ip-based/build.xml build-delay-management
	java "${JFLAGS[@]}" -classpath ${CLASSPATH}${PATHSEP}${CORE_DIR}/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/ip-based/Solve${PATHSEP}${PROGRAMPATH}/../essentials/config${PATHSEP}${PROGRAMPATH}/../essentials/dm-helper/Tools${PATHSEP}${PROGRAMPATH}/../essentials/dm-helper/EAN${PATHSEP}${PROGRAMPATH}/../essentials/statistic SolveDM $1
elif [[ ${dm_method,,} == "online-dm" ]]; then
	ant -q -f ${PROGRAMPATH}/online-dm/build.xml build-online-delay-management
	java "${JFLAGS[@]}" -classpath ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/online-dm${PATHSEP}${PROGRAMPATH}/../essentials/config${PATHSEP}${PROGRAMPATH}/../essentials/dm-helper/Tools${PATHSEP}${PROGRAMPATH}/../essentials/dm-helper/EAN ODM
else
	echo "Error: Invalid dm_method argument: ${dm_method}"
	exit 1;
fi
