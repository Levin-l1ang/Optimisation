#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

vs_model=`"${CONFIGCMD[@]}" -s vs_model -u`
if [[ ${vs_model,,} = "line_based" ]]; then
	bash ./${PROGRAMPATH}/Line-Based/run.sh $1
elif [[ ${vs_model,,} = "simple" ]]; then
	ant -q -f ${PROGRAMPATH}/simple/build.xml
	java  ${JFLAGS[@]} -classpath ${PROGRAMPATH}/simple/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar net.lintim.main.vehiclescheduling.SimpleVehicleScheduleMain $1
elif [[ ${vs_model,,} = "ip" ]]; then
	ant -q -f ${PROGRAMPATH}/ip-model/build.xml
	java  ${JFLAGS[@]} -classpath ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/ip-model/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar net.lintim.main.vehiclescheduling.IPModelMain $1
elif [[ ${vs_model,,} = "assignment_model" || ${vs_model,,} = "transportation_model" || ${vs_model,,} = "network_flow_model" ]]; then
	bash ${PROGRAMPATH}/NetworkxBased/run.sh $1
elif [[ ${vs_model,,} = "single_commodity_model" || ${vs_model,,} = "mdm1" || ${vs_model,,} = "mdm2" || ${vs_model,,} = "canal_model" ]]; then
	bash ${PROGRAMPATH}/SolverBased/run.sh $1

else
	echo "Error: Invalid vs_model argument: ${vs_model}"
	exit 1;
fi
