#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../base.sh

lc_model=`"${CONFIGCMD[@]}" -s lc_model -u`
default_lines_file=`"${CONFIGCMD[@]}" -s default_lines_file -u`
lc_minimal_global_frequency=`"${CONFIGCMD[@]}" -s lc_minimal_global_frequency -u`
statistic_file=`"${CONFIGCMD[@]}" -s default_statistic_file -u`
lc_respect_fixed_lines=`"${CONFIGCMD[@]}" -s lc_respect_fixed_lines -u`
lc_common_frequency_divisor=`"${CONFIGCMD[@]}" -s lc_common_frequency_divisor -u`

if [[ ${lc_model,,} == from_default ]]; then
	lc_model_default_file=`"${CONFIGCMD[@]}" -s default_lines_default_file -u`
	cp ${lc_model_default_file} ${default_lines_file} || (
			echo "Failed to copy default line concept"
			exit 1 )
elif [[ ${lc_model,,} == cost_greedy_1 ]] || [[ ${lc_model,,} == cost_greedy_2 ]]; then
	make -C ${PROGRAMPATH}/cost-heuristics || exit 1
	${PROGRAMPATH}/cost-heuristics/cost_heuristics $1

elif [[ ${lc_model,,} == cost ]] && ( [[ ${lc_respect_fixed_lines,,} == true ]] || [[ ${lc_common_frequency_divisor} != 1 ]] ); then
    ant -q -f ${PROGRAMPATH}/cost-model-extended/build.xml
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/cost-model-extended/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar net.lintim.main.lineplanning.ExtendedCost $1

elif [[ ${lc_model,,} == cost ]]; then
 	ant -q -f ${PROGRAMPATH}/cost-model/build.xml
  	java "${JFLAGS[@]}" -cp "${CLASSPATH}${PATHSEP}${PROGRAMPATH}/cost-model/build${PATHSEP}${CORE_DIR}/java/lintim-core.jar" net.lintim.main.lineplanning.Cost $1

elif [[ ${lc_model,,} == cost_restricting_frequencies ]]; then
	ant -q -f ${PROGRAMPATH}/cost-model-restricting-frequencies/build.xml
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/cost-model-restricting-frequencies/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar net.lintim.main.lineplanning.CostRestrictingFrequencies $1

elif [[ ${lc_model,,} == game ]]; then
	make -C ${PROGRAMPATH} || exit 1
	if [[ `type mosel 2>&1 | grep "not found"` ]]; then
		echo "Error: xpress-mosel not available or \"mosel\" not contained in "\
		"the PATH environment variable.";
		exit 1;
	fi

	${PROGRAMPATH}/game/Conv_Game &&
	echo "exec ${PROGRAMPATH}/game/line_game.mos lc_minimal_global_frequency=${lc_minimal_global_frequency}" | mosel -s &&
	${PROGRAMPATH}/game/SolConv || exit 1
	#rm -f line-planning/xpresssol
	#rm -f line-planning/Moseldaten

elif [[ ${lc_model,,} == direct ]]; then
	ant -q -f ${PROGRAMPATH}/direct-travelers/build.xml build-direct-travelers
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/direct-travelers/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar net.lintim.main.lineplanning.Direct $1

elif [[ ${lc_model,,} == direct_restricting_frequencies ]]; then
	ant -q -f ${PROGRAMPATH}/direct-travelers-restricting-frequencies/build.xml
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/direct-travelers-restricting-frequencies/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar net.lintim.main.lineplanning.DirectRestrictingFrequencies $1

elif [[ ${lc_model,,} == direct_relaxation ]]; then
	ant -q -f ${PROGRAMPATH}/direct-travelers-relaxation/build.xml build-direct-travelers-relaxation
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/direct-travelers-relaxation/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar Direct $1

elif [[ ${lc_model,,} == mult-cost-direct || ${lc_model,,} == mult-cost-direct-relax ]]; then
	ant -q -f ${PROGRAMPATH}/cost-model-direct-travellers/build.xml build-cost-model-direct-travellers
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/cost-model-direct-travellers/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar CostDirect $1

elif [[ ${lc_model,,} == traveling-time-mip ]]; then
	PYTHON_CORE_PATH=${PROGRAMPATH}/../core/python
	export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}/traveling-time/mip:${PYTHON_CORE_PATH}"
	python3 $PROGRAMPATH/traveling-time/mip/traveling-time-mip.py $1

elif [[ ${lc_model,,} == traveling-time-cg ]]; then
	ant -q -f ${PROGRAMPATH}/traveling-time/column-generation-approach/build.xml
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/traveling-time/column-generation-approach/build${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jheaps-0.13.jar Run $1

elif [[ ${lc_model,,} == minchanges_ip ]]; then
	ant -q -f ${PROGRAMPATH}/min-changes/build.xml build-min-changes
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/min-changes/build${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jheaps-0.13.jar${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/../essentials/sl-helper/PTNTools MinChangesIP $1

elif [[ ${lc_model,,} == minchanges_cg ]]; then
	ant -q -f ${PROGRAMPATH}/min-changes/build.xml build-min-changes
	java "${JFLAGS[@]}" -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/min-changes/build${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}${PROGRAMPATH}/../../libs/jgrapht/jheaps-0.13.jar${PATHSEP}${PROGRAMPATH}/../core/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/../essentials/sl-helper/PTNTools MinChangesColGen $1

elif [[ ${lc_model,,} == cost-od-routing ]]; then
	PYTHON_CORE_PATH=${PROGRAMPATH}/../core/python
	export PYTHONPATH="${PYTHONPATH}:${PROGRAMPATH}/cost-od-routing:${PYTHON_CORE_PATH}"
	python3 $PROGRAMPATH/cost-od-routing/main.py $1

else
	echo "Error: Invalid lc_model argument: ${lc_model}"
	exit 1
fi
