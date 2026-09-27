#!/usr/bin/env bash

PROGRAMPATH=`dirname $0`

source ${PROGRAMPATH}/../../base.sh

load_model=`"${CONFIGCMD[@]}" -s load_generator_model -u`

if [[ ${load_model,,} == load_from_ptn ]]; then
    ant -q -f ${PROGRAMPATH}/build.xml build
    java -Djava.util.logging.config.file=${PROGRAMPATH}/../../core/java/logging.properties -cp ${CLASSPATH}${PATHSEP}${PROGRAMPATH}/build${PATHSEP}${PROGRAMPATH}/../../../libs/jgrapht/jgrapht-core-1.5.0.jar${PATHSEP}${PROGRAMPATH}/../../../libs/jgrapht/jheaps-0.13.jar${PATHSEP}${PROGRAMPATH}/../../core/java/lintim-core.jar${PATHSEP}${PROGRAMPATH}/../../line-planning/cost-model/cost-model.jar net.lintim.main.tools.PTNLoadGeneratorMain basis/Config.cnf

elif [[ ${load_model,,} == load_from_ean ]]; then
    bash ${PROGRAMPATH}/../../essentials/javatools/runner.sh $1 RegenerateLoad

elif [[ ${load_model,,} == spanners ]]; then
    bash ${PROGRAMPATH}/spanners/run.sh $1

else
	echo "Error: Invalid load_generator_model argument: ${load_model}"
	exit 1
fi

