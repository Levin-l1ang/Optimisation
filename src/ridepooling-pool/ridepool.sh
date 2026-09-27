#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

rpool_model=`"${CONFIGCMD[@]}" -s rpool_model -u`

if [[ ${rpool_model,,} == all ]]; then
	bash ${PROGRAMPATH}/all/run.sh ${1}
elif [[ ${rpool_model,,} == demand_heuristic ]]; then
	bash ${PROGRAMPATH}/demand_heuristic/run.sh ${1}
elif [[ ${rpool_model,,} == tree_based ]]; then
	bash ${PROGRAMPATH}/tree-based/run.sh ${1}
elif [[ ${rpool_model,,} == connected_subgraphs ]]; then
	bash ${PROGRAMPATH}/connected-subgraphs/run.sh ${1}
elif [[ ${rpool_model,,} == node_based ]]; then
	bash ${PROGRAMPATH}/node_based/run.sh ${1}
elif [[ ${rpool_model,,} == induced_subgraphs ]]; then
	bash ${PROGRAMPATH}/induced_subgraphs/run.sh ${1}
else
	echo "Error: Invalid rpool_model argument: ${rpool_model}"
	exit 1
fi

EXITSTATUS=$?

exit ${EXITSTATUS}